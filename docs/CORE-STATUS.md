# Spd con core/runtime status

This file separates configuration-generation support from a real running core. A UI field or exported JSON is not proof that the corresponding traffic engine is embedded, installed or connected.

## Current status

| Engine / component | Present in repository | What works today | What is still missing |
|---|---|---|---|
| Xray-core | Config generator and JSON tests only | VLESS + REALITY outbound preview/export and protocol diagnostics | No bundled/running Xray binary in the Android APK; no runtime config test against a pinned Xray release; no live API adapter, session enforcement or traffic accounting |
| Hysteria2 | Profile and share-link handling | Basic profile/URI generation and parsing | No Hysteria2 runtime, server install/enrollment, live handshake test or traffic policy enforcement |
| TUIC | Profile and share-link handling | Basic profile/URI generation and parsing | No TUIC runtime, server install/enrollment, live handshake test or traffic policy enforcement |
| Backhaul | TOML generator | Role-specific server/client templates for documented fields | No installed tunnel process, service supervisor, end-to-end tunnel test, reconnect/health monitor or production secret management |
| Cloudflare Worker | Metadata/API scaffold | Config metadata, account/subscription routes and starter panel | It does not carry arbitrary VPN traffic and cannot replace a proxy node/runtime |
| Node telemetry bridge | Initial Python bridge in `node-agent/` | Enrolled-node bearer authentication, input validation, stale-event and duplicate-event rejection, source-IP telemetry submission | No built-in Xray log parser/StatsService polling, no per-core session-kill or live policy enforcement |

## What “complete the cores” means in implementation terms

1. **Runtime contract:** define start/stop, version, health, config validation, logs, traffic stats and capability reporting for each supported engine.
2. **Xray first:** pin an exact Xray-core release and license; validate generated JSON with that release in CI; implement a node-side adapter for Xray API and access logs; use a loopback-only API listener and authenticated management channel.
3. **Other engines behind adapters:** add Hysteria2 and TUIC only after their installed versions, configuration schemas, health checks and per-user stats/enforcement capabilities are tested. Keep Backhaul as a tunnel service adapter, not a client-protocol substitute.
4. **Android runtime decision:** decide whether the APK embeds a native core (ABI packaging, foreground service, VPNService/TUN integration, lifecycle and battery handling) or manages remote nodes only. Current code is a configuration builder and does not yet provide a working on-device VPN connection.
5. **Policy enforcement:** translate admin policy into core-supported actions only where the runtime supports them. If an engine cannot block a new source IP or disconnect a specific session safely, expose monitoring/alerting rather than claiming enforcement.
6. **Release gate:** protocol fixture tests, pinned-core config validation, Android ABI build checks, integration tests against disposable local nodes, secret-handling review, and a documented rollback procedure.

## Security boundaries

- Never expose the Xray management API to the public internet; bind it to loopback or a private authenticated channel.
- Do not infer an identity from an IP alone. Map an authenticated core identity (such as an Xray user email) to a Spd con account.
- Do not automatically rewrite REALITY keys, UUIDs, SNI, ports or transport parameters without validating them against the selected server.
- A passing config parser or a successful build does not prove a real network path works.
