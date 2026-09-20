CREATE TABLE errors (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at INTEGER NOT NULL,
  kind TEXT NOT NULL,
  message TEXT NOT NULL,
  stack TEXT,
  route TEXT,
  agent TEXT
);

CREATE INDEX errors_at ON errors (at DESC);
