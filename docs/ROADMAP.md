# Spd con — Config Hub roadmap

This roadmap is the acceptance checklist for evolving the current Android configuration builder into a full Config Hub. A checkbox means implemented and verified, not merely designed or shown in the UI.

## Phase 0 — compatibility, security and license review
- [ ] Review source trees, licenses, dependencies, APIs and release activity of SpiderPanel, Blue-Knight-Panel, 3x-ui, Xray-core, Scan-Cloud and DicodeConfigChecker.
- [x] Document the staged feature strategy for SpiderPanel (primary product inspiration), Blue-Knight-Panel (web UX inspiration), Shade (setup/import-export/relay reliability ideas) and a server-side 3x-ui node adapter in `docs/INTEGRATION-PLAN.md`.
- [ ] Confirm the exact public repository URL and license for `4nOnymou3/WARP`; the supplied repo path returned 404 in this review, so no features are attributed to it yet.
- [ ] Implement Shade-inspired guided setup, safe profile import/export, real health-based relay failover, and optional runtime-backed domain filtering.
- [x] Initial scanner license metadata check: Clean-IP-Scanner and cfray report MIT; SpiderPanel, Blue-Knight-Panel, WhiteDNS-cleanip-finder and Exelite-Scanner do not expose a recognized SPDX license in repository metadata, so do not copy their code until the license files are checked.
- [ ] Record exact versions, attribution and license obligations before reusing code or assets.
- [ ] Pin protocol compatibility to a tested Xray-core release and document export-format limitations.

## Current usability hardening (2026-10-13)
- [x] Wire the guided setup wizard into the actual config form so network goal presets populate protocol, transport, security, port and ALPN.
- [x] Add an optional Cloudflare CDN mode that aligns VLESS + WS + TLS, Host, SNI and Path defaults, with domain validation.
- [x] Fix IPv4-literal detection in hostname hints.
- [x] Add backend storage/API foundations for admin-defined connection policies, source-IP observations, connection state and reported byte counters; report evaluation emits policy alerts.
- [x] Add enrolled-node credentials and a first telemetry bridge with one-time secret display, enabled/disabled nodes, source-IP validation, event freshness and duplicate rejection.
- [ ] Connect a trusted Xray runtime adapter to access logs and StatsService; current bridge consumes normalized JSONL events and does not yet tail arbitrary logs or query the core automatically.
- [ ] Add per-core policy enforcement for supported actions. The current bridge is telemetry-only; a generated share link cannot independently discover a connecting user's public IP, and policy actions are not yet applied to a running proxy.
- [ ] Add live DNS/Cloudflare proxy-state and origin compatibility checks before labeling a CDN profile as ready.

## Phase 1 — Android configuration foundation
- [x] Compose dashboard and application shell.
- [x] Typed configuration model and required-field validation.
- [x] Initial VLESS/Trojan URI and VMess Base64-JSON parsing.
- [x] Initial Shadowsocks legacy share-link parsing.
- [x] Share-link serializers for VLESS, Trojan, VMess and the supported Shadowsocks form.
- [x] Fragment settings model, range validation and editable network-labelled starting presets.
- [x] REALITY fields, validation, share-link mapping and VLESS + REALITY JSON preview.
- [x] QR generation, clipboard and Android share-sheet sharing for supported output.
- [x] Local SQLite configuration library.
- [x] Encrypt saved credentials with Android Keystore AES-GCM; migrate plaintext credentials from database v1.
- [ ] Full manual builder coverage for each supported protocol and transport/security combination.\n- [x] Add initial Backhaul server/client TOML generator for numeric forwarded ports and documented transports (ws/wsmux/wss/wssmux plus other upstream-supported transports); generate separate role-specific files.\n- [ ] Verify generated Backhaul TOML against pinned releases and add advanced port mapping, WSS certificate, and version-aware fields.\n- [x] Add a TWP-CF reference card with upstream links and HTTPS URL-format validation; no Cloudflare API token is collected or stored in the Android app.\n- [ ] Add a secure, user-owned Cloudflare deployment workflow for Telegram Web Proxy after reviewing upstream source and license.
- [x] Simplify VLESS/VMess creation by generating UUID automatically; keep password input only for Trojan/Shadowsocks.
- [x] Add a simple country target and adult-filter checkbox to the manual builder.
- [x] Add a multi-inbound VLESS generator to the Android form for REALITY TCP/XHTTP, TLS Relay WS/XHTTP and explicitly configured Cloudflare Worker routes; persist one local user UUID and save generated profiles automatically.
- [x] Add regression tests for stable identity, route expansion, REALITY parameter validation and preserving the UUID in per-user relay paths.
- [ ] Connect generated user IDs to authenticated server-side provisioning; current multi-inbound generation creates candidate links only and does not register users on Xray/Relay.
- [x] Add optional Xray geosite-category-porn routing to a blackhole outbound in VLESS+REALITY JSON export when the adult filter is selected.
- [x] Collapse advanced Fragment and REALITY controls by default; use safe defaults for REALITY fingerprint and spiderX.
- [ ] Add real transport auto-detection by testing candidate transports with a valid Xray runtime and server credentials; manual builder currently falls back to TCP, while imported links retain their declared transport.
- [ ] Add a real country-aware IP candidate pool and bounded health/latency/TLS checks; country selection is a preference only. Cloudflare anycast IPs cannot be guaranteed to belong to the selected country.
- [ ] Add tests for the new local search, tags and favorites flow; add duplicate detection and configuration change history.
- [ ] File import/export, bulk operations, backup and restore.
- [ ] Navigation, accessibility, polished RTL layouts and selectable themes.

## Phase 2 — protocol engine and interoperability
- [ ] Define a shared internal profile model for all supported protocols and transport options.\n- [ ] Add a versioned Backhaul capability profile so unsupported transports (including uwsmux unless a compatible implementation is confirmed) cannot be generated silently.
- [ ] Expand Shadowsocks method/plugin compatibility and add comprehensive import/export fixtures.
- [ ] Add tested Xray JSON import/export and protocol-specific validation.
- [ ] Add correct, explicitly validated Fragment routing/chaining rather than presenting a standalone outbound as connected Fragment support.
- [ ] Add tested exporters/importers for v2rayNG, Hiddify, Clash/Mihomo, sing-box and NPV Tunnel where their documented formats allow it.
- [ ] Add Hysteria2 and TUIC models, validation and tested share/export formats.
- [ ] Add encrypted DNS configuration (DoH) with explicit resolver settings.
- [ ] Generate REALITY client/server parameters only where the required key operations and roles are well-defined; validate dest, server name, public key, short ID and fingerprint.
- [ ] Add compatibility fixtures and regression tests for malformed, oversized and dialect-specific inputs.
- [x] Pin Xray-core v26.3.27 for CI and validate a representative VLESS + REALITY JSON fixture with the upstream binary.
- [ ] Validate the Android generator's exported JSON directly against the pinned runtime, then add Xray API/access-log telemetry and actual connection testing. CI fixture validation does not bundle Xray into Android or prove a profile can reach a server.

## Phase 3 — optional cloud services (Android-first, no web panel required)
- [x] Add an initial FastAPI API for account registration/login/logout, own-config CRUD, role checks, subscription creation/revocation, expiry and admin quota metadata.
- [x] Add Android account login, optional profile upload/download, and subscription-link actions for the Cloudflare Worker API (initial sync; conflicts are skipped, not merged).
- [x] Add a mobile-first Cloudflare `/panel` for login, config CRUD, subscription links, usage summary, per-config link display/copy and basic admin user controls.
- [ ] Add protocol-specific Cloudflare builder/link support for Hysteria2 and TUIC with comprehensive compatibility tests; initial UI and basic share-link serializers are now present.
- [x] Add web controls to update saved config country/adult-filter preferences.
- [x] Add a Cloudflare Worker route test for `/panel`.
- [ ] Add integration tests and production controls to the Cloudflare Worker; deploy only after setting a real D1 database ID and secrets.
- [x] Keep FastAPI/Docker as an optional local-development alternative; Cloudflare is the no-personal-VPS option.
- [x] Add initial opaque bearer sessions, password hashing, Admin/User authorization checks and owner-scoped config access.
- [ ] Add rate limiting, comprehensive audit logs, account recovery and production session-management controls.
- [x] Add an initial responsive web dashboard for login, basic config CRUD, subscription creation and admin quota controls.
- [ ] Build a production-ready web admin interface with full lifecycle workflows, validation, accessibility and visual polish; keep mobile Android as a first-class client. The current Cloudflare panel is a basic first version.
- [ ] Define API contracts, encryption and conflict handling before enabling multi-device sync.

## Phase 4 — nodes, scanner and checker
- [ ] Multi-node inventory, enrollment, capability detection, health checks and safe configuration sync previews.
- [ ] Cancellable, bounded-concurrency IP/SNI checks with progress and structured results.
- [ ] Restrict scans to user-supplied or explicitly authorized targets; apply timeouts and rate limits.
- [ ] Separate syntax validation from live connectivity tests; clearly report timeout, DNS, TLS and transport failures.
- [ ] Add live health/traffic metrics only when supported by a trusted runtime or node telemetry source.

## Phase 5 — users, quotas and subscriptions
- [ ] User/group lifecycle and node assignments.
- [x] Account authenticated node-reported per-connection byte-counter deltas into usage events and per-user used-byte totals.
- [ ] Enforce quota/expiry/disable policies in live tunnel sessions; automatic Xray log/StatsService collection and per-core session termination are still missing.
- [ ] Unique, high-entropy subscription URLs with revocation, expiry, access controls and access logs.
- [ ] Subscription output in supported client formats, with format-specific tests.
- [ ] Backup/restore for configuration and metadata, including integrity checks and documented secret handling.

## Phase 6 — multi-device sync and release hardening
- [ ] Authenticated sync across devices with conflict handling and explicit secret protection.
- [x] Add initial API tests for password hashing, config owner isolation, subscription revocation and admin limit metadata.
- [ ] Add integration tests for migrations, actual node quota enforcement and restore.
- [ ] Dependency/security scanning, secret scanning, CI and signed release artifacts.
- [ ] Verify TLS, restore procedures, migration strategy, log redaction and a deployment runbook.

## Acceptance criteria for a Config Hub
- A profile can be created manually, imported, validated, edited, saved, searched, tagged, exported and represented as a QR code.
- Every advertised client export is tested against that client's documented format; unsupported features are reported rather than silently dropped.
- Subscription limits and expiry are enforced server-side, not only in the Android UI.
- Node operations require authentication and authorization, have audit trails, and do not silently overwrite remote configuration.
- Connectivity status is based on a real test/runtime, not inferred from syntactic validity.

## Current verified limitations
- The Android app has an optional Cloudflare sync card; the Worker serves `/panel` with basic config controls and account usage summary. The FastAPI/Docker backend is a local-development alternative and uses SQLite in one instance.
- Country choice currently records the requested country; it does not find or guarantee a clean IP. Actual IP selection requires a curated candidate pool or authorized nodes plus DNS/TLS/connectivity checks.
- Adult-domain filtering is added to the VLESS+REALITY Xray JSON export only when selected and depends on an Xray-compatible geosite data file. It is not a device-wide content filter and is not enforced by Cloudflare profile metadata alone.
- Quota/usage is currently based on trusted admin usage reports; no node agent exists to independently measure or enforce tunnel traffic.
- Subscription response is a normalized JSON profile list, not yet a tested v2rayNG/Hiddify/Clash/sing-box/NPV-specific subscription encoding. The Cloudflare panel has initial Hysteria2/TUIC form and share-link support, but it is not yet compatibility-tested against those clients.
- Before public deployment, configure strong APP_SECRET and bootstrap token, HTTPS, request rate limits, audit logging, and production-grade database migrations.
- This is still an Android-first prototype plus optional cloud API scaffolds, not a production-ready Config Hub.
- [x] Document engine/runtime gaps and acceptance gates in `docs/CORE-STATUS.md`.
- No Xray-core runtime is currently bundled or running; generated Xray JSON is a preview/export.
- The generated Fragment Freedom outbound is separate; automatic routing/chaining to a selected VLESS connection is not implemented.
- Network-labelled Fragment presets are editable heuristics, not measured ISP profiles and do not guarantee connectivity or bypass.
- Local credentials are encrypted at rest using Android Keystore AES-GCM; other SQLite metadata is not encrypted.
- Remote nodes, real traffic enforcement, live scanner/checker, production admin workflows and conflict-safe multi-device sync remain incomplete. The Cloudflare panel is a starter UI, not a full Blue-Knight-Panel replacement.


## Backhaul and Telegram Cloudflare scope (initial)
- Backhaul TOML generation is local-only and emits distinct server/client templates. The first version accepts numeric forwarding ports only; advanced port mappings and runtime install/health tests are not implemented.
- The upstream `Musixal/Backhaul` README lists tcp, tcpmux, ws, wss, wsmux and wssmux. `uwsmux` is deliberately rejected until an exact compatible implementation/version is identified.
- Cloudflare-proxied WebSocket requires a supported port, a proxied DNS record, a compatible origin and matching transport configuration. A generated TOML file does not prove that the CDN path works; verify current Cloudflare port/transport rules and run a real connection test.
- TWP-CF is linked as an external deployment helper. The Android app does not ask for Cloudflare API tokens; the upstream repository has no SPDX license metadata in the repository response, so its source is not copied into Spd con.
