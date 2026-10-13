# Spd con API (initial Config Hub backend)

This is an optional initial, testable API foundation for local Docker-based development. It is not required by the Android-first app and is not yet the production control plane for live VPN nodes. For a no-VPS Cloudflare option, see [../cloudflare/README.md](../cloudflare/README.md).

## Run locally with Docker Compose

1. Copy `.env.example` to `.env`.
2. Generate separate long random values for `APP_SECRET` and `BOOTSTRAP_ADMIN_TOKEN` (at least 32 characters for the app secret).
3. Optionally set `PUBLIC_BASE_URL` to the externally reachable HTTPS API origin. Leave it blank for local development.
4. Run `docker compose up --build` from this directory.
5. Open `http://127.0.0.1:8000/panel/` only if you want to try the optional prototype dashboard, or `http://127.0.0.1:8000/docs` to explore the API.

Do not expose the development API to the public internet without TLS termination, rate limiting, secure secret management, monitoring and a production review. Do not commit `.env`.

## First admin bootstrap

1. Register the first account using `POST /auth/register` with a username and password of at least 12 characters.
2. Call `POST /admin/bootstrap` with the `X-Bootstrap-Token` header matching `BOOTSTRAP_ADMIN_TOKEN`. It promotes the earliest registered account only if no admin exists.
3. Sign in with `POST /auth/login`, then use the returned bearer token for admin routes.
4. Remove `BOOTSTRAP_ADMIN_TOKEN` from the runtime environment after bootstrap. Keep `APP_SECRET` stable; changing it invalidates existing session and subscription tokens.

## Initial web dashboard\n\nThe responsive `/panel/` UI supports account registration/login, config create/list/delete, subscription creation, and basic admin user enable/quota controls. It is a first prototype, not a polished production admin panel; config profiles are entered as JSON, and client-specific export formats are not implemented yet.\n\n## Implemented endpoints

- `GET /health/live`, `GET /health/ready`
- `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /users/me`
- `GET/POST /configs`, `PUT/DELETE /configs/{config_id}` — each account can access only its own configs
- `POST /subscriptions`, `GET /subscriptions/{token}`, `DELETE /subscriptions/{subscription_id}` — high-entropy token shown once, stored as a keyed hash, revocable and optionally expiring
- `GET /admin/users`, `PATCH /admin/users/{user_id}/limits`
- `POST /admin/users/{user_id}/usage`, `GET /admin/usage`
- `GET/PUT /admin/users/{user_id}/connection-policy` — store per-user IP/concurrency policy preferences
- `POST /admin/users/{user_id}/connections/report`, `GET /admin/users/{user_id}/connections` — record node-reported source IP, connection state and byte counters; admin-only in this scaffold

## Current limitations and safety boundaries

- SQLite with a persistent Docker volume is used for this first single-instance scaffold. The existing PostgreSQL/Redis services are not yet wired into the API. Do not scale multiple API replicas against this SQLite file.
- The quota counter and connection-observation endpoints accept reports through admin-authenticated API routes. No dedicated node enrollment/credential flow or node agent currently authenticates and sends telemetry; reports are not yet independent live traffic accounting.
- Connection policies (maximum distinct IPs, concurrent connections and requested action) are stored and evaluated against reported observations to produce alerts. They are **not enforced** by this API: `deny_new_ip` is a policy intent, not a live block, until a trusted node agent checks/enforces it at connection time.
- Connection records include source IP addresses and byte counters. Restrict admin access, define retention/deletion policy, and avoid collecting client labels unless needed. IPs may change due to mobile networks, carrier NAT, VPNs and IPv6 privacy addressing, so IP count is not a reliable unique-device identity.
- Subscription payloads currently return the service's normalized JSON profile list, not client-specific v2rayNG, Hiddify, Clash/Mihomo, sing-box or NPV Tunnel subscription formats.
- Registration/login rate limiting, email verification, password reset, session management UI, audit-log policy, database migrations and production deployment hardening remain to be implemented.
- No remote node enrollment, Xray-core runtime, live scanner/checker, web admin UI, Hysteria2/TUIC runtime or cross-device sync is implemented by this API scaffold yet.

## Tests

Install `requirements.txt`, then run:

```sh
python -m unittest discover -s tests -v
```

The test suite covers password hashing, account isolation, config persistence, subscription revocation, admin quota metadata, connection policy evaluation, IP validation and admin-only connection history.
