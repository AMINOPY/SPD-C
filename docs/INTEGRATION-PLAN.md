# Spd con integration plan: SpiderPanel, Blue-Knight-Panel, Shade, WARP and 3x-ui

## Product direction

Spd con remains Android-first. The Android app is the simple configuration builder and local library; the Cloudflare Worker is the optional account/subscription web panel. A node runtime is a separate component: Cloudflare Workers cannot run Xray-core or route proxy traffic.

## Feature borrowing strategy

- **SpiderPanel (primary product inspiration):** prioritize a short configuration flow, config lifecycle, user/subscription organization, and useful node-oriented workflows. Reimplement compatible behavior against Spd con's own models rather than copying source files before license review.
- **Blue-Knight-Panel (web UX inspiration):** use a compact dashboard, clear user/usage/expiry summaries, config and subscription actions, and mobile-first admin flows. Do not claim parity until each workflow is implemented and tested.
- **Shade (workflow and relay inspiration):** adopt the beginner-friendly setup wizard, export/import of working profiles, clear connection status, latency checks, relay pools with health-based fallback, and optional domain filtering as product ideas. Shade is a macOS client built around Google Apps Script HTTP/SOCKS relays, so its implementation is not an Android/Xray drop-in. The repository declares MIT; preserve its copyright/license notices if any code is later reused.
- **WARP repository:** the supplied `4nOnymou3/WARP` URL could not be fetched as a public repository during this pass (the repository/file lookup returned 404). Do not assume its purpose or copy code until the exact accessible repository URL and license are confirmed.
- **3x-ui (optional node adapter):** use its documented API as the first real-node integration target for inbound/client CRUD and traffic counters. Keep 3x-ui credentials server-side, never embed admin tokens in the Android app or expose them to a browser. Use a dedicated least-privilege credential if the deployed 3x-ui version supports one; otherwise isolate the adapter and restrict its network access.
- **Xray-core:** use a pinned, tested runtime for validating generated Xray JSON and performing real connection tests. JSON preview alone is not a connectivity check.
- **Scanners/checkers:** only scan user-supplied or explicitly authorized endpoints. Report DNS, TCP, TLS/SNI, transport, and timeout outcomes separately; a fast TLS handshake does not prove good tunnel throughput or a geographically local IP.

## Proposed implementation order

1. **Config builder and exports**
   - Cover VLESS, VMess, Trojan, Shadowsocks, Hysteria2 and TUIC with protocol-specific validation and tested import/export fixtures.
   - Keep UUID generation automatic where the protocol requires a UUID; TUIC also requires its independent password.
   - Make advanced fields collapsible; don't silently infer WS path, gRPC serviceName, or REALITY server keys from SNI.
2. **Web panel parity essentials**
   - Add config edit/duplicate/import/export, searchable lists, tags/favorites, subscription revocation and clear account expiry/quota state.
   - Separate reported usage from enforced quota until a trusted node integration is active.
3. **3x-ui node adapter**
   - Store node endpoint and secret only on the server side, encrypted at rest where the platform permits; add explicit connect/test status and audited actions.
   - Start read-only: list inbounds/clients and traffic. Then add preview-first client create/update/disable operations with confirmation and rollback notes.
   - Verify API routes against the exact deployed 3x-ui version before enabling writes; API details can change between releases.
4. **Runtime-backed scanner and checker**
   - Add bounded concurrency, cancellation, rate limits and per-target result details.
   - Test candidate profiles with the actual runtime and credentials; only then call a profile working.
5. **Shade-inspired usability and reliability**
   - Add a guided setup flow that validates inputs before saving, with a simple path and an advanced path.
   - Add profile export/import with schema versioning and a warning before sharing secrets.
   - Add connection state, per-candidate latency, health checks, and configurable load-balancing policies with automatic fallback only when a real runtime can verify path health.
   - Make ad/adult-domain filtering optional and explicit; implement it in the actual supported runtime, show its source/update time, and provide a quick disable switch for broken sites.
   - Keep the macOS-specific UI and Google Apps Script relay architecture separate from the Android/Xray engine; reuse ideas first, code only after compatibility and license review.
6. **Release hardening**
   - Verify repository licenses and dependency licenses before code reuse; preserve attribution and notices.
   - Add integration tests, migrations, audit logs, secret redaction, rate limiting, backup/restore, and release artifacts before public deployment.

## Non-negotiable correctness and security notes

- A selected country is a preference, not proof that a Cloudflare anycast address physically belongs to that country.
- Load balancing and automatic failover must be based on recent real health checks; simply distributing links does not prove that a tunnel is working.
- REALITY `pbk` and `shortId` must match the server configuration; SNI alone cannot derive them.
- Adult-domain blocking must be configured in the actual compatible runtime and use its domain data. Saving a checkbox in a profile is not enforcement.
- Traffic quota and expiry are not enforced by a UI or Cloudflare metadata alone. They require trusted node counters and server-side enforcement.
- Do not copy code from any source repository until its license file and applicable obligations have been reviewed.
