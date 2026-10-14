# Shared configuration schemas

This directory will hold protocol-neutral schema documentation and sanitized test fixtures shared by the Android app and backend.

Planned fixture groups:
- valid and invalid VLESS links
- valid and invalid VMess JSON/base64 payloads
- Trojan and Shadowsocks links
- transport/security combinations supported by the pinned Xray version

Do not place real subscription URLs, private keys, UUIDs, passwords, private-node IPs, or user data in fixtures. Tests should use synthetic values.
