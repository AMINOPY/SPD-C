#!/usr/bin/env python3
"""Validate and safely edit one existing Xray VLESS inbound's client list.

This helper edits a config file only. It does not restart/reload a running Xray
process; operators must use their service manager after reviewing the change.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path
from typing import Any, Sequence


MAX_CONFIG_BYTES = 1_000_000
EMAIL_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._@+-]{0,127}$")


def load_config(path: str | Path) -> dict[str, Any]:
    target = Path(path)
    if target.is_symlink() or not target.is_file():
        raise ValueError("Config must be an existing regular file, not a symlink")
    if target.stat().st_size > MAX_CONFIG_BYTES:
        raise ValueError("Config file exceeds 1 MB limit")
    try:
        value = json.loads(target.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError("Config is unreadable or is not valid JSON") from exc
    if not isinstance(value, dict) or not isinstance(value.get("inbounds"), list):
        raise ValueError("Config must contain an inbounds array")
    return value


def normalize_client_id(value: str) -> str:
    try:
        return str(uuid.UUID(value))
    except (ValueError, AttributeError, TypeError) as exc:
        raise ValueError("client ID must be a valid UUID") from exc


def mutate_vless_client(config: dict[str, Any], inbound_tag: str, client_id: str,
                        email: str, action: str) -> tuple[dict[str, Any], bool]:
    if not inbound_tag or len(inbound_tag) > 128 or any(ord(c) < 32 for c in inbound_tag):
        raise ValueError("inbound tag is required and must be printable")
    client_id = normalize_client_id(client_id)
    if not EMAIL_RE.fullmatch(email):
        raise ValueError("email must be 1..128 safe ASCII characters")
    if action not in {"add", "remove"}:
        raise ValueError("action must be add or remove")

    matches = [item for item in config["inbounds"]
               if isinstance(item, dict) and item.get("tag") == inbound_tag]
    if len(matches) != 1:
        raise ValueError("Exactly one inbound with the requested tag must exist")
    inbound = matches[0]
    if str(inbound.get("protocol", "")).lower() != "vless":
        raise ValueError("Target inbound protocol must be VLESS")
    settings = inbound.get("settings")
    if not isinstance(settings, dict) or not isinstance(settings.get("clients"), list):
        raise ValueError("Target VLESS inbound must have a settings.clients array")
    clients = settings["clients"]
    if any(not isinstance(client, dict) for client in clients):
        raise ValueError("Existing client entries must all be JSON objects")

    id_matches = [client for client in clients if str(client.get("id", "")).lower() == client_id]
    email_matches = [client for client in clients if str(client.get("email", "")).casefold() == email.casefold()]
    if action == "add":
        if id_matches or email_matches:
            if len(id_matches) == 1 and len(email_matches) == 1 and id_matches[0] is email_matches[0]:
                return config, False
            raise ValueError("Client UUID or email already belongs to another entry")
        clients.append({"id": client_id, "email": email, "level": 0, "flow": ""})
        return config, True

    target = id_matches[0] if id_matches else None
    if target is None:
        raise ValueError("Client UUID was not found on the target inbound")
    if str(target.get("email", "")).casefold() != email.casefold():
        raise ValueError("Client email does not match the UUID; refusing removal")
    clients.remove(target)
    return config, True


def validate_candidate(xray_bin: str, config_path: str, timeout: int = 20) -> tuple[bool, str]:
    try:
        result = subprocess.run(
            [xray_bin, "run", "-test", "-config", config_path],
            capture_output=True, text=True, timeout=timeout, check=False,
        )
    except FileNotFoundError:
        return False, "Xray binary not found"
    except subprocess.TimeoutExpired:
        return False, "Xray validation timed out"
    if result.returncode != 0:
        return False, "Xray rejected the candidate config; inspect restricted local logs"
    return True, "Xray accepted the candidate config"


def change_client(config_path: str, inbound_tag: str, client_id: str, email: str,
                  action: str, xray_bin: str = "xray", apply: bool = False) -> dict[str, Any]:
    target = Path(config_path)
    original = load_config(target)
    changed, did_change = mutate_vless_client(original, inbound_tag, client_id, email, action)
    if not did_change:
        return {"ok": True, "changed": False, "applied": False,
                "message": "Client already provisioned exactly as requested"}
    rendered = (json.dumps(changed, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if len(rendered) > MAX_CONFIG_BYTES:
        raise ValueError("Resulting config exceeds 1 MB limit")

    temp_name = None
    try:
        fd, temp_name = tempfile.mkstemp(prefix=".spdcon-xray-", suffix=".json", dir=str(target.parent))
        try:
            os.fchmod(fd, stat.S_IMODE(target.stat().st_mode) & 0o600 or 0o600)
            with os.fdopen(fd, "wb") as temp_file:
                temp_file.write(rendered)
                temp_file.flush()
                os.fsync(temp_file.fileno())
        except Exception:
            try:
                os.close(fd)
            except OSError:
                pass
            raise
        valid, message = validate_candidate(xray_bin, temp_name)
        if not valid:
            return {"ok": False, "changed": True, "applied": False, "message": message}
        if not apply:
            return {"ok": True, "changed": True, "applied": False,
                    "message": "Candidate validates; rerun with --apply to atomically replace the config"}
        if target.is_symlink() or not target.is_file():
            raise ValueError("Config path changed during validation; refusing to replace it")
        os.chmod(temp_name, stat.S_IMODE(target.stat().st_mode) & 0o600 or 0o600)
        os.replace(temp_name, target)
        temp_name = None
        directory_fd = os.open(str(target.parent), os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
        return {"ok": True, "changed": True, "applied": True,
                "message": "Validated config atomically replaced; running Xray was not restarted"}
    finally:
        if temp_name:
            try:
                os.unlink(temp_name)
            except FileNotFoundError:
                pass


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("add", "remove"))
    parser.add_argument("--config", required=True)
    parser.add_argument("--inbound-tag", required=True)
    parser.add_argument("--client-id", required=True)
    parser.add_argument("--email", required=True)
    parser.add_argument("--xray-bin", default=os.getenv("SPD_CON_XRAY_BIN", "xray"))
    parser.add_argument("--apply", action="store_true",
                        help="atomically replace the config after Xray validation; does not restart Xray")
    args = parser.parse_args(argv)
    try:
        result = change_client(args.config, args.inbound_tag, args.client_id,
                               args.email, args.action, args.xray_bin, args.apply)
    except (OSError, ValueError) as exc:
        print(json.dumps({"ok": False, "message": str(exc)}))
        return 2
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
