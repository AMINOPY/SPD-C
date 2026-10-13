# Spd con — reference matrix (2026-10-13)

This document records which ideas from the requested projects should shape Spd con. It is a feature/architecture reference, not permission to copy source code. Check each repository's current license and dependencies before reusing code, assets, or substantial implementation.

## Product direction

Spd con stays Android-first with an optional Cloudflare Worker web panel. The Android app should be easy for everyday use; advanced node administration belongs behind authenticated server APIs. Cloudflare Workers can call remote node APIs, but they do not run Xray-core themselves. Real tunnel tests, usage accounting, quotas, and failover require a real runtime/node telemetry.

## Reference-to-feature mapping

| Reference | Use for Spd con | Important boundary |
|---|---|---|
| SpiderPanel | Primary reference for overall product workflows; one of the two primary config-builder references for config creation, profile lifecycle, supported options and validation | Reimplement against Spd con's own model unless exact license and compatibility review allows source reuse |
| Z-E-U-S (Z-E-U-S Panel) | Second primary config-builder reference: VLESS/Trojan over WebSocket, multi-proxy routing, fallback, Fragment/PattN-style fields, fingerprint/SNI controls, config links/QR and profile quotas as design references | Repository LICENSE currently says proprietary/non-commercial and prohibits derivatives/redistribution without explicit written permission. User reports owner permission; retain written scope and confirm it covers code adaptation, redistribution and any commercial use. Do not assume public source availability alone grants rights |
| Blue-Knight-Panel | Compact web admin experience, quick actions, readable user and node status | Inspiration only; do not claim feature parity without implemented tests |
| Marzban | REST API integration patterns, multi-node management, multi-protocol users, traffic/expiry limits, client-compatible subscriptions, monitoring | Marzban is AGPL-3.0; keep API integration separate from copying code and review obligations before reuse |
| PasarGuard Panel | Multi-node administration, VLESS/VMess/Trojan/Shadowsocks plus WireGuard/Hysteria2 where supported, device/HWID limits, periodic quotas, RBAC, subscription formats and monitoring | Panel repository is AGPL-3.0; validate exact version/API and licensing before reusing implementation |
| PasarGuard Node | Future option for a dedicated remote-node layer and decentralized node operations | Node repository is GPL-3.0 per organization metadata; treat as a separate service and review license obligations |
| 3x-ui | First optional node adapter target if its deployed API supports required operations | API routes differ by version; verify against the actual instance, store credentials server-side and begin read-only |
| Xray-core | Pinned runtime for JSON validation, real connection tests and runtime-backed routing | Do not label JSON preview as a working VPN connection; pin version and test compatibility |
| Shade | Setup wizard, import/export, clear connection status, measured latency, healthy relay pools and fallback UX | Shade is a macOS/Google Apps Script relay client; its UI/runtime is not a drop-in Android/Xray implementation. Repository declares MIT |
| Scan-Cloud / DicodeConfigChecker | Scanner/checker workflow and structured diagnostics | Only scan user-supplied or explicitly authorized targets; bound concurrency and use timeouts/rate limits |

## Implementation priorities

1. **Reliable config builder (highest product priority)**
   - Use SpiderPanel and Z-E-U-S as the only primary references for config creation. Produce a parity checklist by inspecting their current user-facing workflows and supported combinations; retain earlier user requirements as additional acceptance criteria.
   - Keep the builder as a focused, guided flow: protocol → transport/security → protocol-specific fields → validation → preview/export/QR. Keep advanced Fragment, fingerprint and unusual transport settings collapsed by default.
   - Share one typed profile model between Android and Cloudflare API.
   - Validate each protocol's required fields and support import/export with fixtures.
   - Keep automatic UUID generation for UUID-based protocols. For REALITY, never derive pbk or shortId from SNI.
   - Only advertise Hysteria2/TUIC exports after URI formats are verified against documented clients.

2. **Node integrations**
   - Define a common node-adapter interface: test connection, list inbounds, list clients, read traffic/health, preview changes, then perform confirmed writes.
   - Add Marzban and 3x-ui adapters behind that interface; evaluate PasarGuard as a full panel/node integration rather than duplicating all its backend code.
   - Store node secrets only in server-side secret bindings or protected backend storage. Never put panel passwords/API tokens in the Android APK or browser JavaScript.
   - Show per-node capabilities and API/version compatibility. Unknown capabilities should be disabled, not guessed.

3. **Users, devices, quotas and subscriptions**
   - Users/groups, expiry, enabled/disabled state, traffic quota and periodic quota.
   - Optional device/HWID limit only where the selected backend/runtime can enforce it.
   - Unique, revocable subscription tokens; tested output formats for v2rayNG, sing-box, Clash/Mihomo and other explicitly supported clients.
   - Separate reported usage from enforced usage until trusted node telemetry and server-side enforcement are active.

4. **Scanner/checker and health-based selection**
   - Cancellable, bounded-concurrency tests with DNS, TCP, TLS/SNI, protocol handshake, latency and timeout results shown separately.
   - A TLS handshake or open port is not proof of a working tunnel or good throughput.
   - Only label a profile as working after a real runtime test with valid server-side parameters.
   - Country selection is a preference unless backed by a verified candidate pool; Cloudflare anycast does not guarantee a specific country.

5. **Simple, polished experience**
   - Beginner path with a few guided questions; advanced settings collapsed by default.
   - Dashboard cards for configs, nodes, subscriptions, expiry and usage, with clear empty/error/loading states.
   - Search, tags, favorites, duplicate detection, QR, share/copy, safe profile import/export and backup/restore.
   - Persian RTL first, accessible controls, selectable themes and minimal technical jargon.
   - Load balancing/failover only when recent health checks and the actual runtime can verify the selected path.

6. **Production gates**
   - License and dependency review, attribution, API contract tests, migration tests, audit logs, redacted secrets, rate limiting and backup/restore validation.
   - No production deployment until HTTPS, strong secrets, database migrations, account recovery and operational monitoring are configured.

## License and source policy

- Marzban: AGPL-3.0.
- PasarGuard Panel: AGPL-3.0; PasarGuard Node: GPL-3.0 according to the organization repository listing.
- Shade: MIT.
- For SpiderPanel, Blue-Knight-Panel and other sources, inspect the exact LICENSE file and dependencies before reusing code. Missing/unclear license metadata means do not copy source code yet.
- An API integration is not the same as copying a codebase, but its authentication, data model and version compatibility still need tests.


## Separate modules — not part of the config-builder reference scope

- **Backhaul** is a dedicated Iran↔abroad tunnel utility. It generates separate role-specific configuration files and needs version-pinned validation; it does not belong in the VLESS/VMess/etc. builder.
- **TWP-CF** is a separate Telegram Web Proxy deployment/reference flow. Keep deployment, Cloudflare credentials and Worker status out of the config builder. The current module only links to the upstream and validates HTTPS URL syntax.

## Backhaul and Telegram proxy references
- **Musixal/Backhaul** (AGPL-3.0): use as the reference for role-specific TOML settings and supported transports. The app currently generates configuration text only; it does not install the daemon, create a working tunnel, or verify traffic.
- **ArasTey/TWP-CF**: the repository describes a single-file Cloudflare Worker deployer for TWP-CF and Cloudflare API diagnostics. GitHub repository metadata does not identify an SPDX license, so Spd con links to the project and does not copy its source. Never collect Cloudflare API tokens in the Android client; a later deploy integration must use least-privilege credentials and server-side secret handling.
- **Cloudflare CDN constraints**: do not promise that all Backhaul transports or UDP traffic work through the orange-cloud proxy. Keep Cloudflare compatibility warnings visible and require a live tunnel test before marking a route healthy.
