# Spd con

**Spd con** is an Android-first configuration builder and management client. It is being built incrementally: offline configuration tools first, followed by scanning/checking and an optional backend for remote nodes, users, and subscriptions.

> Current status: initial Android dashboard, typed configuration model, VLESS/Trojan URI parser, and VMess Base64-JSON parser are in the feature branch. The project is not yet a production-ready VPN panel.

## Product goals

- Android app using Kotlin, Jetpack Compose, and Material 3.
- Read and validate common VLESS, VMess, and Trojan share links; add complete serializers, Shadowsocks support, QR, and local storage next.
- Persian RTL interface with localization separated from business logic.
- Later: node health/management, user and group management, subscriptions, scan/check jobs, audit history, role-based access, and backups.
- Optional FastAPI backend with PostgreSQL, Redis workers, and controlled node-agent integration.

## Architecture

```text
Android (Compose UI)
  ├── config domain/parser + feature modules
  ├── core: design system, Room, network, security, common utilities
  └── repository interfaces
          │ HTTPS (later, optional)
          ▼
FastAPI API ── PostgreSQL
     └─────── Redis + background worker
                     │
                authenticated node agent
                     │
                 version-pinned Xray adapter
```

The Android client should work locally without a personal server. Remote multi-node management requires deploying the backend and configuring nodes.

## Repository layout

- `android/` — Kotlin Android application.
- `backend/` — FastAPI health endpoint and local Compose scaffold.
- `docs/` — architecture, roadmap, and config format notes.
- `.github/workflows/` — Android CI.

## Build Android app

Open `android/` in Android Studio, install the Android SDK version requested by Gradle, sync, and run the `app` configuration. CI builds a debug APK.

## Run backend locally

From the repository root:

1. Copy `backend/.env.example` to `backend/.env` and replace the development password.
2. Run `docker compose --env-file backend/.env -f backend/compose.yaml up --build`.
3. Check `http://localhost:8000/health/live`.

The compose stack is for local development, not production. Do not expose PostgreSQL or Redis publicly. The API does not yet implement authentication or remote node operations.

## Development status

- [x] Android dashboard shell.
- [x] Typed configuration model and required-field validation.
- [x] Initial VLESS and Trojan URI parsing.
- [x] Initial VMess Base64 JSON parsing.
- [x] Parser/model unit-test source.
- [x] Backend health endpoint and local Compose scaffold.
- [ ] Run CI and resolve any build/test failures.
- [ ] Shadowsocks parser, protocol serializers, QR, clipboard, and import/export UI.
- [ ] Room persistence, config library, search, tags, and history.
- [ ] Scanner/checker job pipeline.
- [ ] Authentication, roles, node management, users, and subscriptions.
- [ ] Security review, release packaging, and deployment runbook.

## Security and upstream integration

- Never commit API keys, node credentials, private keys, or production environment files.
- Treat imported links as untrusted input; parsing does not prove a connection will work.
- Redact UUIDs, credentials, and subscription tokens from logs.
- Scanner/checker must only test targets the operator is authorized to assess.
- Review each upstream repository's current license and required notices before reusing code. Prefer adapters over copying entire source trees.
