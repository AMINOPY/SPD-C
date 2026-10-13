# Spd con integration plan: SpiderPanel + Z-E-U-S config builder, Marzban, PasarGuard, Shade, Backhaul and TWP-CF

## Product direction

Spd con remains Android-first. The Android app is the simple configuration builder and local library; the Cloudflare Worker is the optional account/subscription web panel. A node runtime is a separate component: Cloudflare Workers cannot run Xray-core or route proxy traffic.

The source-to-feature matrix is maintained in docs/REFERENCE-MATRIX.md. The aim is to learn from the requested projects while building a coherent product, not to merge unrelated repositories blindly.

## Feature borrowing strategy

- **Configuration builder — SpiderPanel + Z-E-U-S only as primary references:** this is a deliberately separate product area. Study both projects' configuration-creation flows, supported protocol/security/transport combinations, REALITY and TLS fields, Fragment controls, link generation, QR/copy/export behavior, and validation. Preserve the user's earlier config requirements as a separate acceptance checklist, then merge only compatible items into one Spd con profile model. Do not let node administration, scanner, billing, or Telegram proxy features clutter the config-creation flow. The user reports that Z-E-U-S's owner granted permission to build with it; the repository's current LICENSE still states proprietary, non-commercial use and prohibits derivative works/redistribution without explicit written permission, so keep that authorization on record and verify its exact scope before copying or redistributing code. Build a clean-room compatible implementation where possible and preserve required notices if code reuse is covered by the permission.
- **SpiderPanel (overall product inspiration outside the builder):** prioritize user/config organization, subscriptions, and useful node-oriented workflows. For the builder itself, SpiderPanel and Z-E-U-S are the only primary reference projects; other repositories are supplemental only where they fill a previously requested gap.
- **Blue-Knight-Panel (web UX inspiration):** use a compact dashboard, clear user/usage/expiry summaries, config and subscription actions, and mobile-first admin flows. Do not claim parity until each workflow is implemented and tested.
- **Marzban (API and panel capability reference):** use its documented REST API patterns, multi-node model, multi-protocol user handling, traffic/expiry limits, client-compatible subscriptions, monitoring and backup concepts. Marzban is AGPL-3.0; API integration is preferred over copying its implementation, and code reuse requires a license review.
- **PasarGuard Panel and Node:** evaluate the panel for multi-node operations, protocol coverage including WireGuard/Hysteria2 where supported, periodic quotas, device/HWID limits, RBAC and subscription formats. Treat PasarGuard Node as a separate possible node service, not code to bundle into Android. The panel is AGPL-3.0 and the node repository is listed as GPL-3.0; review exact licenses and dependencies before any code reuse.
- **Shade (workflow and relay inspiration):** adopt the beginner-friendly setup wizard, export/import of working profiles, clear connection status, latency checks, relay pools with health-based fallback, and optional domain filtering as product ideas. Shade is a macOS client built around Google Apps Script HTTP/SOCKS relays, so its implementation is not an Android/Xray drop-in. The repository declares MIT; preserve its copyright/license notices if any code is later reused.
- **WARP repository:** the supplied 4nOnymou3/WARP URL could not be fetched as a public repository during this pass (the repository/file lookup returned 404). Do not assume its purpose or copy code until the exact accessible repository URL and license are confirmed.
- **3x-ui (optional node adapter):** use its documented API as a real-node integration target for inbound/client operations and traffic counters. Keep 3x-ui credentials server-side, never embed admin tokens in the Android app or expose them to a browser. Use least-privilege credentials if supported; otherwise isolate the adapter and restrict its network access.
- **Backhaul (separate Iran ↔ abroad tunnel module):** provide role-specific server/client configuration generation for supported upstream transports, validate matching transport/token/ports, and keep generated TOML version-aware. This is not the same as generating VLESS/VMess/etc. client profiles. The app currently generates text only; it does not install Backhaul, configure either server, or prove the tunnel works. Cloudflare orange-cloud compatibility must be treated as conditional: HTTP/WebSocket proxying supports only documented ports/protocols, not arbitrary TCP/UDP. Do not recommend leaked installers or unverified mirrors; link to the official upstream project and require an authorized, secure deployment path.
- **TWP-CF (separate Telegram Web Proxy module):** use the repository as a reference and provide a safe user-owned deployment guide/status flow. Do not copy source until its license/permission is clear. Never collect Cloudflare API tokens in a plain Android form or browser JavaScript; any future deployment automation must use scoped credentials and server-side secret storage. A syntactically valid Worker URL is not proof that Telegram proxying works.
- **Xray-core:** use a pinned, tested runtime for validating generated Xray JSON and performing real connection tests. JSON preview alone is not a connectivity check.
- **Scanners/checkers:** only scan user-supplied or explicitly authorized endpoints. Report DNS, TCP, TLS/SNI, transport, and timeout outcomes separately; a fast TLS handshake does not prove good tunnel throughput or a geographically local IP.

## Proposed implementation order

1. **Config builder and exports (highest priority)**
   - Use SpiderPanel + Z-E-U-S as the two primary references for the entire config-creation workflow. Build a feature-by-feature comparison and acceptance checklist before porting any code.
   - Cover VLESS, VMess, Trojan, Shadowsocks, Hysteria2 and TUIC with protocol-specific validation and tested import/export fixtures.
   - Keep UUID generation automatic where the protocol requires a UUID; TUIC also requires its independent password.
   - Make advanced fields collapsible; don't silently infer WS path, gRPC serviceName, or REALITY server keys from SNI.
2. **Web panel essentials**
   - Add config edit/duplicate/import/export, searchable lists, tags/favorites, subscription revocation and clear account expiry/quota state.
   - Separate reported usage from enforced quota until a trusted node integration is active.
3. **Common node-adapter interface**
   - Define shared operations: test connection, list inbounds/clients, read health/traffic, preview changes, then perform confirmed writes.
   - Add a read-only first adapter for a verified deployed panel (3x-ui or Marzban); evaluate PasarGuard integration using its documented API and node architecture.
   - Store node endpoint and secret only on the server side, encrypted at rest where supported; add explicit connection status and audited actions.
   - Verify API routes against the exact deployed version before enabling writes. Use preview-first create/update/disable operations with confirmation and rollback notes.
4. **Runtime-backed scanner and checker**
   - Add bounded concurrency, cancellation, rate limits and per-target result details.
   - Test candidate profiles with the actual runtime and credentials; only then call a profile working.
5. **Shade-inspired usability and reliability**
   - Add a guided setup flow that validates inputs before saving, with a simple path and an advanced path.
   - Add profile export/import with schema versioning and a warning before sharing secrets.
   - Add connection state, per-candidate latency, health checks, and configurable load-balancing policies with automatic fallback only when a real runtime can verify path health.
   - Make ad/adult-domain filtering optional and explicit; implement it in the actual supported runtime, show its source/update time, and provide a quick disable switch for broken sites.
   - Keep the macOS-specific UI and Google Apps Script relay architecture separate from the Android/Xray engine; reuse ideas first, code only after compatibility and license review.
6. **Backhaul and Telegram proxy modules (separate from config creation)**
   - Validate generated Backhaul TOML against a pinned official release and test server/client symmetry, token escaping, WSS certificate paths, forwarded port lists, and unsupported transports.
   - Keep TWP-CF as a distinct guided deployment/reference module until its license and a safe user-owned deployment workflow are verified.
   - Show explicit state labels: generated configuration, deployed service, and live-tested connection are different states.
7. **Release hardening**
   - Verify repository licenses and dependency licenses before code reuse; preserve attribution and notices.
   - Add integration tests, migrations, audit logs, secret redaction, rate limiting, backup/restore, and release artifacts before public deployment.

## Non-negotiable correctness and security notes

- A selected country is a preference, not proof that a Cloudflare anycast address physically belongs to that country.
- Load balancing and automatic failover must be based on recent real health checks; simply distributing links does not prove that a tunnel is working.
- REALITY pbk and shortId must match the server configuration; SNI alone cannot derive them.
- Adult-domain blocking must be configured in the actual compatible runtime and use its domain data. Saving a checkbox in a profile is not enforcement.
- Traffic quota and expiry are not enforced by a UI or Cloudflare metadata alone. They require trusted node counters and server-side enforcement.
- Do not copy code from any source repository until its license file and applicable obligations have been reviewed.
