# Spd con

**Spd con** is an Android-first configuration builder and management console for VPN/Xray configurations, nodes, users, subscriptions, scanning, and checking.

> This repository is the foundation, not a finished production VPN panel. Remote node management requires a deployed backend and explicit credentials for each managed server.

## Project goals

- Build and validate VLESS, VMess, Trojan, and Shadowsocks profiles.
- Import/export supported share links and JSON, copy links, and display QR codes.
- Organize configuration profiles and maintain local history.
- Add node health, user/group management, subscriptions, scanner, and checker in staged releases.
- Keep server credentials out of the Android client; use an authenticated backend and node agent for remote operations.

## Architecture

- **Android:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room, Retrofit/OkHttp, Kotlin coroutines.
- **Backend (planned):** FastAPI, PostgreSQL, Redis, background workers, version-pinned Xray adapters.
- **Deployment (planned):** Docker Compose for API, worker, database, and Redis. Node agents are deployed separately on managed nodes.

## Repository layout

```text
android/                 Android application
backend/                 API service and deployment scaffolding
shared/config-schemas/   Shared config format notes and fixtures
docs/                    Architecture, API, security, roadmap
.github/workflows/       CI workflows
```

## Start the Android app

Open the `android/` directory in Android Studio (current stable version), allow Gradle sync, then run the `app` configuration on an emulator or device. Install the SDK requested by Gradle if prompted.

## Start the backend scaffold

From the repository root:

1. Copy `backend/.env.example` to `backend/.env` and replace development secrets.
2. Run `docker compose --env-file backend/.env -f backend/compose.yaml up --build`.

The API scaffold exposes `/health/live` and `/health/ready`. It is for local development only: do not expose it publicly until authentication, authorization, TLS, rate limiting, migrations, and secret management are implemented.

## Development status

- [x] Project structure and initial Android shell
- [x] Backend health-check scaffold and local Compose services
- [ ] Configuration model, parser, validator, QR and import/export
- [ ] Room persistence and configuration library
- [ ] Scanner/checker job pipeline
- [ ] Auth, roles, node management, users and subscriptions
- [ ] Security review, CI, tests and release packaging

## Licensing and integrations

Before copying code or assets from SpiderPanel, Blue-Knight-Panel, 3x-ui, Xray-core, Scan-Cloud, or DicodeConfigChecker, review each repository's current license and required notices. Prefer adapters and documented interfaces over blindly merging unrelated source trees.

## Security notes

- Never commit real API keys, node credentials, subscription secrets, or production environment files.
- Treat imported configuration links as untrusted input.
- Redact credentials and UUIDs from logs.
- Require explicit confirmation before remote changes to nodes or users.
