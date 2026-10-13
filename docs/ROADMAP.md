# Implementation roadmap

## Phase 0 — repository and license review
- Review source trees, licenses, dependencies, APIs and release activity of each inspiration project.
- Record attribution and license obligations before reusing code or assets.
- Define protocol compatibility against a pinned Xray release.

## Phase 1 — Android foundation
- Establish app navigation, Persian RTL support, theme, reusable components and accessibility.
- Add Room-backed configuration library and repository tests.
- Set up formatting, unit tests and GitHub Actions APK build.

## Phase 2 — configuration engine
- Implement typed models for VLESS, VMess, Trojan and Shadowsocks.
- Parse and generate supported share links; validate required fields and supported transport/security combinations.
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
- Android project opens in Android Studio and renders the dashboard.
- Backend health endpoint runs locally in Docker Compose.
- No real secrets are committed.
- Next increment is the configuration domain model and tested parser/serializer.
