# Spd con Cloudflare API

This is the optional cloud backend for the Android-first Spd con app. It does not require a separate web panel. The Android app now has an optional cloud card for account registration/login, uploading/downloading profiles and creating subscription links. Most config building, QR generation, local storage, export, tags/search and favorites remain in Android; this Worker provides online accounts, sync primitives and subscription links.

## Deploy with Cloudflare

Requirements: a Cloudflare account and Node.js/npm with Wrangler. No personal VPS is needed.

1. From this folder, install Wrangler locally: `npm install`.
2. Authenticate once: `npx wrangler login`.
3. Create the D1 database: `npx wrangler d1 create spd-con-db`.
4. Copy the returned database ID into `wrangler.toml`, replacing `REPLACE_WITH_D1_DATABASE_ID`.
5. Apply the schema: `npx wrangler d1 migrations apply spd-con-db --remote`.
6. Set secrets (use different random values, each at least 32 characters):
   `npx wrangler secret put APP_SECRET`
   `npx wrangler secret put BOOTSTRAP_ADMIN_TOKEN`
7. Deploy: `npx wrangler deploy`.

The API will be available at the Worker URL, for example `https://spd-con-api.<your-subdomain>.workers.dev`. Do not assume the example hostname; use the URL printed by Wrangler. Keep both secrets private. Keep APP_SECRET stable because it is used to hash sessions and subscription tokens.

## First admin account

Call `POST /api/v1/admin/bootstrap` with JSON `{"username":"admin","password":"use-a-long-unique-password"}` and header `X-Bootstrap-Token: <BOOTSTRAP_ADMIN_TOKEN>`. This endpoint only works before any admin exists. After creating the admin, remove the bootstrap secret with `npx wrangler secret delete BOOTSTRAP_ADMIN_TOKEN` (if you need future admin recovery, implement a separate secure recovery path first).

## Endpoints

- `GET /api/v1/health`
- `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/users/me`
- `GET/POST /api/v1/configs`, `PUT/DELETE /api/v1/configs/{id}`
- `POST /api/v1/subscriptions`, `GET /api/v1/subscriptions/{token}`, `DELETE /api/v1/subscriptions/id/{id}`
- `GET /api/v1/admin/users`, `PATCH /api/v1/admin/users/{id}/limits`

Auth uses opaque bearer tokens; only keyed hashes of sessions and subscription tokens are stored. Passwords use PBKDF2-SHA256 with unique random salts. Config ownership is checked server-side. The Worker returns generic server errors instead of leaking exception details.

## Important limits

- This is a deployable development foundation, not a production-ready traffic control plane.
- Quotas and expiry metadata do not enforce live VPN traffic until a trusted node agent reports usage. Do not present them as independently measured traffic usage.
- Subscription payloads are normalized JSON profiles, not yet the tested client-specific base64/Clash/sing-box formats.
- Add rate limiting, monitoring/audit events, schema/API integration tests, account recovery and node-agent authentication before public production use.
- No web panel is required; API can be called from the Android app. Cloudflare dashboard is used only for setup/operations.
