# Configuration format support

## Implemented in the current branch

- Typed configuration model and basic required-field validation.
- VLESS and Trojan URI parsing; common VMess Base64-JSON parsing; initial Shadowsocks URI parsing.
- Share-link serializers for VLESS, Trojan, VMess and the supported Shadowsocks URI form.
- Bounded input size for imported links.
- Fragment settings with range validation and editable starting presets labelled همراه اول, ایرانسل, and نت ملی / اتصال محدود.
- REALITY client fields for serverName/SNI, public key (pbk), shortId, fingerprint, spiderX and optional Vision flow validation.
- Xray JSON outbound generator for VLESS + REALITY, with an optional documented Freedom outbound containing Fragment settings.
- Android UI controls to inspect links, edit the Fragment values and REALITY fields, and preview generated output.

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
- QR generation, clipboard integration, import UI polish, Room persistence, and live connectivity testing.
- Carrier-specific diagnostics based on real tests; the current presets are not measured network profiles.
- End-to-end validation against a pinned Xray-core version.

A successful parse or generated JSON does not prove that a configuration will connect. Keep Xray versions pinned and validate generated output against that version. Imported values are untrusted; do not log credentials, public/private keys, or subscription tokens.
