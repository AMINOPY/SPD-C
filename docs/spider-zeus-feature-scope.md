# Spd con — SpiderPanel / Zeus-inspired product scope

This document translates the requested product direction into an implementation plan for **Spd con**, without copying upstream source or branding.

Reference projects:
- SpiderPanel: https://github.com/amirh00sain/SpiderPanel
- Z-E-U-S: https://github.com/panel-zeus/Z-E-U-S

## Product shape

Spd con remains an **Android-first app**. The common tasks should work in the app with minimal typing; an optional Cloudflare panel is for remote administration, subscriptions and user/node operations, not a requirement for everyday local profile creation.

### 1. Simple profile builder

Default flow: **profile name → goal → network preset → generate → QR / copy / share**.

- Generate client-side IDs and safe defaults automatically.
- Keep protocol-specific fields in an expandable Advanced section.
- REALITY requires actual server-provided public key and short ID; entering an SNI alone must never fabricate a valid REALITY configuration.
- A generated link/profile is not proof that a remote server has provisioned the user or that the tunnel works.
- Saved profiles expose a real Xray-core test: native link conversion, temporary core construction (`testXray`), and an outbound HTTPS HEAD probe (`pingBatch`). The probe does not create an Android TUN or route all device traffic.
- Apply a selected scanner result only to a compatible host/address field, preserving the actual SNI and other TLS identity settings.

### 2. Scanner and network diagnostics

Inspired by SpiderPanel's scanner workflow and Zeus's integrated scanner, but implemented against Spd con's own models:

- Candidate sources: built-in sample, user-pasted IP/CIDR/range, imported text, recent results; later add maintained provider lists and bounded country allocation sampling.
- Bounded concurrency, supported TLS ports, timeouts, visible errors and measured handshake latency.
- Separate **TCP reachable**, **TLS hostname verified**, and **end-to-end profile tested**. A TLS handshake alone is not proof of a working proxy or a permanently clean IP.
- Add pause/cancel, progress checkpoints, persistent scan history, CSV/TXT export and re-scan.
- Country mode must use a documented allocation source and show the sample count before scanning. IP geolocation cannot be inferred from a country label or a Cloudflare anycast address.
- Reject private, loopback, link-local, multicast and other reserved destinations by default. Never perform broad port sweeps; keep explicit per-run limits.

### 3. Account, subscription and node management

The optional server side should grow in phases:

1. Secure administrator/user sessions, role checks, rate limits, audit trail and backup/restore.
2. User lifecycle: create, enable/disable, expiry, quota metadata, device/IP policy and bulk actions.
3. Subscription links, QR delivery, revocation and status page.
4. Node enrollment with per-node credentials, heartbeat/health, config sync and authenticated usage telemetry.
5. Actual Xray-core integration and integration tests before claiming connection, traffic enforcement, or live quota enforcement.
6. Cloudflare D1/Workers dashboard: deployment status, request usage, health and backup visibility, with secrets stored only as protected server-side secrets.

Traffic quotas must be enforced by a trusted node/runtime counter, not by client-submitted totals alone. Until that integration exists, label values as reported usage and do not promise hard enforcement.

### 4. Network presets and optional controls

- One-tap general, mobile, weak-network, gaming and Cloudflare-WebSocket presets.
- Network detection is a hint; let the user override it.
- Fragment and TLS options remain advanced, editable heuristics, never advertised as guaranteed bypasses.
- Optional adult-content blocking must be implemented by an explicit, testable DNS/routing policy in the actual runtime. A JSON preview or share-link flag alone does not enforce filtering.
- Automatic IP rotation/fallback requires a runtime that can observe failed connections and safely replace an endpoint; rotating a stored address alone is not a working fallback system.

## Current implementation snapshot

Already present on the development branch:
- Android profile library, common link parsing/serialization, QR/copy/share, REALITY fields and guided config recommendations.
- A bounded IPv4/TLS scanner with built-in, custom IP/CIDR/range and recent candidates, supported TLS ports, SNI validation, and a maximum of 256 candidates.
- A new DNS resolver quick scan inspired by SlipNet: curated public resolver candidates, custom IP/CIDR input, UDP/TCP A/TXT/random-subdomain probes, selectable UDP, TCP, or UDP-first/TCP-fallback transport, a 0–3 compatibility score, latency sorting, and remembered successful candidates. This is a DNS compatibility probe only—not SlipNet's full DNS scanner.
- Official XTLS/libXray native Android AAR pinned to release `v26.10.10`, downloaded during Gradle build and checked against a SHA-256 digest; saved profiles can be tested with the real core and an outbound network probe.
- Optional FastAPI and Cloudflare Worker foundations for accounts/configs/subscriptions.

Still incomplete and must not be presented as finished:
- Country-based candidate sourcing, IPv6 scanner support, pausing/resuming scans, durable scan history/export, and speed tests. The current DNS probe does not implement SlipNet's full Simple/Advanced/E2E/Prism modes or its signed server-verification protocol.
- Device-wide VPN operation via Android `VpnService`/TUN, automatic route and DNS handling, and real device-traffic verification. Xray outbound ping is not a substitute for a live full-device tunnel.
- Android-to-cloud account/config sync and production-grade Cloudflare deployment management.
- Full-device Android tunnel operation is not yet implemented. The native Xray core is bundled for config construction and outbound connectivity tests, but `VpnService`/TUN routing, runtime lifecycle UI, and device traffic verification remain unfinished. Trusted server-side quota enforcement and tested automatic endpoint failover also remain unfinished.
- Complete node lifecycle, production audit/rate-limit/security review, and end-to-end subscription testing.

## Licensing and implementation rule

SpiderPanel and Z-E-U-S are references for feature discovery and interaction design, not code to copy blindly. In particular, the Z-E-U-S README states restrictive terms against derivative works and redistribution. Do not copy, port, or adapt its source without explicit permission. Build independent implementations and review each upstream project's current license and notice requirements before using any code or assets.
