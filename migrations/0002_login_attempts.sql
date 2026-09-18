CREATE TABLE login_attempts (at INTEGER NOT NULL, ip TEXT NOT NULL);
CREATE INDEX idx_login_attempts_ip_at ON login_attempts (ip, at);
