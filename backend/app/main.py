"""Spd con API: development-grade account, config and subscription service.

Set a long random APP_SECRET and BOOTSTRAP_ADMIN_TOKEN before deployment.
Use HTTPS outside localhost. SQLite is for a small single-instance deployment;
migrate to PostgreSQL before horizontal scaling. Traffic quotas rely on trusted
usage reports; this service does not inspect packets or independently measure traffic.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field

APP_SECRET = os.getenv("APP_SECRET", "")
BOOTSTRAP_ADMIN_TOKEN = os.getenv("BOOTSTRAP_ADMIN_TOKEN", "")
PUBLIC_BASE_URL = os.getenv("PUBLIC_BASE_URL", "").rstrip("/")
DATABASE_PATH = os.getenv("DATABASE_PATH", "/data/spdcon.db")
TOKEN_TTL_SECONDS = 60 * 60 * 12
PBKDF2_ROUNDS = 310_000
MAX_CONFIG_BYTES = 256_000
MAX_SUBSCRIPTION_BYTES = 1_000_000

app = FastAPI(title="Spd con Config Hub API", version="0.2.0",
              description="Accounts, config library, roles, quotas and revocable subscriptions.")


def _db_path() -> str:
    path = Path(DATABASE_PATH)
    path.parent.mkdir(parents=True, exist_ok=True)
    return str(path)


@contextmanager
def db() -> Iterator[sqlite3.Connection]:
    conn = sqlite3.connect(_db_path(), timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA busy_timeout = 10000")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def init_db() -> None:
    if len(APP_SECRET) < 32:
        raise RuntimeError("APP_SECRET must be configured to at least 32 characters")
    with db() as conn:
        conn.executescript("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE COLLATE NOCASE,
            password_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'user'
                CHECK(role IN ('admin','user')),
            enabled INTEGER NOT NULL DEFAULT 1,
            quota_bytes INTEGER NOT NULL DEFAULT 0 CHECK(quota_bytes >= 0),
            used_bytes INTEGER NOT NULL DEFAULT 0 CHECK(used_bytes >= 0),
            expires_at INTEGER, created_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS sessions (
            token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS configs (
            id TEXT PRIMARY KEY, owner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            name TEXT NOT NULL, protocol TEXT NOT NULL, profile_json TEXT NOT NULL,
            created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE(owner_id,name)
        );
        CREATE TABLE IF NOT EXISTS subscriptions (
            id TEXT PRIMARY KEY, token_hash TEXT NOT NULL UNIQUE,
            owner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            expires_at INTEGER, revoked INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS usage_events (
            id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            node_id TEXT NOT NULL, delta_bytes INTEGER NOT NULL CHECK(delta_bytes >= 0),
            reported_at INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_configs_owner ON configs(owner_id);
        CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON sessions(expires_at);
        CREATE INDEX IF NOT EXISTS idx_subscriptions_owner ON subscriptions(owner_id);
        """)
        conn.commit()


@app.on_event("startup")
def startup() -> None:
    init_db()


def now() -> int:
    return int(time.time())


def hash_secret(value: str) -> str:
    return hmac.new(APP_SECRET.encode("utf-8"), value.encode("utf-8"), hashlib.sha256).hexdigest()


def password_hash(password: str, salt: bytes | None = None) -> str:
    salt = salt or secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ROUNDS)
    return "pbkdf2_sha256" + "$" + str(PBKDF2_ROUNDS) + "$" + salt.hex() + "$" + digest.hex()


def verify_password(password: str, encoded: str) -> bool:
    try:
        algorithm, rounds, salt_hex, digest_hex = encoded.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        actual = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), bytes.fromhex(salt_hex), int(rounds)
        ).hex()
        return hmac.compare_digest(actual, digest_hex)
    except (ValueError, TypeError):
        return False


def public_user(row: sqlite3.Row) -> dict[str, Any]:
    quota = row["quota_bytes"]
    return {"id": row["id"], "username": row["username"], "role": row["role"],
            "enabled": bool(row["enabled"]), "quota_bytes": quota, "used_bytes": row["used_bytes"],
            "remaining_bytes": max(0, quota - row["used_bytes"]) if quota else None,
            "expires_at": row["expires_at"], "created_at": row["created_at"]}


def issue_session(user_id: str) -> str:
    token = secrets.token_urlsafe(40)
    with db() as conn:
        conn.execute("INSERT INTO sessions VALUES(?,?,?,?)",
                     (hash_secret(token), user_id, now() + TOKEN_TTL_SECONDS, now()))
    return token


def current_user(authorization: str | None = Header(default=None)) -> sqlite3.Row:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Bearer token required",
                            headers={"WWW-Authenticate": "Bearer"})
    token = authorization[7:].strip()
    with db() as conn:
        row = conn.execute("""SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id
            WHERE s.token_hash=? AND s.expires_at>?""", (hash_secret(token), now())).fetchone()
    if row is None or not row["enabled"]:
        raise HTTPException(status_code=401, detail="Invalid or expired session")
    if row["expires_at"] is not None and row["expires_at"] <= now():
        raise HTTPException(status_code=403, detail="Account expired")
    return row


def admin_user(user: sqlite3.Row = Depends(current_user)) -> sqlite3.Row:
    if user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Admin role required")
    return user


class RegisterIn(BaseModel):
    username: str = Field(min_length=3, max_length=64, pattern=r"^[A-Za-z0-9_.-]+$")
    password: str = Field(min_length=12, max_length=256)


class LoginIn(BaseModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=256)


class ConfigIn(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    protocol: str = Field(min_length=2, max_length=32)
    profile: dict[str, Any]


class SubscriptionIn(BaseModel):
    expires_at: int | None = None


class LimitsIn(BaseModel):
    quota_bytes: int = Field(ge=0)
    expires_at: int | None = None
    enabled: bool = True


class UsageIn(BaseModel):
    node_id: str = Field(min_length=1, max_length=128)
    delta_bytes: int = Field(ge=0, le=10**13)


def validate_profile(profile: dict[str, Any]) -> str:
    encoded = json.dumps(profile, ensure_ascii=False, separators=(",", ":"))
    if len(encoded.encode("utf-8")) > MAX_CONFIG_BYTES:
        raise HTTPException(status_code=413, detail="Config profile too large")
    if not profile:
        raise HTTPException(status_code=422, detail="Profile cannot be empty")
    return encoded


@app.get("/health/live", tags=["health"])
def liveness() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/ready", tags=["health"])
def readiness() -> dict[str, str]:
    try:
        with db() as conn:
            conn.execute("SELECT 1").fetchone()
        return {"status": "ready", "database": "ok"}
    except sqlite3.Error:
        raise HTTPException(status_code=503, detail="Database unavailable")


@app.post("/auth/register", status_code=201, tags=["auth"])
def register(payload: RegisterIn) -> dict[str, Any]:
    user_id = secrets.token_urlsafe(16)
    with db() as conn:
        if conn.execute("SELECT 1 FROM users WHERE username=?", (payload.username,)).fetchone():
            raise HTTPException(status_code=409, detail="Username already exists")
        conn.execute("INSERT INTO users(id,username,password_hash,created_at) VALUES(?,?,?,?)",
                     (user_id, payload.username, password_hash(payload.password), now()))
        row = conn.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
    return {"user": public_user(row)}


@app.post("/auth/login", tags=["auth"])
def login(payload: LoginIn) -> dict[str, Any]:
    with db() as conn:
        row = conn.execute("SELECT * FROM users WHERE username=?", (payload.username,)).fetchone()
    if row is None or not verify_password(payload.password, row["password_hash"]) or not row["enabled"]:
        raise HTTPException(status_code=401, detail="Invalid username or password")
    if row["expires_at"] is not None and row["expires_at"] <= now():
        raise HTTPException(status_code=403, detail="Account expired")
    return {"access_token": issue_session(row["id"]), "token_type": "bearer",
            "expires_in": TOKEN_TTL_SECONDS, "user": public_user(row)}


@app.post("/auth/logout", tags=["auth"])
def logout(authorization: str | None = Header(default=None),
           user: sqlite3.Row = Depends(current_user)) -> dict[str, str]:
    token = authorization[7:].strip()
    with db() as conn:
        conn.execute("DELETE FROM sessions WHERE token_hash=? AND user_id=?",
                     (hash_secret(token), user["id"]))
    return {"status": "logged_out"}


@app.get("/users/me", tags=["users"])
def me(user: sqlite3.Row = Depends(current_user)) -> dict[str, Any]:
    return public_user(user)


@app.get("/configs", tags=["configs"])
def list_configs(user: sqlite3.Row = Depends(current_user)) -> list[dict[str, Any]]:
    with db() as conn:
        rows = conn.execute("SELECT id,name,protocol,profile_json,created_at,updated_at FROM configs "
                            "WHERE owner_id=? ORDER BY name COLLATE NOCASE", (user["id"],)).fetchall()
    return [{"id": r["id"], "name": r["name"], "protocol": r["protocol"],
             "profile": json.loads(r["profile_json"]), "created_at": r["created_at"],
             "updated_at": r["updated_at"]} for r in rows]


@app.post("/configs", status_code=201, tags=["configs"])
def create_config(payload: ConfigIn, user: sqlite3.Row = Depends(current_user)) -> dict[str, Any]:
    profile_json, config_id, timestamp = validate_profile(payload.profile), secrets.token_urlsafe(16), now()
    with db() as conn:
        try:
            conn.execute("INSERT INTO configs VALUES(?,?,?,?,?,?,?)",
                         (config_id, user["id"], payload.name, payload.protocol.lower(),
                          profile_json, timestamp, timestamp))
        except sqlite3.IntegrityError:
            raise HTTPException(status_code=409, detail="Config name already exists")
    return {"id": config_id, "name": payload.name, "protocol": payload.protocol.lower(),
            "profile": payload.profile, "created_at": timestamp, "updated_at": timestamp}


@app.put("/configs/{config_id}", tags=["configs"])
def update_config(config_id: str, payload: ConfigIn,
                  user: sqlite3.Row = Depends(current_user)) -> dict[str, Any]:
    profile_json, timestamp = validate_profile(payload.profile), now()
    with db() as conn:
        try:
            cursor = conn.execute("UPDATE configs SET name=?,protocol=?,profile_json=?,updated_at=? "
                                  "WHERE id=? AND owner_id=?",
                                  (payload.name, payload.protocol.lower(), profile_json,
                                   timestamp, config_id, user["id"]))
        except sqlite3.IntegrityError:
            raise HTTPException(status_code=409, detail="Config name already exists")
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="Config not found")
    return {"id": config_id, "name": payload.name, "protocol": payload.protocol.lower(),
            "profile": payload.profile, "updated_at": timestamp}


@app.delete("/configs/{config_id}", status_code=204, tags=["configs"])
def delete_config(config_id: str, user: sqlite3.Row = Depends(current_user)) -> None:
    with db() as conn:
        cursor = conn.execute("DELETE FROM configs WHERE id=? AND owner_id=?", (config_id, user["id"]))
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="Config not found")


@app.post("/subscriptions", status_code=201, tags=["subscriptions"])
def create_subscription(payload: SubscriptionIn, user: sqlite3.Row = Depends(current_user)) -> dict[str, Any]:
    if payload.expires_at is not None and payload.expires_at <= now():
        raise HTTPException(status_code=422, detail="Subscription expiry must be in the future")
    token, sub_id, timestamp = secrets.token_urlsafe(48), secrets.token_urlsafe(16), now()
    with db() as conn:
        conn.execute("INSERT INTO subscriptions(id,token_hash,owner_id,expires_at,created_at) VALUES(?,?,?,?,?)",
                     (sub_id, hash_secret(token), user["id"], payload.expires_at, timestamp))
    url_path = f"/subscriptions/{token}"
    subscription_url = f"{PUBLIC_BASE_URL}{url_path}" if PUBLIC_BASE_URL else url_path
    return {"id": sub_id, "url": subscription_url, "expires_at": payload.expires_at,
            "warning": "Keep this URL private; the secret is shown only once."}


@app.get("/subscriptions/{token}", tags=["subscriptions"])
def fetch_subscription(token: str) -> dict[str, Any]:
    with db() as conn:
        sub = conn.execute("""SELECT s.*,u.enabled,u.expires_at AS user_expires,u.quota_bytes,u.used_bytes
            FROM subscriptions s JOIN users u ON u.id=s.owner_id WHERE s.token_hash=?""",
            (hash_secret(token),)).fetchone()
        if (sub is None or sub["revoked"] or not sub["enabled"]
            or (sub["expires_at"] is not None and sub["expires_at"] <= now())
            or (sub["user_expires"] is not None and sub["user_expires"] <= now())
            or (sub["quota_bytes"] > 0 and sub["used_bytes"] >= sub["quota_bytes"])):
            raise HTTPException(status_code=404, detail="Subscription not found or inactive")
        rows = conn.execute("SELECT name,protocol,profile_json FROM configs WHERE owner_id=? "
                            "ORDER BY name COLLATE NOCASE", (sub["owner_id"],)).fetchall()
    profiles = [{"name": r["name"], "protocol": r["protocol"], **json.loads(r["profile_json"])} for r in rows]
    body = json.dumps(profiles, ensure_ascii=False, separators=(",", ":"))
    if len(body.encode("utf-8")) > MAX_SUBSCRIPTION_BYTES:
        raise HTTPException(status_code=413, detail="Subscription payload too large")
    return {"profiles": profiles, "update_interval": 6, "profile_count": len(profiles)}


@app.delete("/subscriptions/{subscription_id}", tags=["subscriptions"])
def revoke_subscription(subscription_id: str,
                        user: sqlite3.Row = Depends(current_user)) -> dict[str, str]:
    with db() as conn:
        cursor = conn.execute("UPDATE subscriptions SET revoked=1 WHERE id=? AND owner_id=?",
                              (subscription_id, user["id"]))
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="Subscription not found")
    return {"status": "revoked"}


@app.post("/admin/bootstrap", status_code=201, tags=["admin"])
def bootstrap_admin(x_bootstrap_token: str | None = Header(default=None)) -> dict[str, Any]:
    if not APP_SECRET or len(APP_SECRET) < 32:
        raise HTTPException(status_code=503, detail="APP_SECRET must be at least 32 characters")
    if not BOOTSTRAP_ADMIN_TOKEN or not x_bootstrap_token or not hmac.compare_digest(
        BOOTSTRAP_ADMIN_TOKEN, x_bootstrap_token
    ):
        raise HTTPException(status_code=403, detail="Valid X-Bootstrap-Token required")
    with db() as conn:
        if conn.execute("SELECT 1 FROM users WHERE role='admin' LIMIT 1").fetchone():
            raise HTTPException(status_code=409, detail="An admin already exists")
        first = conn.execute("SELECT id FROM users ORDER BY created_at LIMIT 1").fetchone()
        if first is None:
            raise HTTPException(status_code=409, detail="Register a first account before bootstrap")
        conn.execute("UPDATE users SET role='admin' WHERE id=?", (first["id"],))
        row = conn.execute("SELECT * FROM users WHERE id=?", (first["id"],)).fetchone()
    return {"user": public_user(row), "notice": "Remove BOOTSTRAP_ADMIN_TOKEN after bootstrap."}


@app.get("/admin/users", tags=["admin"])
def admin_list_users(_: sqlite3.Row = Depends(admin_user)) -> list[dict[str, Any]]:
    with db() as conn:
        rows = conn.execute("SELECT * FROM users ORDER BY created_at DESC").fetchall()
    return [public_user(row) for row in rows]


@app.patch("/admin/users/{user_id}/limits", tags=["admin"])
def admin_update_limits(user_id: str, payload: LimitsIn,
                        _: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    if payload.expires_at is not None and payload.expires_at <= now():
        raise HTTPException(status_code=422, detail="Expiry must be in the future")
    with db() as conn:
        cursor = conn.execute("UPDATE users SET quota_bytes=?,expires_at=?,enabled=? WHERE id=?",
                              (payload.quota_bytes, payload.expires_at, int(payload.enabled), user_id))
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="User not found")
        row = conn.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
    return public_user(row)


@app.post("/admin/users/{user_id}/usage", status_code=201, tags=["admin"])
def admin_report_usage(user_id: str, payload: UsageIn,
                       _: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    """Accept a trusted admin/node usage report; this is not packet-level accounting."""
    event_id, timestamp = secrets.token_urlsafe(16), now()
    with db() as conn:
        if not conn.execute("SELECT 1 FROM users WHERE id=?", (user_id,)).fetchone():
            raise HTTPException(status_code=404, detail="User not found")
        conn.execute("INSERT INTO usage_events VALUES(?,?,?,?,?)",
                     (event_id, user_id, payload.node_id, payload.delta_bytes, timestamp))
        conn.execute("UPDATE users SET used_bytes=used_bytes+? WHERE id=?",
                     (payload.delta_bytes, user_id))
        updated = conn.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
    return {"event_id": event_id, "user": public_user(updated)}


@app.get("/admin/usage", tags=["admin"])
def admin_usage(_: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    with db() as conn:
        totals = conn.execute("SELECT COUNT(*) AS events, COALESCE(SUM(delta_bytes),0) AS bytes "
                              "FROM usage_events").fetchone()
    return {"events": totals["events"], "reported_bytes": totals["bytes"],
            "source": "trusted usage reports"}
