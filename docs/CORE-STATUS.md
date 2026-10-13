# Spd con core/runtime status

This file separates configuration-generation support from a real running core. A UI field or exported JSON is not proof that the corresponding traffic engine is embedded, installed or connected.

## Current status

| Engine / component | Present in repository | What works today | What is still missing |
|---|---|---|---|
| Xray-core | Pinned CI validator plus config generator | VLESS + REALITY outbound preview/export, protocol diagnostics, and fixture validation against Xray-core v26.3.27 in CI | No bundled/running Xray binary in the Android APK; no live connection test from the app; the node helper can query local StatsService but is not yet connected to API quota accounting; no access-log-to-IP mapping or session enforcement |
| Hysteria2 | Profile and share-link handling | Basic profile/URI generation and parsing | No Hysteria2 runtime, server install/enrollment, live handshake test or traffic policy enforcement |
| TUIC | Profile and share-link handling | Basic profile/URI generation and parsing | No TUIC runtime, server install/enrollment, live handshake test or traffic policy enforcement |
| Backhaul | TOML generator | Role-specific server/client templates for documented fields | No installed tunnel process, service supervisor, end-to-end tunnel test, reconnect/health monitor or production secret management |
| Cloudflare Worker | Metadata/API scaffold | Config metadata, account/subscription routes and starter panel | It does not carry arbitrary VPN traffic and cannot replace a proxy node/runtime |
| Node telemetry bridge | Python bridge and read-only Xray helper in `node-agent/` | Enrolled-node bearer authentication, input validation, stale-event and duplicate-event rejection, source-IP telemetry submission, local Xray config validation and loopback StatsService queries | An optional case-insensitive Xray core-email-to-Spd-con-user mapping is now supported by the node bridge; the bridge still requires a trusted adapter to supply the core email, source IP and counters. A local `xray_provision.py` helper now supports dry-run and Xray-validated atomic add/remove of one VLESS client on an existing inbound, but it is not yet orchestrated by the central API and does not reload the running core. Automatic Xray log ingestion, per-core session-kill and live policy enforcement are not yet implemented |

## Config-generation UI integration (2026-10-13)

The Android builder now exposes a multi-inbound generation action for REALITY TCP/XHTTP, TLS Relay WS/XHTTP, and explicitly entered Cloudflare Worker routes. It persists one local user UUID, generates links with that identity, and saves resulting profiles in the encrypted local config library. Tests cover stable identity, route expansion and the per-user relay path.

This is still a **client-profile generator**. It does not provision the UUID into Xray, install or configure the FastAPI relay, deploy Worker routes, discover a user's public IP from the share link, or prove DPI resilience. Real server-side user lifecycle and live connection checks remain release blockers.

## What “complete the cores” means in implementation terms

1. **Runtime contract:** define start/stop, version, health, config validation, logs, traffic stats and capability reporting for each supported engine.
2. **Xray first:** CI pins Xray-core v26.3.27 and validates a REALITY fixture using the upstream binary. A read-only node helper now validates local configs and queries StatsService on loopback. The API now accounts for authenticated node-reported cumulative byte-counter deltas idempotently. The node bridge supports a local identity map (`SPD_CON_IDENTITY_MAP_PATH`), and `xray_usage_sync.py` polls cumulative Xray StatsService counters and reports usage deltas to the API. The API returns quota status but does not yet enforce it in the core. Next, implement and test trusted Xray access-log correlation for source IPs and a core-specific enforcement adapter; no live node test has yet been verified.
3. **Other engines behind adapters:** add Hysteria2 and TUIC only after their installed versions, configuration schemas, health checks and per-user stats/enforcement capabilities are tested. Keep Backhaul as a tunnel service adapter, not a client-protocol substitute.
4. **Android runtime decision:** decide whether the APK embeds a native core (ABI packaging, foreground service, VPNService/TUN integration, lifecycle and battery handling) or manages remote nodes only. Current code is a configuration builder and does not yet provide a working on-device VPN connection.
5. **Policy enforcement:** translate admin policy into core-supported actions only where the runtime supports them. If an engine cannot block a new source IP or disconnect a specific session safely, expose monitoring/alerting rather than claiming enforcement.
6. **Release gate:** protocol fixture tests, pinned-core config validation, Android ABI build checks, integration tests against disposable local nodes, secret-handling review, and a documented rollback procedure.

## Security boundaries

- Never expose the Xray management API to the public internet; bind it to loopback or a private authenticated channel.
- Do not infer an identity from an IP alone. Configure `SPD_CON_IDENTITY_MAP_PATH` as a JSON object mapping Xray user emails to Spd con account IDs; keep this file readable only by the node-agent service account. Events must include `core_email` when this map is enabled, and conflicting user IDs are rejected. This is a mapping boundary, not proof that arbitrary input is an authentic Xray log; only feed it from a trusted local runtime adapter.
- Do not automatically rewrite REALITY keys, UUIDs, SNI, ports or transport parameters without validating them against the selected server.
- A passing config parser or a successful build does not prove a real network path works.


## IP policy decision signals

The API reports `decision: allow` or `decision: deny_new_ip` when the configured distinct-IP rule is exceeded. This is a decision signal only; the current node runtime bridge does not yet enforce it before admitting a session.
