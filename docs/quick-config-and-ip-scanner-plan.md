# Quick Config + Clean IP Scanner — implementation plan

Target branch: `feat/spd-con-foundation`

## Product goal

The default workflow should be: **name the profile → choose a few guided options → Generate**. Advanced protocol fields remain available under an optional “Advanced” section and should not be required for common cases.

## 1. Guided configuration flow

1. **Name** — profile name (required; suggest a name automatically if blank).
2. **Purpose** — “Everyday”, “Gaming”, “Weak network”, “Mobile data”, or “Custom”.
3. **Network / country** — choose a saved preset or “Auto-detect”; clearly label detection as a hint and allow manual override.
4. **Protocol** — recommend compatible options from the chosen goal and server capabilities; never fabricate server credentials or claim a profile is live-tested when it is only generated.
5. **Optional IP optimization** — “Find fastest compatible IP” opens the scanner and applies a selected result to a compatible profile.
6. **Generate** — validate required fields, produce the supported profile/export, save it locally, and offer Copy / QR / Share.

Use sensible defaults, short descriptions, inline validation, a visible generation status, and a final summary. Keep UUIDs and complex protocol fields hidden unless the selected protocol actually needs them. Do not imply a generated client config creates/provisions a server-side user automatically.

## 2. Clean IP Scanner screen

Add a dedicated **IP Scanner** entry with a simple mode first and an expandable advanced mode.

### Sources

- **Recommended** — small, maintained candidate set.
- **Country** — select a country and bounded sample size.
- **Import** — text file, one IP/CIDR per line or comma-separated.
- **Custom** — single IP, CIDR, or start-end range.
- **Recent scan / Passed IPs** — rerun known candidates without rebuilding the list.

### Scan modes

- **TCP / TLS reachability** — checks only the selected host/port and reports latency; does not call an IP “clean” based on reachability alone.
- **Profile compatibility** — where a user-supplied, supported Xray config is available, test the candidate through the actual configured protocol and endpoint.
- Keep concurrency bounded, provide pause/cancel, persist checkpoints and results, and avoid unbounded CIDR expansion. Set per-run candidate and time limits, reject private/reserved/multicast IP ranges by default, and require explicit confirmation before scanning unusually large ranges.

### Results

Show IP, port, latency, packet loss, test mode, compatibility result, timestamp, and optional measured throughput. Sort by the user's selected objective (compatibility first, then latency / loss / throughput). Include “Use for profile”, “Copy”, “Export TXT/CSV”, and “Rescan”. A result must retain its test context and timestamp; avoid permanent “clean IP” claims because routes and CDN edges change.

### Suggested states

Idle → Validating source → Scanning (progress + cancel) → Results / Partial results / Error. Resume an interrupted scan when its checkpoint is valid.

## 3. SlipNet-inspired IP discovery

Use the public SlipNet project as a **behavioral reference**, not as code to copy blindly. Its useful IP scanner patterns to evaluate and implement independently are:

- distinct candidate sources (curated, import, country allocation, custom range, recent results);
- bounded country-based sampling and previewing the number of candidates before scan;
- a simple one-tap mode and deeper server/profile-verified mode;
- separating “reachable/working” candidates from end-to-end profile-tested candidates;
- replaying previous candidates and reusing only previously passed results;
- optional neighbor probing for a hit, with strict caps and opt-in;
- visible transport/test badges, resumable progress, and applying selected results to a profile.

Do **not** copy SlipNet source into this repository without reviewing its current source-available license and redistribution restrictions. Implement the behavior against Spd con's own data models and tests.

## 4. Clean-IP-Scanner-inspired checks

Evaluate the upstream Clean-IP-Scanner project for compatible ideas: candidate IP/CIDR input, IPv4/IPv6 support, configurable port, bounded workers, resumable scans, and real measurements rather than latency-only ranking. Its Xray mode is only meaningful when the supplied config matches the CDN/provider being tested. Any adapted code must retain the required license notices.

## 5. Architecture boundaries

- Android UI owns guided setup, progress, local history, selection, and profile application.
- A scanner engine exposes cancellable, bounded jobs and typed results; UI must not run long scans on the main thread.
- Persist scan settings/checkpoints/results locally; never persist private credentials in plaintext.
- Keep optional cloud/backend integration separate from the offline-first core.
- Never scan targets outside the user's authorized scope; apply candidate-count and timeout limits.
- Scanner results are observations, not guarantees of censorship bypass, anonymity, or future connectivity.

## 6. Acceptance criteria

- A new user can create a basic supported profile by entering a name and choosing presets without manually hunting for protocol fields.
- Advanced settings remain accessible and are validated.
- Scanner offers at least curated/import/custom sources, bounded progress, cancellation, history, and export.
- Country mode previews the sample size and never attempts to enumerate an entire national allocation.
- “Reachable” and “profile-compatible” are distinct statuses.
- A selected result can be applied only to a compatible profile field and is revalidated before saving.
- Unit tests cover CIDR bounds, invalid/reserved addresses, cancellation/checkpoint recovery, result ordering, and config generation.
- README documents actual implemented status; no feature is described as complete until CI/build and integration tests pass.

## Upstream references

- Clean-IP-Scanner: https://github.com/4n0nymou3/Clean-IP-Scanner
- SlipNet: https://github.com/anonvector/SlipNet
- SlipNet user guide (scanner source/modes): https://github.com/anonvector/SlipNet/blob/main/docs/USER_GUIDE.md
