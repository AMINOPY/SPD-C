# Spd con

**Spd con** is an Android-first configuration builder with an initial Config Hub API foundation. The development branch prioritizes local Android features and offers two optional backend paths: a local FastAPI/Docker scaffold and a Cloudflare Workers + D1 API. A separate web panel is not required.

> Status: the Android build and unit tests have passed on earlier CI runs. The latest workflow is now also testing the backend; check the current run before relying on the latest commit. This remains a development project, not a production-ready VPN client or multi-node panel.

## Android features currently implemented

- Manual profile creation and common share-link parsing for VLESS, Trojan, VMess, and common legacy/SIP002 Shadowsocks formats.
- Share-link serialization for supported formats, common Shadowsocks plugin parameters, and bracketed IPv6 hosts.
- Input validation and tests for URL-safe VMess payloads, plus signs in credentials, and malformed input.
- REALITY fields: SNI, public key, shortId, fingerprint, spiderX, and optional Vision flow validation.
- Editable Fragment values and labelled starting presets for همراه اول, ایرانسل, and نت ملی / اتصال محدود.
- Xray JSON preview for VLESS + REALITY, QR-code generation, clipboard, and Android share-sheet actions.
- Local SQLite library to save, load, delete, search and filter profiles; local favorites and editable tags are supported. Credentials are encrypted with AES-GCM using an Android Keystore key; other database metadata is not encrypted.

## Optional cloud sync and subscriptions (no web panel required)

The `cloudflare/` directory contains a Cloudflare Workers API with a D1 schema and Wrangler configuration for accounts, bearer sessions, user-scoped config sync and unique revocable subscription links. Most day-to-day features should stay inside Android and work offline; Cloudflare is only for optional online services. Follow [cloudflare/README.md](cloudflare/README.md) to deploy. Deployment still requires creating your own D1 database and setting secrets in your Cloudflare account.

## Initial local Config Hub API

The `backend/` directory contains a FastAPI API foundation with:

- Account registration/login/logout, opaque bearer sessions, salted password hashing, and initial Admin/User role checks.
- Per-user config create/list/update/delete endpoints.
- High-entropy subscription tokens that are returned once, stored as a keyed hash, checked for expiry, and revocable.
- Admin endpoints for enabling/disabling accounts, setting quota/expiry metadata, accepting trusted usage reports, and viewing reported totals.
- A persistent SQLite volume in the local Docker Compose setup.
- API tests for password hashing, config owner isolation, subscription revocation and admin limit metadata.

See [backend setup and API limitations](backend/README.md). The FastAPI scaffold is an alternative for local Docker testing and is not connected to the Android UI yet. Its `/panel/` page is optional; the intended product does not require a web dashboard.

## Build status and APK

- [GitHub Actions](https://github.com/AMINOPY/SPD-C/actions) — open the latest successful run and download `spd-con-debug-apk` under **Artifacts**.
- **Important for phone users:** commits do not update an already-installed app. Download the APK artifact from the newest successful run and install it; the artifact is a ZIP containing the debug APK. If Android blocks installation, allow installation from the browser/files app you used.
- The country field is still a preference only. The Android scanner now has a bounded TLS IP check and an DNS resolver compatibility mode (A/TXT/random-subdomain probes, maximum 256 candidates) with selectable UDP, TCP, and UDP-first/TCP-fallback transport; it does not yet support country-generated ranges, IPv6, pause/resume, durable scan history/export, speed tests, or full end-to-end tunnel verification. VLESS/VMess user IDs are hidden and generated automatically, but the same user must be provisioned on the server. Adult-site filtering is only emitted in the Xray JSON export path; a normal share link does not enforce it.
- [Development pull request](https://github.com/AMINOPY/SPD-C/pull/1)
- [Development branch](https://github.com/AMINOPY/SPD-C/tree/feat/spd-con-foundation)

After a successful workflow, download **spd-con-debug-apk** under **Artifacts**. It is a debug build, not a signed production release.

## Important limitations

- The Android app does not currently bundle or run Xray-core, so JSON generation is a preview/export operation, not a live connection test.
- The Fragment-enabled Freedom outbound is separate from the VLESS outbound. Automatic routing/chaining through Fragment is not implemented.
- Network-labelled Fragment presets are editable heuristics, not measured ISP profiles and not a guarantee of bypass or connectivity.
- Full Xray JSON import/export, tested client-specific subscription encodings for v2rayNG/Hiddify/Clash/sing-box/NPV Tunnel, Hysteria2/TUIC runtime support, DoH, file-based import/export and backup/restore, change history, live tunnel connectivity checks, and advanced scanner/checker jobs remain incomplete.
- The API quota counter uses trusted usage reports. The node agent can submit authenticated connection telemetry and cumulative byte-counter deltas now update API usage totals; however, it still does not automatically collect Xray access logs/StatsService counters or enforce live tunnel quotas.
- The optional FastAPI backend currently uses SQLite for a single instance. The Cloudflare Worker uses D1 but still needs production rate limiting, audit logging, integration tests, Android API integration and trusted node telemetry. Remote node enrollment and cross-device sync remain incomplete.
- Upstream repositories have not been merged or copied into this project. Review licenses, versions, notices, and APIs before adapting code.

## Repository layout

- `android/` — Kotlin Android application.
- `backend/` — optional FastAPI API, tests, and local Docker Compose setup.
- `cloudflare/` — optional Cloudflare Worker API and D1 migration.
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
