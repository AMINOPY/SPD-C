# Spd con

**Spd con** is an Android-first configuration builder. The current branch focuses on local config creation, import/export, and a foundation for future authorized node checks and optional remote management.

> Status: the Android debug build and unit tests passed on the CI run linked below. The project is still under development and is not a production-ready VPN client or multi-node panel.

## Current Android features

- Manual profile creation and common share-link parsing for VLESS, Trojan, VMess, and common legacy/SIP002 Shadowsocks formats.
- Share-link serialization, including common Shadowsocks plugin parameters and bracketed IPv6 hosts.
- Basic input validation and tests for URL-safe VMess payloads, plus signs in credentials, and malformed input.
- REALITY fields: SNI, public key, shortId, fingerprint, spiderX, and optional Vision flow validation.
- Editable Fragment values and labelled starting presets for همراه اول, ایرانسل, and نت ملی / اتصال محدود.
- Xray JSON preview for VLESS + REALITY, QR-code generation, clipboard, and Android share-sheet actions.
- Local SQLite library to save, load, and delete profiles. Credentials are encrypted with AES-GCM using an Android Keystore key; the rest of the database metadata is not encrypted.
- GitHub Actions runs unit tests, builds a debug APK, and uploads it as a workflow artifact for 14 days.

## Build status and APK

- [Latest CI run](https://github.com/AMINOPY/SPD-C/actions/runs/38372318614) — unit tests, debug APK build, and artifact upload succeeded for that run.
- [Development pull request](https://github.com/AMINOPY/SPD-C/pull/1)
- [Development branch](https://github.com/AMINOPY/SPD-C/tree/feat/spd-con-foundation)

To get the APK, open a successful workflow run and download **spd-con-debug-apk** under **Artifacts**. It is a debug build, not a signed production release.

## Important limitations

- The app does not currently bundle or run Xray-core, so JSON generation is a preview/export operation, not a live connection test.
- The Fragment-enabled Freedom outbound is separate from the VLESS outbound. The generator does not yet automatically route or chain the selected proxy through Fragment.
- Network-labelled presets are editable heuristics, not measured ISP profiles and not a guarantee of bypass or connectivity.
- Local credential encryption uses a device-local Android Keystore key. Clearing app data, uninstalling the app, or losing the key can make old encrypted credentials unrecoverable.
- Full Xray JSON import/export, complete client-dialect compatibility, file-based import/export, search/tags, live connectivity checks, scanner/checker jobs, and remote nodes/users/subscriptions are not yet complete.
- Upstream repositories have not been merged or copied into this project. Review licenses, versions, notices, and APIs before adapting code.

## Repository layout

- `android/` — Kotlin Android application.
- `backend/` — local FastAPI health endpoint and Docker Compose development scaffold.
- `docs/` — architecture, roadmap, and configuration format notes.
- `.github/workflows/` — Android CI.

## Build locally

Open `android/` in Android Studio with JDK 17 and Android SDK 36 installed, sync Gradle, and run the `app` configuration. CI runs:

```sh
cd android
gradle testDebugUnitTest
gradle assembleDebug
```

## Run the backend scaffold locally

From the repository root:

1. Copy `backend/.env.example` to `backend/.env` and replace the development password.
2. Run `docker compose --env-file backend/.env -f backend/compose.yaml up --build`.
3. Check `http://localhost:8000/health/live`.

The Compose stack is for local development only. The API does not yet implement authentication or remote node operations. Do not expose PostgreSQL or Redis publicly.

## Security notes

- Never commit API keys, node credentials, private keys, or production environment files.
- Treat imported links as untrusted input; a successful parse does not prove connectivity.
- Redact UUIDs, credentials, and subscription tokens from logs.
- Scanner/checker features must only test targets the operator is authorized to assess.
- Do not reuse upstream source until its current license and notice requirements have been reviewed.
