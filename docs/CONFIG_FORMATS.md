# Configuration format support

## Implemented in the current branch

- Typed configuration model and basic required-field validation.
- VLESS and Trojan URI parsing; common VMess Base64-JSON parsing (including URL-safe, unpadded payloads); common legacy and SIP002 Shadowsocks parsing.
- Share-link serializers for VLESS, Trojan, VMess and the supported Shadowsocks URI subset, including plugin query preservation and IPv6 host formatting.
- Bounded input size for imported links.
- Fragment settings with range validation and editable starting presets labelled همراه اول, ایرانسل, and نت ملی / اتصال محدود.
- REALITY client fields for serverName/SNI, public key (pbk), shortId, fingerprint, spiderX and optional Vision flow validation.
- Xray JSON outbound generator for VLESS + REALITY, with an optional documented Freedom outbound containing Fragment settings.
- Android UI controls for manual profiles, link inspection, Fragment/REALITY editing, JSON preview, QR, clipboard and system share sheet.
- Local SQLite library with AES-GCM encrypted credentials using Android Keystore.

## Fragment and network presets

The presets are editable starting points only; they are not measured carrier profiles. There is no reliable universal set of DPI values: behavior varies by location, APN, time, protocol version and network policy. A selected preset does not test a carrier or guarantee connectivity or access during a national-network restriction.

Xray documents Fragment as outgoing TCP fragmentation under the Freedom outbound's `settings.fragment` object, with `packets` (`tlshello` or a write range), `length` (bytes) and `interval` (milliseconds). The generated `fragment-direct` outbound is separate from the VLESS proxy outbound; routing/chaining must be explicitly configured for a particular use case. This generator does not silently claim to fragment the VLESS-to-server handshake.

## REALITY validation

- `serverName` should match a server name configured by the server.
- `publicKey` is the server's public key (share-link parameter commonly named `pbk`).
- `shortId` must be 0-16 hexadecimal characters and have an even length.
- Fingerprint must be supported by the selected Xray version; `unsafe` is rejected.
- `spiderX` and optional `xtls-rprx-vision` flow are validated.
- The generator normalizes share-link `type=tcp` to Xray's `raw` transport name and only accepts RAW/TCP, XHTTP or gRPC for REALITY.

## Remaining engine work

- Broader Shadowsocks URI variants and full round-trip compatibility with every client dialect.
- Complete Xray routing/chaining generation so Fragment can be attached to a selected outbound intentionally.
- Full Xray JSON import/export, file-based import/export, search/tags/history, and live connectivity testing.
- Carrier-specific diagnostics based on real tests; the current presets are not measured network profiles.
- End-to-end validation against a pinned Xray-core version.

A successful parse or generated JSON does not prove that a configuration will connect. Keep Xray versions pinned and validate generated output against that version. Imported values are untrusted; do not log credentials, public/private keys, or subscription tokens.

## Multi-inbound generation service

`ConfigGenerationService` turns explicitly configured inbounds into a per-user list of VLESS share profiles:

- **REALITY:** TCP and/or XHTTP; requires the actual server SNI, public key (`pbk`) and a valid even-length hexadecimal `shortId`. The server private key is never needed by the client generator and must stay server-side.
- **TLS relay:** creates WS and XHTTP profiles using the configured relay routes (`/ws/{uuid}` and `/xhttp-siz10/{mode}/{uuid}`). Optional alternate addresses are only used when supplied by an operator/scanner; the configured TLS Host/SNI is preserved for IP variants.
- **Cloudflare Worker:** expands explicitly configured Worker route templates such as `/ws/{uuid}` or country-specific paths. A route is emitted only when it is configured in the input; the generator does not assume that a Worker serves a route merely because the URL looks plausible.
- **Stable identity:** the caller supplies a persisted user UUID. All links produced for that user reuse the same UUID across inbounds. `newStableUserId()` is a one-time provisioning helper; callers must persist its result rather than call it for every refresh.
- **Metadata:** generator-only labels are omitted from share-link query parameters. Adult-filter preference is retained on the profile, but a share link alone cannot enforce it; the generated client JSON/runtime and its domain lists must support the policy.

This service does not create the user on Xray, configure a FastAPI relay, deploy a Cloudflare Worker, scan Clean IPs, or test DPI behavior. Server-side provisioning and route compatibility must be completed separately. It produces candidate profiles only for the exact inbounds/routes supplied to it.

## DPI-resilience policy

No protocol, transport, SNI, fingerprint, Fragment preset or CDN route can guarantee resistance to DPI or availability on every carrier. The generator therefore does not fabricate REALITY keys, SNI values, short IDs, country paths or successful scanner results. Use a server-backed profile, validate it against the deployed core version, test connectivity on the target network, and prefer the profile that actually works there. CDN mode is valid only when DNS proxying, TLS certificate, origin and route are configured together. Cloudflare's ordinary HTTP proxy is not a generic UDP tunnel for Hysteria2/TUIC.

