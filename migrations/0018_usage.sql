CREATE TABLE usage (
  hour INTEGER NOT NULL,
  token TEXT NOT NULL,
  requests INTEGER NOT NULL,
  upstream INTEGER NOT NULL,
  cached INTEGER NOT NULL,
  blocked INTEGER NOT NULL,
  PRIMARY KEY (hour, token)
) WITHOUT ROWID;

CREATE TABLE usage_names (
  hour INTEGER NOT NULL,
  token TEXT NOT NULL,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  count INTEGER NOT NULL,
  PRIMARY KEY (hour, token, name, type)
) WITHOUT ROWID;
