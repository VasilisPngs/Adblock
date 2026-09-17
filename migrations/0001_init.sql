CREATE TABLE settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  enabled INTEGER NOT NULL DEFAULT 1,
  resolvers TEXT NOT NULL DEFAULT '["https://cloudflare-dns.com/dns-query"]',
  block_mode TEXT NOT NULL DEFAULT 'zero',
  log_enabled INTEGER NOT NULL DEFAULT 1,
  log_days INTEGER NOT NULL DEFAULT 7,
  updated_at INTEGER NOT NULL
);

INSERT INTO settings (id, enabled, resolvers, block_mode, log_enabled, log_days, updated_at)
VALUES (1, 1, '["https://cloudflare-dns.com/dns-query"]', 'zero', 1, 7, 0);

CREATE TABLE rules (
  host TEXT PRIMARY KEY,
  action TEXT NOT NULL CHECK (action IN ('allow', 'block')),
  created_at INTEGER NOT NULL
);

CREATE TABLE devices (
  token TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  last_seen_at INTEGER
);

CREATE TABLE queries (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at INTEGER NOT NULL,
  token TEXT,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  action TEXT NOT NULL,
  source TEXT NOT NULL,
  rule TEXT,
  ms INTEGER
);

CREATE INDEX queries_at ON queries (at DESC);
CREATE INDEX queries_name ON queries (name);
CREATE INDEX queries_action_at ON queries (action, at DESC);
