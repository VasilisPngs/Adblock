CREATE TABLE counters (
  hour INTEGER NOT NULL,
  action TEXT NOT NULL,
  total INTEGER NOT NULL,
  PRIMARY KEY (hour, action)
);
