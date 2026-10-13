#!/usr/bin/env python3
"""Poll local Xray StatsService and report cumulative per-user counters to Spd con.

Requires a trusted local identity map and a loopback-only Xray API. This accounts
traffic; it does not disconnect sessions or enforce quotas in the core.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Any

from agent import load_identity_map
from xray_runtime import query_stats


def parse_user_counters(stats: dict[str, int]) -> dict[str, dict[str, int]]:
    """Convert Xray user>>>email>>>traffic>>>{uplink,downlink} stats to per-email totals."""
    users: dict[str, dict[str, int]] = {}
    for name, value in stats.items():
        parts = name.split(">>>")
        if len(parts) != 4 or parts[0] != "user" or parts[2] != "traffic":
            continue
        email, direction = parts[1].strip().casefold(), parts[3]
        if not email or direction not in {"uplink", "downlink"} or value < 0:
            continue
        user = users.setdefault(email, {"upload_bytes": 0, "download_bytes": 0})
        key = "upload_bytes" if direction == "uplink" else "download_bytes"
        user[key] = value
    return users


def post_usage(api_base: str, node_id: str, node_secret: str, user_id: str,
               counters: dict[str, int], timeout: float = 8.0) -> dict[str, Any]:
    base = api_base.rstrip("/")
    parsed = urllib.parse.urlparse(base)
    if parsed.scheme != "https" and parsed.hostname not in {"localhost", "127.0.0.1", "::1"}:
        raise ValueError("API_BASE_URL must use HTTPS except for local development")
    if not parsed.scheme or not parsed.netloc:
        raise ValueError("API_BASE_URL must be an absolute URL")
    payload = {
        "event_id": str(uuid.uuid4()),
        "event_timestamp": int(time.time()),
        "user_id": user_id,
        "upload_bytes": counters["upload_bytes"],
        "download_bytes": counters["download_bytes"],
    }
    request = urllib.request.Request(
        f"{base}/nodes/{urllib.parse.quote(node_id, safe='')}/usage/report",
        data=json.dumps(payload, separators=(",", ":")).encode("utf-8"),
        method="POST",
        headers={"Authorization": f"Bearer {node_secret}",
                 "Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read(2048).decode("utf-8", errors="replace")
        raise RuntimeError(f"API returned HTTP {exc.code}: {body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Could not reach Spd con API: {exc.reason}") from exc


def sync_once(args: argparse.Namespace, identity_map: dict[str, str]) -> int:
    ok, result = query_stats(args.xray_bin, args.server, "user>>>", args.timeout)
    if not ok:
        print(f"Xray StatsService query failed: {result}", file=sys.stderr)
        return 1
    users = parse_user_counters(result)
    failures = 0
    for email, counters in users.items():
        user_id = identity_map.get(email)
        if user_id is None:
            print(f"Skipping unmapped Xray identity: {email}", file=sys.stderr)
            failures += 1
            continue
        try:
            response = post_usage(args.api_base, args.node_id, args.node_secret, user_id, counters,
                                  timeout=args.timeout)
            print(json.dumps({"core_email": email, "accepted": response.get("accepted"),
                              "usage_delta_bytes": response.get("usage_delta_bytes"),
                              "quota_exhausted": response.get("quota_exhausted"),
                              "enforcement": response.get("enforcement")}, separators=(",", ":")),
                  flush=True)
        except (ValueError, RuntimeError) as exc:
            print(f"Usage sync failed for mapped core identity {email}: {exc}", file=sys.stderr)
            failures += 1
    if not users:
        print("No per-user Xray traffic counters returned; check StatsService and user traffic stats.",
              file=sys.stderr)
        return 1
    return 1 if failures else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--xray-bin", default=os.getenv("SPD_CON_XRAY_BIN", "xray"))
    parser.add_argument("--server", default=os.getenv("SPD_CON_XRAY_API", "127.0.0.1:10085"))
    parser.add_argument("--identity-map", default=os.getenv("SPD_CON_IDENTITY_MAP_PATH", ""))
    parser.add_argument("--api-base", default=os.getenv("SPD_CON_API_BASE_URL", ""))
    parser.add_argument("--node-id", default=os.getenv("SPD_CON_NODE_ID", ""))
    parser.add_argument("--node-secret", default=os.getenv("SPD_CON_NODE_SECRET", ""))
    parser.add_argument("--interval", type=int, default=60)
    parser.add_argument("--timeout", type=int, default=8)
    parser.add_argument("--once", action="store_true")
    args = parser.parse_args(argv)

    if not all((args.identity_map, args.api_base, args.node_id, args.node_secret)):
        parser.error("identity map, API base URL, node ID and node secret are required")
    if args.interval < 10 or args.timeout < 1 or args.timeout > 60:
        parser.error("interval must be >= 10 seconds and timeout must be 1..60 seconds")
    try:
        identity_map = load_identity_map(args.identity_map)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Could not load identity map: {exc}", file=sys.stderr)
        return 2

    status = sync_once(args, identity_map)
    if args.once:
        return status
    while True:
        time.sleep(args.interval)
        status = sync_once(args, identity_map)
    # The loop is intentionally long-running; systemd should supervise restarts.


if __name__ == "__main__":
    raise SystemExit(main())
