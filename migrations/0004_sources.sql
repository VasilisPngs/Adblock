CREATE TABLE sources (
  url TEXT PRIMARY KEY,
  name TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);

INSERT INTO sources (url, name, created_at)
VALUES ('https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt', 'AdGuard DNS filter', 0);
