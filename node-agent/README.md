# Spd con Node Telemetry Agent (initial bridge)

This is a least-dependency bridge between a trusted node-side runtime adapter and the Spd con API. It accepts normalized JSON Lines on stdin, validates the fields, and submits each event with the node's one-time-enrolled bearer secret.

## Enrollment and setup

1. Start the API with a long random `APP_SECRET`; protect it with HTTPS in any non-local deployment.
2. Register and promote an admin account.
3. Create a node using `POST /admin/nodes` with an admin bearer token and JSON `{"name":"edge-01"}`. Store the returned `id` and `node_secret` securely; the secret is returned only once.
4. On the node, install Python 3.12 or newer and configure:
   - `SPD_CON_API_BASE_URL=https://your-api.example`
   - `SPD_CON_NODE_ID=<enrolled node id>`
   - `SPD_CON_NODE_SECRET=<one-time node secret>`
5. Run `python3 node-agent/agent.py` and pipe normalized JSON Lines from your runtime adapter into stdin.

Do not put the secret in command-line arguments, source control, or logs. Use a root-owned environment file with restrictive permissions or a secret manager. The agent verifies HTTPS by default; plain HTTP is allowed only for localhost development.

## JSON Lines input

Each line is one event, for example:

```json
{"user_id":"<Spd con user id>","connection_id":"runtime-session-123","client_ip":"198.51.100.20","state":"active","upload_bytes":1200,"download_bytes":3400}
{"user_id":"<Spd con user id>","connection_id":"runtime-session-123","client_ip":"198.51.100.20","state":"disconnected","upload_bytes":1800,"download_bytes":4500}
```

The runtime adapter must obtain the source IP from the node/runtime and map the authenticated runtime identity (for example an Xray user email) to the correct Spd con user id. Never accept a user id or client IP directly from an untrusted client. The optional `xray_runtime.py` helper can validate a config using an installed Xray binary and query StatsService counters through a loopback-only Xray API. It does not yet parse access logs into IP observations, map counters to policy enforcement, install firewall rules, disconnect sessions, or apply policy changes to a running core.

## Xray runtime helper

Install a pinned Xray-core release on the node and keep its management API bound to loopback (for example `127.0.0.1:10085`). The Xray config must enable the API/StatsService for counter queries to work; do not expose the API port publicly.

- Validate a config without starting or restarting the core: `python3 node-agent/xray_runtime.py --xray-bin /usr/local/bin/xray validate --config /etc/xray/config.json`
- Query user traffic counters: `python3 node-agent/xray_runtime.py --xray-bin /usr/local/bin/xray stats --server 127.0.0.1:10085 --pattern 'user>>>'`

The helper is read-only. It does not echo raw Xray error logs because those may contain private deployment details. The xray_usage_sync.py utility now polls these cumulative per-user StatsService counters and reports deltas to the API for quota accounting. It still does not terminate sessions or enforce a quota in the running core, and config validation does not prove reachability to a remote server.

## Automatic Xray usage accounting

Create a root-owned JSON identity map that connects the exact Xray account email to a Spd con user ID:

    {
      "vpn-user@example.net": "<Spd con user id>"
    }

Keep the map outside the repository and readable only by the node-agent service account. Then configure the same API/node credentials from the enrollment steps above and run:

    python3 node-agent/xray_usage_sync.py --identity-map /etc/spd-con/identity-map.json --xray-bin /usr/local/bin/xray --server 127.0.0.1:10085 --once

Omit --once to run a supervised polling loop (default interval: 60 seconds). Alternatively set SPD_CON_IDENTITY_MAP_PATH, SPD_CON_API_BASE_URL, SPD_CON_NODE_ID, and SPD_CON_NODE_SECRET in the service environment. Unknown core identities are not assigned to an account and are reported as errors. The API de-duplicates event IDs, tracks cumulative counters per node/account, handles counter resets, and reports whether the recorded quota is exhausted. This is accounting and status reporting—not a guarantee that traffic is blocked when the quota is exhausted.

## Security and behavior

- The API stores only a keyed hash of the node secret and reveals the raw secret only during enrollment.
- Events have a unique id and a five-minute timestamp window; duplicate event ids are rejected.
- API reports are accepted only for enabled enrolled nodes and existing Spd con users.
- Use HTTPS except for loopback development. The API does not trust forwarded IP headers to identify the VPN client.
- IP observations and policy violations are telemetry, not enforcement. `deny_new_ip` does not block an already accepted connection; a core-specific adapter and enforcement design must be implemented and tested before advertising it as active.
- Source IP is personal data. Restrict admin access, define retention/deletion, and avoid collecting labels unless necessary.

## Tests

```sh
python3 -m unittest discover -s node-agent -p 'test_*.py' -v
```
