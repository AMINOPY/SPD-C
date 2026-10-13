# Spd con — Config Hub roadmap

This roadmap is the acceptance checklist for evolving the current Android configuration builder into a full Config Hub. A checkbox means implemented and verified, not merely designed or shown in the UI.

## Phase 0 — compatibility, security and license review
- [ ] Review source trees, licenses, dependencies, APIs and release activity of SpiderPanel, Blue-Knight-Panel, 3x-ui, Xray-core, Scan-Cloud and DicodeConfigChecker.
- [ ] Record exact versions, attribution and license obligations before reusing code or assets.
- [ ] Pin protocol compatibility to a tested Xray-core release and document export-format limitations.

## Phase 1 — Android configuration foundation
- [x] Compose dashboard and application shell.
- [x] Typed configuration model and required-field validation.
- [x] Initial VLESS/Trojan URI and VMess Base64-JSON parsing.
- [x] Initial Shadowsocks legacy share-link parsing.
- [x] Share-link serializers for VLESS, Trojan, VMess and the supported Shadowsocks form.
- [x] Fragment settings model, range validation and editable network-labelled starting presets.
- [x] REALITY fields, validation, share-link mapping and VLESS + REALITY JSON preview.
- [x] QR generation, clipboard and Android share-sheet sharing for supported output.
- [x] Local SQLite configuration library.
- [x] Encrypt saved credentials with Android Keystore AES-GCM; migrate plaintext credentials from database v1.
- [ ] Full manual builder coverage for each supported protocol and transport/security combination.
- [x] Simplify VLESS/VMess creation by generating UUID automatically; keep password input only for Trojan/Shadowsocks.
- [x] Collapse advanced Fragment and REALITY controls by default; use safe defaults for REALITY fingerprint and spiderX.
- [ ] Add real transport auto-detection by testing candidate transports against a configured server; TCP is only the current manual-builder fallback.
- [ ] Add tests for the new local search, tags and favorites flow; add duplicate detection and configuration change history.
- [ ] File import/export, bulk operations, backup and restore.
- [ ] Navigation, accessibility, polished RTL layouts and selectable themes.

## Phase 2 — protocol engine and interoperability
- [ ] Define a shared internal profile model for all supported protocols and transport options.
- [ ] Expand Shadowsocks method/plugin compatibility and add comprehensive import/export fixtures.
- [ ] Add tested Xray JSON import/export and protocol-specific validation.
- [ ] Add correct, explicitly validated Fragment routing/chaining rather than presenting a standalone outbound as connected Fragment support.
- [ ] Add tested exporters/importers for v2rayNG, Hiddify, Clash/Mihomo, sing-box and NPV Tunnel where their documented formats allow it.
- [ ] Add Hysteria2 and TUIC models, validation and tested share/export formats.
- [ ] Add encrypted DNS configuration (DoH) with explicit resolver settings.
- [ ] Generate REALITY client/server parameters only where the required key operations and roles are well-defined; validate dest, server name, public key, short ID and fingerprint.
- [ ] Add compatibility fixtures and regression tests for malformed, oversized and dialect-specific inputs.
- [ ] Integrate and pin Xray-core for actual configuration validation and connection/runtime features; a JSON preview alone is not a running VPN engine.

## Phase 3 — optional cloud services (Android-first, no web panel required)
- [x] Add an initial FastAPI API for account registration/login/logout, own-config CRUD, role checks, subscription creation/revocation, expiry and admin quota metadata.
- [x] Add Android account login, optional profile upload/download, and subscription-link actions for the Cloudflare Worker API (initial sync; conflicts are skipped, not merged).
- [x] Add a mobile-first Cloudflare `/panel` for login, config CRUD, subscription links and basic admin user controls.
- [x] Add a Cloudflare Worker route test for `/panel`.
- [ ] Add integration tests and production controls to the Cloudflare Worker; deploy only after setting a real D1 database ID and secrets.
- [x] Keep FastAPI/Docker as an optional local-development alternative; Cloudflare is the no-personal-VPS option.
- [x] Add initial opaque bearer sessions, password hashing, Admin/User authorization checks and owner-scoped config access.
- [ ] Add rate limiting, comprehensive audit logs, account recovery and production session-management controls.
- [x] Add an initial responsive web dashboard for login, basic config CRUD, subscription creation and admin quota controls.
- [ ] Build a production-ready web admin interface with full lifecycle workflows, validation, accessibility and visual polish; keep mobile Android as a first-class client. The current Cloudflare panel is a basic first version.
- [ ] Define API contracts, encryption and conflict handling before enabling multi-device sync.

## Phase 4 — nodes, scanner and checker
- [ ] Multi-node inventory, enrollment, capability detection, health checks and safe configuration sync previews.
- [ ] Cancellable, bounded-concurrency IP/SNI checks with progress and structured results.
- [ ] Restrict scans to user-supplied or explicitly authorized targets; apply timeouts and rate limits.
- [ ] Separate syntax validation from live connectivity tests; clearly report timeout, DNS, TLS and transport failures.
- [ ] Add live health/traffic metrics only when supported by a trusted runtime or node telemetry source.

## Phase 5 — users, quotas and subscriptions
- [ ] User/group lifecycle and node assignments.
- [ ] Per-user traffic quota, expiry date, enable/disable state and usage accounting.
- [ ] Unique, high-entropy subscription URLs with revocation, expiry, access controls and access logs.
- [ ] Subscription output in supported client formats, with format-specific tests.
- [ ] Backup/restore for configuration and metadata, including integrity checks and documented secret handling.

## Phase 6 — multi-device sync and release hardening
- [ ] Authenticated sync across devices with conflict handling and explicit secret protection.
- [x] Add initial API tests for password hashing, config owner isolation, subscription revocation and admin limit metadata.
- [ ] Add integration tests for migrations, actual node quota enforcement and restore.
- [ ] Dependency/security scanning, secret scanning, CI and signed release artifacts.
- [ ] Verify TLS, restore procedures, migration strategy, log redaction and a deployment runbook.

## Acceptance criteria for a Config Hub
- A profile can be created manually, imported, validated, edited, saved, searched, tagged, exported and represented as a QR code.
- Every advertised client export is tested against that client's documented format; unsupported features are reported rather than silently dropped.
- Subscription limits and expiry are enforced server-side, not only in the Android UI.
- Node operations require authentication and authorization, have audit trails, and do not silently overwrite remote configuration.
- Connectivity status is based on a real test/runtime, not inferred from syntactic validity.

## Current verified limitations
- The Android app has an optional Cloudflare sync card; the Worker now serves a basic `/panel` page as well. The FastAPI/Docker backend is a local-development alternative and uses SQLite in one instance.
- Quota/usage is currently based on trusted admin usage reports; no node agent exists to independently measure or enforce tunnel traffic.
- Subscription response is a normalized JSON profile list, not yet a tested v2rayNG/Hiddify/Clash/sing-box/NPV-specific subscription encoding.
- Before public deployment, configure strong APP_SECRET and bootstrap token, HTTPS, request rate limits, audit logging, and production-grade database migrations.
- This is still an Android-first prototype plus optional cloud API scaffolds, not a production-ready Config Hub.
- No Xray-core runtime is currently bundled or running; generated Xray JSON is a preview/export.
- The generated Fragment Freedom outbound is separate; automatic routing/chaining to a selected VLESS connection is not implemented.
- Network-labelled Fragment presets are editable heuristics, not measured ISP profiles and do not guarantee connectivity or bypass.
- Local credentials are encrypted at rest using Android Keystore AES-GCM; other SQLite metadata is not encrypted.
- Remote nodes, real traffic enforcement, live scanner/checker, production admin workflows and conflict-safe multi-device sync remain incomplete. The Cloudflare panel is a starter UI, not a full Blue-Knight-Panel replacement.
