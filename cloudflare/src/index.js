/**
 * Spd con Cloudflare Worker API.
 * Android-first: this is a sync/subscription API, not a required web panel.
 * Requires D1 binding DB and APP_SECRET + BOOTSTRAP_ADMIN_TOKEN secrets.
 */
import { panelHtml } from "./panel.js";
const SESSION_SECONDS = 60 * 60 * 24 * 14;
const PASSWORD_ITERATIONS = 310_000;
const MAX_JSON_BYTES = 256_000;

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers },
  });
}
function error(message, status = 400) { return json({ error: message }, status); }
function now() { return Math.floor(Date.now() / 1000); }
function randomToken(bytes = 32) {
  const value = crypto.getRandomValues(new Uint8Array(bytes));
  return [...value].map((b) => b.toString(16).padStart(2, "0")).join("");
}
function hex(bytes) { return [...new Uint8Array(bytes)].map((b) => b.toString(16).padStart(2, "0")).join(""); }
function fromHex(value) {
  const out = new Uint8Array(value.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(value.slice(i * 2, i * 2 + 2), 16);
  return out;
}
function constantTimeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
async function keyedHash(secret, value) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return hex(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value)));
}
async function passwordHash(password, saltHex = randomToken(16)) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: fromHex(saltHex), iterations: PASSWORD_ITERATIONS }, key, 256);
  return { salt: saltHex, hash: hex(bits) };
}
async function readJson(request, maxBytes = MAX_JSON_BYTES) {
  const length = Number(request.headers.get("content-length") || 0);
  if (length > maxBytes) throw new Error("Request body too large");
  const raw = await request.text();
  if (new TextEncoder().encode(raw).length > maxBytes) throw new Error("Request body too large");
  try { return JSON.parse(raw); } catch { throw new Error("Invalid JSON body"); }
}
function cleanUser(row) {
  return { id: row.id, username: row.username, role: row.role, enabled: Boolean(row.enabled),
    quotaBytes: row.quota_bytes, usedBytes: row.used_bytes, expiresAt: row.expires_at, createdAt: row.created_at };
}
function corsHeaders(request, env) {
  const origin = request.headers.get("origin");
  const allowed = (env.ALLOWED_ORIGINS || "").split(",").map((x) => x.trim()).filter(Boolean);
  const headers = { "access-control-allow-methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS", "access-control-allow-headers": "Authorization,Content-Type,X-Bootstrap-Token", "access-control-max-age": "86400", "vary": "Origin" };
  if (origin && allowed.includes(origin)) headers["access-control-allow-origin"] = origin;
  return headers;
}
function withCors(response, headers) {
  const next = new Response(response.body, response);
  for (const [key, value] of Object.entries(headers)) next.headers.set(key, value);
  return next;
}
async function authenticate(request, env) {
  const match = /^Bearer\s+([a-f0-9]{64})$/i.exec(request.headers.get("authorization") || "");
  if (!match) return null;
  const tokenHash = await keyedHash(env.APP_SECRET, match[1]);
  const row = await env.DB.prepare(
    "SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>? LIMIT 1"
  ).bind(tokenHash, now()).first();
  if (!row || !row.enabled || (row.expires_at !== null && row.expires_at <= now())) return null;
  return row;
}
async function requireUser(request, env) {
  const user = await authenticate(request, env);
  return user ? { user } : { response: error("Authentication required", 401) };
}
function routePath(url) { return url.pathname.replace(/\/+$/, "") || "/"; }

async function handle(request, env) {
  if (!env.DB || !env.APP_SECRET || env.APP_SECRET.length < 32) return error("Server configuration incomplete", 500);
  const url = new URL(request.url);
  const path = routePath(url);
  const method = request.method.toUpperCase();
  if (method === "OPTIONS") return new Response(null, { status: 204 });
  if (method === "GET" && (path === "/panel" || path === "/")) {
    return new Response(panelHtml, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
  }

  if (method === "GET" && path === "/api/v1/health") {
    try { await env.DB.prepare("SELECT 1 AS ok").first(); return json({ ok: true, service: "spd-con-api" }); }
    catch { return error("Database unavailable", 503); }
  }

  if (method === "POST" && path === "/api/v1/auth/register") {
    let body;
    try { body = await readJson(request); } catch (e) { return error(e.message); }
    const username = String(body.username || "").trim();
    const password = String(body.password || "");
    if (!/^[a-zA-Z0-9_.-]{3,40}$/.test(username)) return error("Username must be 3-40 letters, digits, dot, underscore or hyphen");
    if (password.length < 12 || password.length > 256) return error("Password must be 12-256 characters");
    const id = crypto.randomUUID();
    const pass = await passwordHash(password);
    try {
      await env.DB.prepare("INSERT INTO users(id,username,password_salt,password_hash,role,created_at) VALUES(?,?,?,?, 'user',?)")
        .bind(id, username, pass.salt, pass.hash, now()).run();
      return json({ user: { id, username, role: "user" } }, 201);
    } catch (e) {
      if (String(e).toLowerCase().includes("unique")) return error("Username is already registered", 409);
      return error("Registration failed", 500);
    }
  }

  if (method === "POST" && path === "/api/v1/auth/login") {
    let body;
    try { body = await readJson(request); } catch (e) { return error(e.message); }
    const username = String(body.username || "").trim();
    const password = String(body.password || "");
    const row = await env.DB.prepare("SELECT * FROM users WHERE username=? COLLATE NOCASE LIMIT 1").bind(username).first();
    if (!row || !constantTimeEqual((await passwordHash(password, row.password_salt)).hash, row.password_hash) || !row.enabled) {
      return error("Invalid username or password", 401);
    }
    if (row.expires_at !== null && row.expires_at <= now()) return error("Account expired", 403);
    const token = randomToken();
    await env.DB.prepare("INSERT INTO sessions(token_hash,user_id,expires_at,created_at) VALUES(?,?,?,?)")
      .bind(await keyedHash(env.APP_SECRET, token), row.id, now() + SESSION_SECONDS, now()).run();
    return json({ token, expiresAt: now() + SESSION_SECONDS, user: cleanUser(row) });
  }

  if (method === "POST" && path === "/api/v1/admin/bootstrap") {
    if (!env.BOOTSTRAP_ADMIN_TOKEN || !constantTimeEqual(request.headers.get("x-bootstrap-token") || "", env.BOOTSTRAP_ADMIN_TOKEN)) {
      return error("Invalid bootstrap token", 403);
    }
    const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM users WHERE role='admin'").first();
    if (Number(count?.n || 0) > 0) return error("Admin already bootstrapped", 409);
    let body;
    try { body = await readJson(request); } catch (e) { return error(e.message); }
    const username = String(body.username || "").trim();
    const password = String(body.password || "");
    if (!/^[a-zA-Z0-9_.-]{3,40}$/.test(username) || password.length < 12 || password.length > 256) return error("Valid username and 12-256 character password required");
    const id = crypto.randomUUID();
    const pass = await passwordHash(password);
    try {
      await env.DB.prepare("INSERT INTO users(id,username,password_salt,password_hash,role,created_at) VALUES(?,?,?,?, 'admin',?)")
        .bind(id, username, pass.salt, pass.hash, now()).run();
      return json({ id, username, role: "admin" }, 201);
    } catch { return error("Bootstrap failed; username may already exist", 409); }
  }

  if (path.startsWith("/api/v1/subscriptions/") && method === "GET") {
    const token = path.slice("/api/v1/subscriptions/".length);
    if (!/^[a-f0-9]{64}$/i.test(token)) return error("Subscription not found", 404);
    const tokenHash = await keyedHash(env.APP_SECRET, token);
    const sub = await env.DB.prepare("SELECT s.*,u.enabled,u.expires_at AS user_expires,u.quota_bytes,u.used_bytes FROM subscriptions s JOIN users u ON u.id=s.owner_id WHERE s.token_hash=? LIMIT 1").bind(tokenHash).first();
    if (!sub || sub.revoked || !sub.enabled || (sub.expires_at !== null && sub.expires_at <= now()) ||
        (sub.user_expires !== null && sub.user_expires <= now()) || (sub.quota_bytes > 0 && sub.used_bytes >= sub.quota_bytes)) {
      return error("Subscription expired, revoked, disabled or unavailable", 404);
    }
    const configs = await env.DB.prepare("SELECT name,protocol,profile_json FROM configs WHERE owner_id=? ORDER BY name COLLATE NOCASE").bind(sub.owner_id).all();
    return json({ updatedAt: now(), profiles: (configs.results || []).map((c) => ({ name: c.name, protocol: c.protocol, profile: JSON.parse(c.profile_json) })) });
  }

  const auth = await requireUser(request, env);
  if (auth.response) return auth.response;
  const user = auth.user;

  if (method === "POST" && path === "/api/v1/auth/logout") {
    const token = /^Bearer\s+([a-f0-9]{64})$/i.exec(request.headers.get("authorization") || "")?.[1];
    if (token) await env.DB.prepare("DELETE FROM sessions WHERE token_hash=?").bind(await keyedHash(env.APP_SECRET, token)).run();
    return json({ ok: true });
  }
  if (method === "GET" && path === "/api/v1/users/me") return json({ user: cleanUser(user) });

  if (method === "GET" && path === "/api/v1/nodes") {
    const result = await env.DB.prepare("SELECT id,name,address,port,country,protocol,network,security,sni,public_key,short_id,fingerprint FROM nodes WHERE enabled=1 ORDER BY country,name LIMIT 300").all();
    return json({ nodes: (result.results || []).map((n) => ({ id:n.id,name:n.name,address:n.address,port:n.port,country:n.country,protocol:n.protocol,network:n.network,security:n.security,sni:n.sni,publicKey:n.public_key,shortId:n.short_id,fingerprint:n.fingerprint })) });
  }

  if (path === "/api/v1/configs" && method === "GET") {
    const result = await env.DB.prepare("SELECT id,name,protocol,profile_json,created_at,updated_at FROM configs WHERE owner_id=? ORDER BY updated_at DESC").bind(user.id).all();
    return json({ configs: (result.results || []).map((r) => ({ ...r, profile: JSON.parse(r.profile_json) })) });
  }
  if (path === "/api/v1/configs" && method === "POST") {
    let body;
    try { body = await readJson(request); } catch (e) { return error(e.message); }
    const name = String(body.name || "").trim();
    const protocol = String(body.protocol || "").trim().toLowerCase();
    const profile = body.profile;
    if (!name || name.length > 120 || !["vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic"].includes(protocol) ||
        !profile || typeof profile !== "object" || Array.isArray(profile)) return error("Valid name, supported protocol and profile object required");
    const id = crypto.randomUUID(), ts = now();
    try {
      await env.DB.prepare("INSERT INTO configs(id,owner_id,name,protocol,profile_json,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")
        .bind(id, user.id, name, protocol, JSON.stringify(profile), ts, ts).run();
      return json({ id, name, protocol, profile, createdAt: ts, updatedAt: ts }, 201);
    } catch (e) {
      if (String(e).toLowerCase().includes("unique")) return error("A config with this name already exists", 409);
      return error("Could not save config", 500);
    }
  }
  const configMatch = /^\/api\/v1\/configs\/([a-f0-9-]+)$/.exec(path);
  if (configMatch && method === "DELETE") {
    const result = await env.DB.prepare("DELETE FROM configs WHERE id=? AND owner_id=?").bind(configMatch[1], user.id).run();
    return Number(result.meta?.changes || 0) ? json({ ok: true }) : error("Config not found", 404);
  }
  if (configMatch && method === "PUT") {
    let body;
    try { body = await readJson(request); } catch (e) { return error(e.message); }
    const name = String(body.name || "").trim(), protocol = String(body.protocol || "").trim().toLowerCase(), profile = body.profile;
    if (!name || name.length > 120 || !["vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic"].includes(protocol) || !profile || typeof profile !== "object" || Array.isArray(profile)) return error("Valid name, protocol and profile object required");
    const result = await env.DB.prepare("UPDATE configs SET name=?,protocol=?,profile_json=?,updated_at=? WHERE id=? AND owner_id=?")
      .bind(name, protocol, JSON.stringify(profile), now(), configMatch[1], user.id).run();
    return Number(result.meta?.changes || 0) ? json({ ok: true }) : error("Config not found", 404);
  }

  if (method === "POST" && path === "/api/v1/subscriptions") {
    let body;
    try { body = await readJson(request); } catch (e) { return error(e.message); }
    const expiry = body.expiresAt == null ? null : Number(body.expiresAt);
    if (expiry !== null && (!Number.isInteger(expiry) || expiry <= now())) return error("expiresAt must be a future Unix timestamp");
    const token = randomToken(), id = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO subscriptions(id,owner_id,token_hash,expires_at,created_at) VALUES(?,?,?,?,?)")
      .bind(id, user.id, await keyedHash(env.APP_SECRET, token), expiry, now()).run();
    const base = (env.PUBLIC_BASE_URL || url.origin).replace(/\/$/, "");
    return json({ id, token, url: base + "/api/v1/subscriptions/" + token, expiresAt: expiry }, 201);
  }
  const subMatch = /^\/api\/v1\/subscriptions\/id\/([a-f0-9-]+)$/.exec(path);
  if (subMatch && method === "DELETE") {
    const result = await env.DB.prepare("UPDATE subscriptions SET revoked=1 WHERE id=? AND owner_id=?").bind(subMatch[1], user.id).run();
    return Number(result.meta?.changes || 0) ? json({ ok: true }) : error("Subscription not found", 404);
  }

  if (path.startsWith("/api/v1/admin/")) {
    if (user.role !== "admin") return error("Admin role required", 403);
    if (method === "GET" && path === "/api/v1/admin/nodes") {
      const result = await env.DB.prepare("SELECT id,name,address,port,country,protocol,network,security,sni,public_key,short_id,fingerprint,enabled,created_at,updated_at FROM nodes ORDER BY created_at DESC LIMIT 300").all();
      return json({ nodes: (result.results || []).map((n) => ({ ...n, enabled: Boolean(n.enabled) })) });
    }
    if (method === "POST" && path === "/api/v1/admin/nodes") {
      let body;
      try { body = await readJson(request); } catch (e) { return error(e.message); }
      const name = String(body.name || "").trim();
      const address = String(body.address || "").trim();
      const port = Number(body.port || 443);
      const rawCountry = String(body.country || "auto");
      const country = rawCountry.toLowerCase() === "auto" ? "auto" : rawCountry.toUpperCase();
      const protocol = String(body.protocol || "vless").toLowerCase();
      const network = String(body.network || "tcp").toLowerCase();
      const security = String(body.security || "tls").toLowerCase();
      const sni = String(body.sni || "").trim();
      const publicKey = String(body.publicKey || "").trim();
      const shortId = String(body.shortId || "").trim().toLowerCase();
      const fingerprint = String(body.fingerprint || "chrome").trim();
      if (!name || name.length > 80 || !address || address.length > 253 || /\\s/.test(address) ||
          !Number.isInteger(port) || port < 1 || port > 65535 ||
          !/^(auto|[A-Z]{2})$/.test(country) ||
          !["vless","vmess","trojan","shadowsocks","hysteria2","tuic"].includes(protocol) ||
          !["tcp","raw","ws","grpc","xhttp","udp"].includes(network) ||
          !["tls","reality","none"].includes(security) ||
          !["chrome","firefox","safari","ios","edge","android","360","qq"].includes(fingerprint)) {
        return error("Invalid node fields");
      }
      if (security === "reality" && (protocol !== "vless" || !sni || !/^[a-z0-9.-]+$/i.test(sni) ||
          !publicKey || !/^[a-f0-9]{0,16}$/.test(shortId) || shortId.length % 2 !== 0)) {
        return error("REALITY nodes need VLESS, a valid SNI, server public key and an even-length hex shortId (up to 16 chars)");
      }
      const id = crypto.randomUUID(), ts = now();
      try {
        await env.DB.prepare("INSERT INTO nodes(id,name,address,port,country,protocol,network,security,sni,public_key,short_id,fingerprint,enabled,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,1,?,?)")
          .bind(id,name,address,port,country,protocol,network,security,sni,publicKey,shortId,fingerprint,ts,ts).run();
        return json({ node: { id,name,address,port,country,protocol,network,security,sni,publicKey,shortId,fingerprint,enabled:true,createdAt:ts,updatedAt:ts } }, 201);
      } catch { return error("Could not save node; check that migration 0002_nodes.sql has been applied", 500); }
    }
    const nodeMatch = /^\\/api\\/v1\\/admin\\/nodes\\/([a-f0-9-]+)$/.exec(path);
    if (nodeMatch && (method === "PATCH" || method === "DELETE")) {
      if (method === "DELETE") {
        const result = await env.DB.prepare("DELETE FROM nodes WHERE id=?").bind(nodeMatch[1]).run();
        return Number(result.meta?.changes || 0) ? json({ ok: true }) : error("Node not found", 404);
      }
      let body;
      try { body = await readJson(request); } catch (e) { return error(e.message); }
      const allowed = ["name","address","port","country","protocol","network","security","sni","publicKey","shortId","fingerprint","enabled"];
      if (Object.keys(body).some((key) => !allowed.includes(key))) return error("Unsupported node field");
      const current = await env.DB.prepare("SELECT * FROM nodes WHERE id=?").bind(nodeMatch[1]).first();
      if (!current) return error("Node not found", 404);
      const merged = {
        name: body.name == null ? current.name : String(body.name).trim(),
        address: body.address == null ? current.address : String(body.address).trim(),
        port: body.port == null ? Number(current.port) : Number(body.port),
        country: body.country == null ? current.country : String(body.country).toUpperCase(),
        protocol: body.protocol == null ? current.protocol : String(body.protocol).toLowerCase(),
        network: body.network == null ? current.network : String(body.network).toLowerCase(),
        security: body.security == null ? current.security : String(body.security).toLowerCase(),
        sni: body.sni == null ? current.sni : String(body.sni).trim(),
        publicKey: body.publicKey == null ? current.public_key : String(body.publicKey).trim(),
        shortId: body.shortId == null ? current.short_id : String(body.shortId).trim().toLowerCase(),
        fingerprint: body.fingerprint == null ? current.fingerprint : String(body.fingerprint).trim(),
        enabled: body.enabled == null ? Boolean(current.enabled) : body.enabled
      };
      if (!merged.name || merged.name.length > 80 || !merged.address || merged.address.length > 253 || /\\s/.test(merged.address) ||
          !Number.isInteger(merged.port) || merged.port < 1 || merged.port > 65535 ||
          !/^(auto|[A-Z]{2})$/.test(merged.country) ||
          !["vless","vmess","trojan","shadowsocks","hysteria2","tuic"].includes(merged.protocol) ||
          !["tcp","raw","ws","grpc","xhttp","udp"].includes(merged.network) ||
          !["tls","reality","none"].includes(merged.security) || typeof merged.enabled !== "boolean" ||
          !["chrome","firefox","safari","ios","edge","android","360","qq"].includes(merged.fingerprint)) return error("Invalid node fields");
      if (merged.security === "reality" && (merged.protocol !== "vless" || !merged.sni || !/^[a-z0-9.-]+$/i.test(merged.sni) ||
          !merged.publicKey || !/^[a-f0-9]{0,16}$/.test(merged.shortId) || merged.shortId.length % 2 !== 0)) {
        return error("REALITY nodes need VLESS, a valid SNI, server public key and an even-length hex shortId (up to 16 chars)");
      }
      await env.DB.prepare("UPDATE nodes SET name=?,address=?,port=?,country=?,protocol=?,network=?,security=?,sni=?,public_key=?,short_id=?,fingerprint=?,enabled=?,updated_at=? WHERE id=?")
        .bind(merged.name,merged.address,merged.port,merged.country,merged.protocol,merged.network,merged.security,merged.sni,merged.publicKey,merged.shortId,merged.fingerprint,Number(merged.enabled),now(),nodeMatch[1]).run();
      return json({ ok: true, node: { id: nodeMatch[1], ...merged } });
    }
    if (method === "GET" && path === "/api/v1/admin/users") {
      const result = await env.DB.prepare("SELECT * FROM users ORDER BY created_at DESC LIMIT 500").all();
      return json({ users: (result.results || []).map(cleanUser) });
    }
    const limits = /^\/api\/v1\/admin\/users\/([a-f0-9-]+)\/limits$/.exec(path);
    if (limits && method === "PATCH") {
      let body;
      try { body = await readJson(request); } catch (e) { return error(e.message); }
      const quota = body.quotaBytes == null ? 0 : Number(body.quotaBytes);
      const expiry = body.expiresAt == null ? null : Number(body.expiresAt);
      if (!Number.isSafeInteger(quota) || quota < 0 || (expiry !== null && (!Number.isInteger(expiry) || expiry <= 0))) return error("Invalid quotaBytes or expiresAt");
      const result = await env.DB.prepare("UPDATE users SET quota_bytes=?,expires_at=?,enabled=COALESCE(?,enabled) WHERE id=?")
        .bind(quota, expiry, typeof body.enabled === "boolean" ? Number(body.enabled) : null, limits[1]).run();
      return Number(result.meta?.changes || 0) ? json({ ok: true }) : error("User not found", 404);
    }
  }
  return error("Not found", 404);
}

export default {
  async fetch(request, env) {
    const cors = corsHeaders(request, env);
    try {
      return withCors(await handle(request, env), cors);
    } catch (e) {
      // Do not expose internal errors or credentials in production responses.
      return withCors(error("Internal server error", 500), cors);
    }
  },
};
