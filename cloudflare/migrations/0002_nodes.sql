PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS nodes (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  address TEXT NOT NULL,
  port INTEGER NOT NULL CHECK(port BETWEEN 1 AND 65535),
  country TEXT NOT NULL DEFAULT 'auto',
  protocol TEXT NOT NULL CHECK(protocol IN ('vless','vmess','trojan','shadowsocks','hysteria2','tuic')),
  network TEXT NOT NULL DEFAULT 'tcp',
  security TEXT NOT NULL DEFAULT 'tls',
  sni TEXT NOT NULL DEFAULT '',
  public_key TEXT NOT NULL DEFAULT '',
  short_id TEXT NOT NULL DEFAULT '',
  fingerprint TEXT NOT NULL DEFAULT 'chrome',
  enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_nodes_enabled_country ON nodes(enabled, country);
