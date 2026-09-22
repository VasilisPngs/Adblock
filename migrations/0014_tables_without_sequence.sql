CREATE TABLE queries_new (
  id INTEGER PRIMARY KEY,
  at INTEGER NOT NULL,
  token TEXT,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  action TEXT NOT NULL,
  source TEXT NOT NULL,
  rule TEXT,
  ms INTEGER
);
INSERT INTO queries_new (id, at, token, name, type, action, source, rule, ms)
  SELECT id, at, token, name, type, action, source, rule, ms FROM queries
  WHERE at >= strftime('%s', 'now') * 1000 - 86400000;
DROP TABLE queries;
ALTER TABLE queries_new RENAME TO queries;
CREATE TABLE errors_new (
  id INTEGER PRIMARY KEY,
  at INTEGER NOT NULL,
  kind TEXT NOT NULL,
  message TEXT NOT NULL,
  stack TEXT,
  route TEXT,
  agent TEXT
);
INSERT INTO errors_new (id, at, kind, message, stack, route, agent)
  SELECT id, at, kind, message, stack, route, agent FROM errors;
DROP TABLE errors;
ALTER TABLE errors_new RENAME TO errors;
CREATE INDEX errors_at ON errors (at);
