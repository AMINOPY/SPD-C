# Implementation roadmap

## Phase 0 — repository and license review
- Review source trees, licenses, dependencies, APIs and release activity of SpiderPanel, Blue-Knight-Panel, 3x-ui, Xray-core, Scan-Cloud and DicodeConfigChecker.
- Record exact versions, attribution and license obligations before reusing code or assets.
- Define protocol compatibility against a pinned Xray release.

## Phase 1 — Android foundation (in progress)
- [x] Compose dashboard and application shell.
- [x] Typed configuration model and required-field validation.
- [x] Initial VLESS/Trojan URI and VMess Base64-JSON parsing.
- [x] Initial Shadowsocks legacy share-link parsing.
- [x] Share-link serializers for VLESS, Trojan, VMess and the supported Shadowsocks form.
- [x] Fragment settings model, range validation and editable network-labelled starting presets.
- [x] REALITY fields, validation, share-link mapping and VLESS + REALITY JSON preview.
- [x] UI controls for importing, inspecting and generating preview output.
- [x] Confirm GitHub Actions build and unit tests pass for CI run 38372318614.
- [x] Add local SQLite library persistence and credential encryption with Android Keystore AES-GCM.
- [ ] Add navigation, accessibility, and polished RTL layouts.

## Phase 2 — complete configuration engine
- [ ] Add broader Shadowsocks URI dialect support and protocol-specific transport/security validation.
- Add tested Xray JSON import/export and explicit routing/chaining generation for Fragment.
- [x] Add QR generation, clipboard and Android share sheet for link/JSON output.
- [ ] Add file import/export, tags, search, duplicate detection and history.
- Add malformed-input tests, compatibility fixtures and version-pinned Xray validation.

## Phase 3 — scanner and checker
- Create cancellable, bounded-concurrency jobs with progress and structured results.
- Restrict scans to user-supplied or explicitly authorized targets.
- Separate syntax validation from connectivity checks; report timeout and TLS/transport errors clearly.

## Phase 4 — backend and nodes
- Add PostgreSQL schema/migrations, authentication, role-based authorization, audit logging and rate limiting.
- Add node enrollment, health checks, capability detection and sync previews.
- Implement an Xray adapter pinned to a tested version; never silently overwrite remote configs.

## Phase 5 — users and subscriptions
- Add user/group lifecycle, limits, expiry, node assignments and usage records.
- Add revocable subscription tokens, expiry and access logs.
- Add backup/restore and export permissions.

## Phase 6 — hardening and release
- Add integration tests, dependency scanning, secret scanning, CI and signed release artifacts.
- Verify TLS, backup restoration, migration strategy and log redaction.
- Document threat model and deployment runbook.

## Important limitations
- Network-labelled Fragment presets are editable heuristics, not measured ISP-specific settings or guaranteed DPI bypasses.
- The generated Fragment Freedom outbound is separate; automatic routing/chaining to a selected VLESS connection is not yet implemented.
- REALITY JSON preview is not a live connection test. The app does not yet bundle or run Xray-core.
