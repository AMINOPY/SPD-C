#!/usr/bin/env python3
"""Spd con node telemetry bridge.

Consumes normalized JSON Lines events from a trusted runtime adapter and sends
them to the Spd con API. It does not sniff traffic or infer a user's identity.
"""
from __future__ import annotations

import ipaddress
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Any


def normalize_event(event: dict[str, Any], node_id: str) -> dict[str, Any]:
    user_id = str(event.get("user_id", "")).strip()
    connection_id = str(event.get("connection_id", "")).strip()
    if not user_id or len(user_id) > 128:
        raise ValueError("user_id is required (map the runtime identity to a Spd con user id)")
    if len(connection_id) < 8 or len(connection_id) > 128:
        raise ValueError("connection_id must contain 8..128 characters")
    try:
        client_ip = str(ipaddress.ip_address(str(event.get("client_ip", "")).strip()))
    except ValueError as exc:
        raise ValueError("client_ip must be a valid IPv4 or IPv6 address") from exc
    state = str(event.get("state", "active"))
    if state not in {"active", "disconnected"}:
        raise ValueError("state must be active or disconnected")

    def counter(name: str) -> int:
        value = event.get(name, 0)
        if isinstance(value, bool) or not isinstance(value, int) or value < 0 or value > 10**15:
            raise ValueError(f"{name} must be an integer between 0 and 10^15")
        return value

    label = event.get("client_label")
    if label is not None:
        label = str(label)[:128]
    return {
        "event_id": str(uuid.uuid4()),
        "event_timestamp": int(time.time()),
        "user_id": user_id,
        "connection_id": connection_id,
        "node_id": node_id,
        "client_ip": client_ip,
        "client_label": label,
        "state": state,
        "upload_bytes": counter("upload_bytes"),
        "download_bytes": counter("download_bytes"),
    }


def post_event(api_base: str, node_id: str, node_secret: str,
               event: dict[str, Any], timeout: float = 8.0) -> dict[str, Any]:
    base = api_base.rstrip("/")
    parsed = urllib.parse.urlparse(base)
    if parsed.scheme != "https" and parsed.hostname not in {"localhost", "127.0.0.1", "::1"}:
        raise ValueError("API_BASE_URL must use HTTPS except for local development")
    if not parsed.scheme or not parsed.netloc:
        raise ValueError("API_BASE_URL must be an absolute URL")
    payload = json.dumps(event, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    url = f"{base}/nodes/{urllib.parse.quote(node_id, safe='')}/connections/report"
    request = urllib.request.Request(url, data=payload, method="POST", headers={
        "Authorization": f"Bearer {node_secret}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    })
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read(2048).decode("utf-8", errors="replace")
        raise RuntimeError(f"API returned HTTP {exc.code}: {body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Could not reach Spd con API: {exc.reason}") from exc


def main() -> int:
    api_base = os.environ.get("SPD_CON_API_BASE_URL", "").strip()
    node_id = os.environ.get("SPD_CON_NODE_ID", "").strip()
    node_secret = os.environ.get("SPD_CON_NODE_SECRET", "").strip()
    if not api_base or not node_id or not node_secret:
        print("Set SPD_CON_API_BASE_URL, SPD_CON_NODE_ID and SPD_CON_NODE_SECRET.", file=sys.stderr)
        return 2

    for line_number, line in enumerate(sys.stdin, start=1):
        if not line.strip():
            continue
        try:
            source = json.loads(line)
            if not isinstance(source, dict):
                raise ValueError("each JSONL line must be an object")
            event = normalize_event(source, node_id)
            result = post_event(api_base, node_id, node_secret, event)
            print(json.dumps({"line": line_number, "accepted": result.get("accepted"),
                              "alerts": result.get("alerts", []),
                              "enforcement": result.get("enforcement")}, separators=(",", ":")),
                  flush=True)
        except (ValueError, json.JSONDecodeError, RuntimeError) as exc:
            print(f"line {line_number}: {exc}", file=sys.stderr, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
