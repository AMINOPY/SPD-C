# Android VPN runtime (Spd con)

This document describes the Android device-wide tunnel path added to the app.

## Runtime path

1. The user selects **Connect through Android VPN** for a saved profile.
2. Android's VpnService.prepare() consent flow is shown when required.
3. SpdConVpnService creates an Android TUN interface with IPv4 and IPv6 default routes.
4. The service converts the selected share link to an Xray outbound using the pinned official libXray build. Legacy VMess QR links are normalized into an Xray VMess outbound because libXray's share-link parser accepts VMessAEAD, not the old Base64(JSON) QR format.
5. The service registers libXray's dialer/listener socket-protection callbacks **before** starting Xray, so the upstream connection is not routed back into its own TUN.
6. The TUN file descriptor is passed to Xray-core as env.xray.tun.fd. The routing rule sends traffic from the TUN inbound to the selected proxy outbound.
7. The service probes https://cp.cloudflare.com/ after startup and reports either connected or running-unverified. The latter means the core started but an HTTPS request through the active Android route did not succeed.
8. Disconnect stops Xray, resets the process-wide libXray DNS resolver, unregisters active socket callbacks, and closes the TUN descriptor.

## What the status means

- **connected**: Xray accepted the config and the app's HTTPS probe received an HTTP response through the active VPN route. This does not prove every app, destination, UDP service, or IPv6 path works.
- **running-unverified**: Xray started, but the HTTPS probe failed. Check the server's real parameters, reachability, DNS, transport support, and server logs.
- **error**: startup failed. The stored status detail contains the failure message.

## Compatibility and limits

- TUIC is intentionally disabled for this runtime because the pinned Xray-core does not implement TUIC.
- The runtime supports profiles that the pinned Xray-core can parse and whose server-side parameters are correct. VLESS/REALITY, VLESS or Trojan over TLS/WebSocket, gRPC, XHTTP, VMess, Shadowsocks and Hysteria2 are not interchangeable: the server must implement the exact same protocol, credentials, transport, SNI, path, public key and short ID.
- A generated share link is not a working server. A client cannot invent a valid UUID/password, REALITY public key, short ID, TLS certificate, WebSocket path, or server-side listener.
- Device DNS packets are redirected to Xray's DNS outbound and resolved through HTTPS DNS endpoints whose requests are routed over the selected proxy. libXray's own resolver for the proxy server hostname uses a protected UDP resolver outside the TUN to avoid a DNS loop; that bootstrap lookup may still fail on networks that block public DNS. The HTTPS probe is not a full UDP/IPv6 test suite.
- The service currently uses a full-device route, not per-app split tunneling. It does not claim to defeat every ISP block or guarantee circumvention in every network. A reachable, correctly configured server and a compatible transport are required.
- The native service has been added to source, but a green Android CI run and on-device end-to-end verification are required before treating this feature as production-ready.

## Manual verification checklist

1. Install the APK built from the branch and select a known-good server configuration.
2. Tap **Connect through Android VPN** and approve Android's system VPN consent dialog.
3. In Library, check that the status becomes connected; if it says running-unverified, inspect the displayed detail and compare the server config with the profile.
4. Open a browser and test both a domain and an IP-literal HTTPS endpoint. Test UDP-dependent apps separately.
5. Confirm the VPN key/system VPN indicator appears. Disconnect from the Library button or the persistent notification action.
6. Repeat with IPv4-only and IPv6-capable destinations; Android routing being configured does not guarantee the remote node supports both.
