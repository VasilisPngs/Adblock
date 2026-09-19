CREATE TABLE totals (action TEXT PRIMARY KEY, total INTEGER NOT NULL);

INSERT INTO totals (action, total) SELECT action, SUM(total) FROM counters GROUP BY action;

CREATE TABLE blocked_totals (name TEXT PRIMARY KEY, total INTEGER NOT NULL);
CREATE INDEX blocked_totals_total ON blocked_totals (total DESC);

INSERT INTO blocked_totals (name, total)
SELECT name, COUNT(*) FROM queries WHERE action = 'block' GROUP BY name;

DROP TABLE counters;
