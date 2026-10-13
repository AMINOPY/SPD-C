#!/usr/bin/env python3
"""Safe, read-only Xray-core validation and local StatsService query helper.

This tool does not start/restart Xray, edit its config, expose the management API,
or enforce account policies. Keep the Xray API bound to loopback.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Sequence


MAX_CONFIG_BYTES = 1_000_000
STAT_LINE = re.compile(r"^\s*name:\s*(.*?)\s+value:\s*(-?\d+)\s*$")


def validate_config(xray_bin: str, config_path: str, timeout: int = 20) -> tuple[bool, str]:
    path = Path(config_path)
    if not path.is_file():
        return False, "Config file does not exist or is not a regular file"
    if path.stat().st_size > MAX_CONFIG_BYTES:
        return False, "Config file exceeds 1 MB validation limit"
    try:
        result = subprocess.run(
            [xray_bin, "run", "-test", "-config", str(path)],
            capture_output=True, text=True, timeout=timeout, check=False,
        )
    except FileNotFoundError:
        return False, "Xray binary not found; set SPD_CON_XRAY_BIN or pass --xray-bin"
    except subprocess.TimeoutExpired:
        return False, "Xray config validation timed out"
    if result.returncode != 0:
        # Do not echo raw engine logs: they may include private endpoints or config values.
        return False, "Xray rejected the configuration; inspect the node's restricted local logs"
    return True, "Xray accepted the configuration"


def parse_stats_output(output: str) -> dict[str, int]:
    stats: dict[str, int] = {}
    for line in output.splitlines():
        match = STAT_LINE.match(line)
        if not match:
            continue
        name, value = match.groups()
        if name:
            stats[name] = int(value)
    return stats


def query_stats(
    xray_bin: str,
    server: str = "127.0.0.1:10085",
    pattern: str = "user>>>",
    timeout: int = 8,
) -> tuple[bool, dict[str, int] | str]:
    host = server.rsplit(":", 1)[0].strip("[]").lower()
    if host not in {"localhost", "127.0.0.1", "::1"}:
        return False, "Xray API server must be loopback-only"
    if not pattern or len(pattern) > 256 or any(ord(ch) < 32 for ch in pattern):
        return False, "Invalid stats pattern"
    try:
        result = subprocess.run(
            [xray_bin, "api", "statsquery", f"--server={server}", "-pattern", pattern],
            capture_output=True, text=True, timeout=timeout, check=False,
        )
    except FileNotFoundError:
        return False, "Xray binary not found; set SPD_CON_XRAY_BIN or pass --xray-bin"
    except subprocess.TimeoutExpired:
        return False, "Xray StatsService query timed out"
    if result.returncode != 0:
        return False, "Xray StatsService query failed; check local API configuration"
    return True, parse_stats_output(result.stdout)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--xray-bin", default=os.getenv("SPD_CON_XRAY_BIN", "xray"))
    commands = parser.add_subparsers(dest="command", required=True)
    validate = commands.add_parser("validate", help="validate a config without starting Xray")
    validate.add_argument("--config", required=True)
    stats = commands.add_parser("stats", help="query user stats from a loopback Xray API")
    stats.add_argument("--server", default="127.0.0.1:10085")
    stats.add_argument("--pattern", default="user>>>")
    args = parser.parse_args(argv)

    if args.command == "validate":
        ok, message = validate_config(args.xray_bin, args.config)
        print(json.dumps({"ok": ok, "message": message}))
        return 0 if ok else 1
    ok, result = query_stats(args.xray_bin, args.server, args.pattern)
    print(json.dumps({"ok": ok, "stats": result if ok else None,
                      "message": None if ok else result}, ensure_ascii=False))
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
