import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

test("health endpoint checks D1 and returns service status", async () => {
  const env = {
    APP_SECRET: "a".repeat(48),
    DB: { prepare: () => ({ first: async () => ({ ok: 1 }) }) },
  };
  const response = await worker.fetch(new Request("https://unit.test/api/v1/health"), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, service: "spd-con-api" });
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("serves the responsive web panel without requiring a session", async () => {
  const response = await worker.fetch(new Request("https://unit.test/panel"), {
    APP_SECRET: "a".repeat(48),
    DB: { prepare: () => ({ first: async () => ({ ok: 1 }) }) },
  });
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") || "", /text\/html/);
  const html = await response.text();
  assert.match(html, /پنل آنلاین ساده/);
  assert.match(html, /data-tab="configs"/);
  assert.match(html, /option value="hysteria2"/);
  assert.match(html, /option value="tuic"/);
  assert.match(html, /hysteria2:\/\//);
  assert.match(html, /tuic:\/\//);
  assert.match(html, /رمز مستقل TUIC/);
  assert.match(html, /tab-nodes/);
  assert.match(html, /nodePbk/);
  assert.match(html, /تنظیمات نود انتخاب‌شده اعمال شد/);
});

test("refuses to run without a strong app secret", async () => {
  const response = await worker.fetch(new Request("https://unit.test/api/v1/health"), {
    APP_SECRET: "too-short",
    DB: { prepare: () => ({ first: async () => ({ ok: 1 }) }) },
  });
  assert.equal(response.status, 500);
  assert.deepEqual(await response.json(), { error: "Server configuration incomplete" });
});

test("protected API routes require a bearer session", async () => {
  const response = await worker.fetch(new Request("https://unit.test/api/v1/configs"), {
    APP_SECRET: "b".repeat(48),
    DB: { prepare: () => ({ first: async () => null }) },
  });
  assert.equal(response.status, 401);
  assert.deepEqual(await response.json(), { error: "Authentication required" });
});

test("admin node registry is protected and returns the stored node list", async () => {
  const admin = { id: "admin-id", username: "admin", role: "admin", enabled: 1, quota_bytes: 0, used_bytes: 0, expires_at: null, created_at: 1 };
  const env = {
    APP_SECRET: "c".repeat(48),
    DB: {
      prepare: (sql) => ({
        bind: (...args) => ({ sql, args, first: async () => sql.includes("SELECT u.* FROM sessions") ? admin : null, all: async () => ({ results: [] }), run: async () => ({ meta: { changes: 1 } }) }),
        first: async () => ({ ok: 1 }),
        all: async () => ({ results: [] }),
      }),
    },
  };
  const unauthorized = await worker.fetch(new Request("https://unit.test/api/v1/admin/nodes"), env);
  assert.equal(unauthorized.status, 401);
  const authorized = await worker.fetch(new Request("https://unit.test/api/v1/admin/nodes", {
    headers: { authorization: "Bearer " + "d".repeat(64) },
  }), env);
  assert.equal(authorized.status, 200);
  assert.deepEqual(await authorized.json(), { nodes: [] });
});

test("REALITY node creation refuses invented or missing server parameters", async () => {
  const admin = { id: "admin-id", username: "admin", role: "admin", enabled: 1, quota_bytes: 0, used_bytes: 0, expires_at: null, created_at: 1 };
  const env = {
    APP_SECRET: "e".repeat(48),
    DB: {
      prepare: (sql) => ({
        bind: (...args) => ({ sql, args, first: async () => sql.includes("SELECT u.* FROM sessions") ? admin : null, all: async () => ({ results: [] }), run: async () => ({ meta: { changes: 1 } }) }),
        first: async () => ({ ok: 1 }),
        all: async () => ({ results: [] }),
      }),
    },
  };
  const response = await worker.fetch(new Request("https://unit.test/api/v1/admin/nodes", {
    method: "POST",
    headers: { authorization: "Bearer " + "f".repeat(64), "content-type": "application/json" },
    body: JSON.stringify({ name: "Reality", address: "node.example.com", port: 443, protocol: "vless", network: "tcp", security: "reality", sni: "www.example.com", publicKey: "", shortId: "" }),
  }), env);
  assert.equal(response.status, 400);
  assert.match((await response.json()).error, /REALITY nodes need VLESS/);
});
