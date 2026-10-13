# Spd con

**Spd con** is an Android-first configuration builder with an initial Config Hub API foundation. The current development branch contains a local Android config library and a separate FastAPI backend scaffold for accounts, config synchronization primitives and subscription tokens.

> Status: the Android build and unit tests have passed on earlier CI runs. The latest workflow is now also testing the backend; check the current run before relying on the latest commit. This remains a development project, not a production-ready VPN client or multi-node panel.

## Android features currently implemented

- Manual profile creation and common share-link parsing for VLESS, Trojan, VMess, and common legacy/SIP002 Shadowsocks formats.
- Share-link serialization for supported formats, common Shadowsocks plugin parameters, and bracketed IPv6 hosts.
- Input validation and tests for URL-safe VMess payloads, plus signs in credentials, and malformed input.
- REALITY fields: SNI, public key, shortId, fingerprint, spiderX, and optional Vision flow validation.
- Editable Fragment values and labelled starting presets for همراه اول, ایرانسل, and نت ملی / اتصال محدود.
- Xray JSON preview for VLESS + REALITY, QR-code generation, clipboard, and Android share-sheet actions.
- Local SQLite library to save, load, and delete profiles. Credentials are encrypted with AES-GCM using an Android Keystore key; other database metadata is not encrypted.

## Initial Config Hub API

The `backend/` directory contains a FastAPI API foundation with:

- Account registration/login/logout, opaque bearer sessions, salted password hashing, and initial Admin/User role checks.
- Per-user config create/list/update/delete endpoints.
- High-entropy subscription tokens that are returned once, stored as a keyed hash, checked for expiry, and revocable.
- Admin endpoints for enabling/disabling accounts, setting quota/expiry metadata, accepting trusted usage reports, and viewing reported totals.
- A persistent SQLite volume in the local Docker Compose setup.
- API tests for password hashing, config owner isolation, subscription revocation and admin limit metadata.

See [backend setup and API limitations](backend/README.md). The backend is not yet connected to the Android UI. The `/panel/` route serves an initial responsive web dashboard for login, basic config management, subscription creation, and admin quota controls.

## Build status and APK

- [GitHub Actions](https://github.com/AMINOPY/SPD-C/actions) — inspect the latest run for both Android and API test results and the debug APK artifact.
- [Development pull request](https://github.com/AMINOPY/SPD-C/pull/1)
- [Development branch](https://github.com/AMINOPY/SPD-C/tree/feat/spd-con-foundation)

After a successful workflow, download **spd-con-debug-apk** under **Artifacts**. It is a debug build, not a signed production release.

## Important limitations

- The Android app does not currently bundle or run Xray-core, so JSON generation is a preview/export operation, not a live connection test.
- The Fragment-enabled Freedom outbound is separate from the VLESS outbound. Automatic routing/chaining through Fragment is not implemented.
- Network-labelled Fragment presets are editable heuristics, not measured ISP profiles and not a guarantee of bypass or connectivity.
- Full Xray JSON import/export, tested client-specific subscription encodings for v2rayNG/Hiddify/Clash/sing-box/NPV Tunnel, Hysteria2/TUIC runtime support, DoH, file-based import/export, search/tags/history, live connectivity checks, and scanner/checker jobs remain incomplete.
- The API quota counter uses trusted usage reports. No node agent is connected to independently measure or enforce live tunnel traffic.
- The backend currently uses SQLite for a single instance. PostgreSQL/Redis integration, database migrations, rate limiting, full audit logging, a web admin UI, remote node enrollment and cross-device sync remain incomplete.
- Upstream repositories have not been merged or copied into this project. Review licenses, versions, notices, and APIs before adapting code.

## Repository layout

- `android/` — Kotlin Android application.
- `backend/` — FastAPI API, tests, and local Docker Compose setup.
- `docs/` — architecture, roadmap, and configuration format notes.
- `.github/workflows/` — Android and backend CI.

## Build Android locally

Open `android/` in Android Studio with JDK 17 and Android SDK 36 installed, sync Gradle, and run the `app` configuration. CI runs:

```sh
cd android
gradle testDebugUnitTest
gradle assembleDebug
```

## Run API tests locally

```sh
cd backend
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
```

## Run the backend locally with Docker Compose

1. Copy `backend/.env.example` to `backend/.env`.
2. Replace `APP_SECRET` with a randomly generated value of at least 32 characters, and set a separate long random `BOOTSTRAP_ADMIN_TOKEN`. Never commit `.env`.
3. Optionally set `PUBLIC_BASE_URL` to the HTTPS origin used for subscription links.
4. Run from the repository root: `docker compose --env-file backend/.env -f backend/compose.yaml up --build`.
5. Check `http://127.0.0.1:8000/health/live` and open `http://127.0.0.1:8000/docs`.

For first-admin setup and endpoint details, see [backend/README.md](backend/README.md). Do not expose the development API publicly without HTTPS, rate limiting, secret management, monitoring and a production security review.

## Security notes

- Never commit API keys, node credentials, private keys, or production environment files.
- Keep `APP_SECRET` stable; changing it invalidates existing bearer sessions and subscription tokens.
- Treat imported links as untrusted input; a successful parse does not prove connectivity.
- Redact UUIDs, credentials, and subscription tokens from logs.
- Scanner/checker features must only test targets the operator is authorized to assess.
- Do not reuse upstream source until its current license and notice requirements have been reviewed.
