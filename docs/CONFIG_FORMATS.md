# Configuration format support

The first config-engine increment introduces a typed `ConfigProfile` model, field validation, and a parser for common share-link formats.

## Implemented in the current branch

- VLESS URI parsing: scheme, credential, host, port, query parameters, and fragment remark.
- Trojan URI parsing using the same URI structure.
- VMess legacy Base64-encoded JSON parsing for common fields (`add`, `port`, `id`, `ps`, `net`, `tls`).
- Bounded input size and basic required-field validation.

## Not yet implemented

- Shadowsocks variants.
- Full round-trip serializers for every protocol.
- Protocol-specific transport/security validation against a pinned Xray version.
- QR generation, clipboard, import UI, and Room persistence.
- A complete VMess JSON schema and all vendor-specific extensions.

Never treat successful parsing as proof that a configuration will connect. Syntax validation and network connectivity checks are separate features. Imported values are untrusted; do not log credentials, UUIDs, or subscription tokens.
