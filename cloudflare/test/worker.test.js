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
