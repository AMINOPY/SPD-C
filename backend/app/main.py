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

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.staticfiles import StaticFiles
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
app.mount("/panel", StaticFiles(directory=str(Path(__file__).parent / "static"), html=True), name="panel")


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
        CREATE TABLE IF NOT EXISTS nodes (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            secret_hash TEXT NOT NULL UNIQUE,
            enabled INTEGER NOT NULL DEFAULT 1,
            created_at INTEGER NOT NULL,
            last_seen_at INTEGER
        );
        CREATE TABLE IF NOT EXISTS node_events (
            event_id TEXT PRIMARY KEY,
            node_id TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
            received_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS connection_policies (
            user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
            max_distinct_ips INTEGER NOT NULL DEFAULT 0 CHECK(max_distinct_ips >= 0 AND max_distinct_ips <= 256),
            max_concurrent_connections INTEGER NOT NULL DEFAULT 0
                CHECK(max_concurrent_connections >= 0 AND max_concurrent_connections <= 1024),
            action TEXT NOT NULL DEFAULT 'observe_only'
                CHECK(action IN ('observe_only','alert_only','deny_new_ip')),
            updated_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS connection_observations (
            connection_id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            node_id TEXT NOT NULL,
            client_ip TEXT NOT NULL,
            client_label TEXT,
            state TEXT NOT NULL CHECK(state IN ('active','disconnected')),
            first_seen_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL,
            disconnected_at INTEGER,
            upload_bytes INTEGER NOT NULL DEFAULT 0 CHECK(upload_bytes >= 0),
            download_bytes INTEGER NOT NULL DEFAULT 0 CHECK(download_bytes >= 0)
        );
        CREATE INDEX IF NOT EXISTS idx_configs_owner ON configs(owner_id);
        CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON sessions(expires_at);
        CREATE INDEX IF NOT EXISTS idx_subscriptions_owner ON subscriptions(owner_id);
        CREATE INDEX IF NOT EXISTS idx_connection_user_last_seen
            ON connection_observations(user_id, last_seen_at);
        CREATE INDEX IF NOT EXISTS idx_connection_user_ip
            ON connection_observations(user_id, client_ip);
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


class ConnectionPolicyIn(BaseModel):
    # Zero means "monitor only / no limit" for that dimension.
    max_distinct_ips: int = Field(default=0, ge=0, le=256)
    max_concurrent_connections: int = Field(default=0, ge=0, le=1024)
    action: str = Field(default="observe_only", pattern=r"^(observe_only|alert_only|deny_new_ip)$")


class ConnectionReportIn(BaseModel):
    connection_id: str = Field(min_length=8, max_length=128)
    node_id: str = Field(min_length=1, max_length=128)
    client_ip: str = Field(min_length=3, max_length=64)
    client_label: str | None = Field(default=None, max_length=128)
    state: str = Field(default="active", pattern=r"^(active|disconnected)$")
    upload_bytes: int = Field(default=0, ge=0, le=10**15)
    download_bytes: int = Field(default=0, ge=0, le=10**15)


class NodeCreateIn(BaseModel):
    name: str = Field(min_length=1, max_length=120)


class NodeConnectionReportIn(ConnectionReportIn):
    user_id: str = Field(min_length=1, max_length=128)
    event_id: str = Field(min_length=16, max_length=128)
    event_timestamp: int = Field(ge=0)


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


@app.put("/admin/users/{user_id}/connection-policy", tags=["admin", "connections"])
def admin_set_connection_policy(user_id: str, payload: ConnectionPolicyIn,
                                _: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    """Save desired policy. This API does not itself block traffic; a trusted node agent must enforce it."""
    timestamp = now()
    with db() as conn:
        if not conn.execute("SELECT 1 FROM users WHERE id=?", (user_id,)).fetchone():
            raise HTTPException(status_code=404, detail="User not found")
        conn.execute(
            "INSERT INTO connection_policies(user_id,max_distinct_ips,max_concurrent_connections,action,updated_at) "
            "VALUES(?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET "
            "max_distinct_ips=excluded.max_distinct_ips, "
            "max_concurrent_connections=excluded.max_concurrent_connections, "
            "action=excluded.action, updated_at=excluded.updated_at",
            (user_id, payload.max_distinct_ips, payload.max_concurrent_connections,
             payload.action, timestamp),
        )
    return {"user_id": user_id, **payload.model_dump(), "updated_at": timestamp,
            "enforcement": "pending_node_agent"}


@app.get("/admin/users/{user_id}/connection-policy", tags=["admin", "connections"])
def admin_get_connection_policy(user_id: str,
                                _: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    with db() as conn:
        if not conn.execute("SELECT 1 FROM users WHERE id=?", (user_id,)).fetchone():
            raise HTTPException(status_code=404, detail="User not found")
        row = conn.execute("SELECT * FROM connection_policies WHERE user_id=?", (user_id,)).fetchone()
    if row is None:
        return {"user_id": user_id, "max_distinct_ips": 0,
                "max_concurrent_connections": 0, "action": "observe_only",
                "enforcement": "pending_node_agent"}
    return {"user_id": user_id, "max_distinct_ips": row["max_distinct_ips"],
            "max_concurrent_connections": row["max_concurrent_connections"],
            "action": row["action"], "updated_at": row["updated_at"],
            "enforcement": "pending_node_agent"}


@app.post("/admin/users/{user_id}/connections/report", status_code=201,
          tags=["admin", "connections"])
def admin_report_connection(user_id: str, payload: ConnectionReportIn,
                            _: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    """Record node-reported connection telemetry; reports are not authenticated from nodes yet."""
    import ipaddress

    try:
        client_ip = str(ipaddress.ip_address(payload.client_ip))
    except ValueError:
        raise HTTPException(status_code=422, detail="client_ip must be a valid IPv4 or IPv6 address")

    timestamp = now()
    with db() as conn:
        if not conn.execute("SELECT 1 FROM users WHERE id=?", (user_id,)).fetchone():
            raise HTTPException(status_code=404, detail="User not found")
        previous = conn.execute(
            "SELECT first_seen_at FROM connection_observations WHERE connection_id=?",
            (payload.connection_id,),
        ).fetchone()
        if previous is not None:
            owner = conn.execute(
                "SELECT user_id FROM connection_observations WHERE connection_id=?",
                (payload.connection_id,),
            ).fetchone()
            if owner["user_id"] != user_id:
                raise HTTPException(status_code=409, detail="Connection ID already belongs to another user")
        first_seen = previous["first_seen_at"] if previous is not None else timestamp
        disconnected_at = timestamp if payload.state == "disconnected" else None
        conn.execute(
            "INSERT INTO connection_observations(connection_id,user_id,node_id,client_ip,client_label,state,"
            "first_seen_at,last_seen_at,disconnected_at,upload_bytes,download_bytes) "
            "VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(connection_id) DO UPDATE SET "
            "node_id=excluded.node_id,client_ip=excluded.client_ip,client_label=excluded.client_label,"
            "state=excluded.state,last_seen_at=excluded.last_seen_at,disconnected_at=excluded.disconnected_at,"
            "upload_bytes=MAX(connection_observations.upload_bytes,excluded.upload_bytes),"
            "download_bytes=MAX(connection_observations.download_bytes,excluded.download_bytes)",
            (payload.connection_id, user_id, payload.node_id, client_ip, payload.client_label,
             payload.state, first_seen, timestamp, disconnected_at,
             payload.upload_bytes, payload.download_bytes),
        )
        policy = conn.execute("SELECT * FROM connection_policies WHERE user_id=?", (user_id,)).fetchone()
        distinct_ips = conn.execute(
            "SELECT COUNT(DISTINCT client_ip) FROM connection_observations "
            "WHERE user_id=? AND last_seen_at>=?",
            (user_id, timestamp - 24 * 60 * 60),
        ).fetchone()[0]
        active_connections = conn.execute(
            "SELECT COUNT(*) FROM connection_observations WHERE user_id=? AND state='active' "
            "AND last_seen_at>=?",
            (user_id, timestamp - 5 * 60),
        ).fetchone()[0]
        row = conn.execute("SELECT * FROM connection_observations WHERE connection_id=?",
                           (payload.connection_id,)).fetchone()

    alerts: list[str] = []
    if policy is not None:
        if policy["max_distinct_ips"] and distinct_ips > policy["max_distinct_ips"]:
            alerts.append("distinct_ip_limit_exceeded")
        if policy["max_concurrent_connections"] and active_connections > policy["max_concurrent_connections"]:
            alerts.append("concurrent_connection_limit_exceeded")
    requested_action = policy["action"] if policy is not None else "observe_only"
    return {
        "connection": {"connection_id": row["connection_id"], "user_id": row["user_id"],
                       "node_id": row["node_id"], "client_ip": row["client_ip"],
                       "client_label": row["client_label"], "state": row["state"],
                       "first_seen_at": row["first_seen_at"], "last_seen_at": row["last_seen_at"],
                       "upload_bytes": row["upload_bytes"], "download_bytes": row["download_bytes"]},
        "observed_distinct_ips_24h": distinct_ips,
        "observed_active_connections_5m": active_connections,
        "usage_delta_bytes": usage_delta,
        "alerts": alerts,
        "requested_policy_action": requested_action,
        "enforcement": "not_enforced_node_agent_required",
    }


@app.get("/admin/users/{user_id}/connections", tags=["admin", "connections"])
def admin_list_connections(user_id: str,
                           _: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    with db() as conn:
        if not conn.execute("SELECT 1 FROM users WHERE id=?", (user_id,)).fetchone():
            raise HTTPException(status_code=404, detail="User not found")
        rows = conn.execute(
            "SELECT connection_id,node_id,client_ip,client_label,state,first_seen_at,last_seen_at,"
            "disconnected_at,upload_bytes,download_bytes FROM connection_observations "
            "WHERE user_id=? ORDER BY last_seen_at DESC LIMIT 500", (user_id,)
        ).fetchall()
    return {"user_id": user_id, "connections": [dict(row) for row in rows],
            "privacy_note": "Contains client IP addresses; restrict admin access and define retention."}



@app.post("/admin/nodes", status_code=201, tags=["admin", "nodes"])
def admin_create_node(payload: NodeCreateIn,
                      _: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    """Enroll a node and reveal its bearer secret once. Use only behind HTTPS."""
    node_id = "node_" + secrets.token_urlsafe(12)
    node_secret = secrets.token_urlsafe(48)
    timestamp = now()
    with db() as conn:
        conn.execute(
            "INSERT INTO nodes(id,name,secret_hash,enabled,created_at) VALUES(?,?,?,?,?)",
            (node_id, payload.name, hash_secret(node_secret), 1, timestamp),
        )
    return {"id": node_id, "name": payload.name, "node_secret": node_secret,
            "created_at": timestamp,
            "warning": "Store node_secret in a root-readable secret file. It is shown only once; HTTPS is required."}


@app.get("/admin/nodes", tags=["admin", "nodes"])
def admin_list_nodes(_: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    with db() as conn:
        rows = conn.execute(
            "SELECT id,name,enabled,created_at,last_seen_at FROM nodes ORDER BY created_at DESC"
        ).fetchall()
    return {"nodes": [dict(row) for row in rows]}


@app.patch("/admin/nodes/{node_id}/enabled", tags=["admin", "nodes"])
def admin_set_node_enabled(node_id: str, enabled: bool,
                           _: sqlite3.Row = Depends(admin_user)) -> dict[str, Any]:
    with db() as conn:
        cursor = conn.execute("UPDATE nodes SET enabled=? WHERE id=?",
                              (int(enabled), node_id))
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="Node not found")
        row = conn.execute("SELECT id,name,enabled,created_at,last_seen_at FROM nodes WHERE id=?",
                           (node_id,)).fetchone()
    return dict(row)


@app.post("/nodes/{node_id}/connections/report", status_code=201,
          tags=["nodes", "connections"])
def node_report_connection(node_id: str, payload: NodeConnectionReportIn,
                           authorization: str | None = Header(default=None)) -> dict[str, Any]:
    """Receive idempotent, time-bounded telemetry from an enrolled node over HTTPS."""
    import ipaddress

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Node bearer secret required")
    node_secret = authorization[7:].strip()
    timestamp = now()
    if abs(timestamp - payload.event_timestamp) > 300:
        raise HTTPException(status_code=401, detail="Telemetry event timestamp outside 5-minute window")
    try:
        client_ip = str(ipaddress.ip_address(payload.client_ip))
    except ValueError:
        raise HTTPException(status_code=422, detail="client_ip must be a valid IPv4 or IPv6 address")

    with db() as conn:
        node = conn.execute("SELECT * FROM nodes WHERE id=?", (node_id,)).fetchone()
        if node is None or not node["enabled"] or not hmac.compare_digest(
            node["secret_hash"], hash_secret(node_secret)
        ):
            raise HTTPException(status_code=401, detail="Unknown, disabled or invalid node credentials")
        if conn.execute("SELECT 1 FROM node_events WHERE event_id=?", (payload.event_id,)).fetchone():
            raise HTTPException(status_code=409, detail="Duplicate telemetry event")
        if not conn.execute("SELECT 1 FROM users WHERE id=?", (payload.user_id,)).fetchone():
            raise HTTPException(status_code=404, detail="User not found")
        if payload.node_id != node_id:
            raise HTTPException(status_code=422, detail="Payload node_id must match authenticated node")
        previous = conn.execute(
            "SELECT user_id,first_seen_at,upload_bytes,download_bytes "
            "FROM connection_observations WHERE connection_id=?",
            (payload.connection_id,),
        ).fetchone()
        if previous is not None and previous["user_id"] != payload.user_id:
            raise HTTPException(status_code=409, detail="Connection ID already belongs to another user")
        first_seen = previous["first_seen_at"] if previous else timestamp
        previous_upload = previous["upload_bytes"] if previous else 0
        previous_download = previous["download_bytes"] if previous else 0
        # Runtime counters are cumulative per connection. Count only positive deltas so
        # repeated snapshots never charge the same bytes twice. A runtime restart should
        # issue a new connection_id rather than reset counters for an existing connection.
        usage_delta = max(0, payload.upload_bytes - previous_upload) + max(
            0, payload.download_bytes - previous_download
        )
        disconnected_at = timestamp if payload.state == "disconnected" else None
        conn.execute(
            "INSERT INTO connection_observations(connection_id,user_id,node_id,client_ip,client_label,state,"
            "first_seen_at,last_seen_at,disconnected_at,upload_bytes,download_bytes) "
            "VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(connection_id) DO UPDATE SET "
            "node_id=excluded.node_id,client_ip=excluded.client_ip,client_label=excluded.client_label,"
            "state=excluded.state,last_seen_at=excluded.last_seen_at,disconnected_at=excluded.disconnected_at,"
            "upload_bytes=MAX(connection_observations.upload_bytes,excluded.upload_bytes),"
            "download_bytes=MAX(connection_observations.download_bytes,excluded.download_bytes)",
            (payload.connection_id, payload.user_id, node_id, client_ip, payload.client_label,
             payload.state, first_seen, timestamp, disconnected_at,
             payload.upload_bytes, payload.download_bytes),
        )
        conn.execute("INSERT INTO node_events(event_id,node_id,received_at) VALUES(?,?,?)",
                     (payload.event_id, node_id, timestamp))
        if usage_delta > 0:
            conn.execute("INSERT INTO usage_events(id,user_id,node_id,delta_bytes,reported_at) "
                         "VALUES(?,?,?,?,?)",
                         (payload.event_id, payload.user_id, node_id, usage_delta, timestamp))
            conn.execute("UPDATE users SET used_bytes=used_bytes+? WHERE id=?",
                         (usage_delta, payload.user_id))
        conn.execute("UPDATE nodes SET last_seen_at=? WHERE id=?", (timestamp, node_id))
        policy = conn.execute("SELECT * FROM connection_policies WHERE user_id=?",
                              (payload.user_id,)).fetchone()
        distinct_ips = conn.execute(
            "SELECT COUNT(DISTINCT client_ip) FROM connection_observations "
            "WHERE user_id=? AND last_seen_at>=?",
            (payload.user_id, timestamp - 24 * 60 * 60),
        ).fetchone()[0]
        active_connections = conn.execute(
            "SELECT COUNT(*) FROM connection_observations WHERE user_id=? AND state='active' "
            "AND last_seen_at>=?",
            (payload.user_id, timestamp - 5 * 60),
        ).fetchone()[0]

    alerts: list[str] = []
    if policy is not None:
        if policy["max_distinct_ips"] and distinct_ips > policy["max_distinct_ips"]:
            alerts.append("distinct_ip_limit_exceeded")
        if policy["max_concurrent_connections"] and active_connections > policy["max_concurrent_connections"]:
            alerts.append("concurrent_connection_limit_exceeded")
    return {
        "accepted": True, "event_id": payload.event_id, "node_id": node_id,
        "user_id": payload.user_id, "client_ip": client_ip,
        "observed_distinct_ips_24h": distinct_ips,
        "observed_active_connections_5m": active_connections,
        "alerts": alerts,
        "requested_policy_action": policy["action"] if policy is not None else "observe_only",
        "enforcement": "telemetry_only_node_runtime_enforcement_not_implemented",
    }
