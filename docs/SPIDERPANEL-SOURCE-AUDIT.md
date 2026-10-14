# SpiderPanel source audit for Spd con

Date: 2026-10-13
Upstream inspected: https://github.com/amirh00sain/SpiderPanel (main branch)
Purpose: map behavior and architecture into Spd con; this is a first-pass source review, not a formal security audit.

## What was inspected

- Repository tree and README
- `main.py` (route inventory and selected auth, subscription, Worker and scanner sections)
- `worker/worker.js`
- `Dockerfile`, `requirements.txt`, `start.sh`
- `static/index.html` and `static/login.html`

The upstream `main.py` is approximately 13.4k lines, the main HTML is approximately 419 KB, and the Cloudflare worker is approximately 46 KB. The panel is a feature-rich monolith rather than a small config form.

## Architecture observed

- FastAPI/Uvicorn panel, configured to listen on port 8080.
- Main state is persisted in a JSON file at `/data/spider_state.json`; deployments need persistent storage and careful backup/restore.
- Server-rendered/static HTML files contain a large amount of client-side UI logic.
- The backend includes routes for login/session, users, subscriptions, inbounds, nodes, groups, IP pools, scanner tools, worker management, tunnel helpers, and Telegram automation.
- The managed Cloudflare Worker is a separate runtime using `cloudflare:sockets` and a KV binding. It handles VLESS-over-WebSocket traffic and usage/heartbeat state; it is not the same thing as running the FastAPI panel on Cloudflare Workers.
- The installer and container path are different: `start.sh` provisions host services and environment values, while the Dockerfile starts Uvicorn directly. Deployment defaults must therefore be checked separately.

## High-priority findings to account for in Spd con

1. **Unsafe source defaults:** `main.py` falls back to a fixed `SECRET_KEY` and initializes the admin password from `ADMIN_PASSWORD` with the fallback `admin`. The Dockerfile's command starts Uvicorn directly, so a deployment that does not inject secure environment values risks retaining these defaults. Spd con should fail closed in production if required secrets/passwords are missing; never copy these defaults.
2. **Broad CORS configuration:** the inspected FastAPI setup uses wildcard origins with credentials enabled. Spd con should use an explicit origin allowlist for the deployed admin UI and avoid credentialed wildcard CORS.
3. **State storage coupling:** a single JSON state file is simple but couples persistence to a writable, persistent filesystem and makes concurrent writes/backup/restore important. Spd con should use a transactional database for user/node/quota state and explicit migrations.
4. **Public subscription links are credentials:** routes use UUID-like public identifiers to fetch a subscription. Treat subscription URLs as bearer secrets, avoid logging them, provide revocation/rotation, and separate internal user IDs from public subscription tokens.
5. **Cloudflare deployment boundary:** the proxy Worker can run on Cloudflare, but the FastAPI panel, Xray process management, local files and raw TCP scanning cannot simply be moved wholesale into a Cloudflare Worker. Keep control-plane APIs and data storage compatible with the chosen runtime, and deploy the data-plane Worker separately.
6. **Real connection requires server provisioning:** a generated client link only works when the matching inbound, user credential, port and transport/security settings are configured on a real server. SNI alone cannot create a valid REALITY profile; public key and short ID must match the server.
7. **Scanner results need validation:** IP/SNI latency results are candidates, not proof of sustained speed, country accuracy or availability. Spd con should probe with timeouts, bound concurrency, validate IP/port inputs, test repeatably, and store measurement time/source.
8. **Large monolithic UI/backend:** the large `main.py` and `static/index.html` are useful feature references but not a good structure to reproduce directly inside Android. Implement feature-compatible modules with testable boundaries instead.
9. **License note:** no top-level LICENSE file was present in the inspected repository tree. Keep the Spd con implementation modular and avoid wholesale verbatim copying unless the claimed permission is documented and covers the specific files/assets.

## Feature map for Spd con

### Build first
- Simple Android home screen and guided config creation.
- Node registry and health status.
- User lifecycle: create, enable/disable, quota, expiry and revoke.
- VLESS/REALITY profile validation and correct link/QR/subscription generation.
- A real server-side provisioning adapter that applies the same user identity to the Xray inbound.
- Automated tests for link serialization, server config validation, quotas and expiry.

### Build after core connection works
- Public customer usage page with an unguessable revocable subscription token.
- Cloudflare Worker provisioning/sync and usage reports.
- IP/SNI scanning with measured latency and country metadata; make it clear that country is approximate and speed can vary.
- Optional DNS/domain-based content filtering only in an actual supported Xray routing profile, not a standard share link.
- Optional Telegram sales/notification features after access control and account lifecycle are reliable.

### Do not claim complete until verified
- In-app VPN runtime / Android VpnService integration.
- Provisioning and revocation against a live, authorized Xray node.
- Enforced quota/expiry on the server (not only UI counters).
- Cloudflare Worker deployed and tested against real traffic.
- Automatic fastest-IP selection measured from the target user's network.
- Adult-content filtering applied by the actual runtime.

## Validation status

This document is a source-informed implementation map. It does not claim that the upstream project has undergone a full security audit or that Spd con has been end-to-end tested against a live server. Each production capability requires tests and a documented pass result.
