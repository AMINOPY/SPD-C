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
| Node telemetry bridge | Python bridge and read-only Xray helper in `node-agent/` | Enrolled-node bearer authentication, input validation, stale-event and duplicate-event rejection, source-IP telemetry submission, local Xray config validation and loopback StatsService queries | No access-log-to-user/IP mapping, cumulative connection-counter deltas are now forwarded to API usage accounting, but no automatic Xray StatsService/access-log adapter, per-core session-kill or live policy enforcement |

## What “complete the cores” means in implementation terms

1. **Runtime contract:** define start/stop, version, health, config validation, logs, traffic stats and capability reporting for each supported engine.
2. **Xray first:** CI pins Xray-core v26.3.27 and validates a REALITY fixture using the upstream binary. A read-only node helper now validates local configs and queries StatsService on loopback. The API now accounts for authenticated node-reported cumulative byte-counter deltas idempotently. Next, map Xray access-log identities to trusted user IDs and source IPs, automatically collect StatsService counters, and add generated-output fixtures before claiming the Android generator is fully compatible.
3. **Other engines behind adapters:** add Hysteria2 and TUIC only after their installed versions, configuration schemas, health checks and per-user stats/enforcement capabilities are tested. Keep Backhaul as a tunnel service adapter, not a client-protocol substitute.
4. **Android runtime decision:** decide whether the APK embeds a native core (ABI packaging, foreground service, VPNService/TUN integration, lifecycle and battery handling) or manages remote nodes only. Current code is a configuration builder and does not yet provide a working on-device VPN connection.
5. **Policy enforcement:** translate admin policy into core-supported actions only where the runtime supports them. If an engine cannot block a new source IP or disconnect a specific session safely, expose monitoring/alerting rather than claiming enforcement.
6. **Release gate:** protocol fixture tests, pinned-core config validation, Android ABI build checks, integration tests against disposable local nodes, secret-handling review, and a documented rollback procedure.

## Security boundaries

- Never expose the Xray management API to the public internet; bind it to loopback or a private authenticated channel.
- Do not infer an identity from an IP alone. Map an authenticated core identity (such as an Xray user email) to a Spd con account.
- Do not automatically rewrite REALITY keys, UUIDs, SNI, ports or transport parameters without validating them against the selected server.
- A passing config parser or a successful build does not prove a real network path works.
