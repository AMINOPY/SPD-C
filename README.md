# Spd con

**Spd con** is an Android-first configuration builder and management client. It is being built incrementally: local configuration tools first, then scanning/checking and an optional backend for remote nodes, users, and subscriptions.

> Current status: Android dashboard, common share-link parsing/serialization, Fragment presets and REALITY JSON preview are in the development branch. It is not yet a production-ready VPN panel.

## Current config-engine work

- Parse VLESS, Trojan and common VMess share links, plus an initial legacy Shadowsocks URI form.
- Serialize VLESS, Trojan, VMess and the supported Shadowsocks URI form.
- Validate basic config fields and imported input size.
- Edit Fragment parameters and select labelled starting presets for همراه اول, ایرانسل, and نت ملی / اتصال محدود.
- Validate REALITY SNI, public key, shortId, fingerprint, spiderX and optional Vision flow.
- Preview client-side Xray JSON for VLESS + REALITY.

Fragment presets are not measured carrier profiles and cannot guarantee connectivity or bypass. The current JSON generator adds a separate Fragment-enabled Freedom outbound; explicit routing/chaining is still future work. A generated JSON preview does not prove a connection will work.

## Architecture

```text
Android (Compose UI)
  ├── config domain, parser/serializer and feature UI
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

Open `android/` in Android Studio, install the Android SDK version requested by Gradle, sync, and run the `app` configuration. GitHub Actions runs unit tests and builds a debug APK; check the workflow result before treating the build as verified.

## Run backend locally

From the repository root:

1. Copy `backend/.env.example` to `backend/.env` and replace the development password.
2. Run `docker compose --env-file backend/.env -f backend/compose.yaml up --build`.
3. Check `http://localhost:8000/health/live`.

The compose stack is for local development, not production. Do not expose PostgreSQL or Redis publicly. The API does not yet implement authentication or remote node operations.

## Security and upstream integration

- Never commit API keys, node credentials, private keys, or production environment files.
- Treat imported links as untrusted input; parsing does not prove a connection will work.
- Redact UUIDs, credentials, and subscription tokens from logs.
- Scanner/checker must only test targets the operator is authorized to assess.
- Review each upstream repository's current license and required notices before reusing code. Prefer adapters over copying entire source trees.
