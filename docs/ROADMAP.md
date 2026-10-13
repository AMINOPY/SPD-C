# Implementation roadmap

## Phase 0 — repository and license review
- Review source trees, licenses, dependencies, APIs and release activity of SpiderPanel, Blue-Knight-Panel, 3x-ui, Xray-core, Scan-Cloud and DicodeConfigChecker.
- Record exact versions, attribution and license obligations before reusing code or assets.
- Define protocol compatibility against a pinned Xray release.

## Phase 1 — Android foundation (started)
- [x] Android Compose dashboard and application shell.
- [x] Typed configuration model and required-field validation.
- [x] Initial VLESS/Trojan URI and VMess Base64 JSON parsing.
- [x] Parser/model unit-test source.
- [ ] Confirm CI build and tests pass.
- [ ] Add navigation, Persian RTL polish, reusable design system and accessibility.
- [ ] Add Room-backed local configuration library.

## Phase 2 — complete configuration engine
- Add Shadowsocks support and round-trip serializers for supported protocols.
- Validate protocol-specific transport/security combinations against a pinned engine version.
- Add copy, QR, JSON/link import-export, search, tags, duplicate detection and version history.
- Add malformed-input tests and redact secrets from diagnostics.

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

## Version 0.1 acceptance criteria
- App builds and renders on supported Android versions.
- User can parse and validate supported share links with understandable errors.
- Local save/edit/search, serializers, QR and import/export work without a backend.
- Persian RTL layout and meaningful validation errors.
- Automated tests cover valid and malformed configuration inputs.
- No credentials or private configuration material are logged.
