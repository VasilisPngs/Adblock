ALTER TABLE settings ADD COLUMN password_hash TEXT;
ALTER TABLE settings ADD COLUMN setup_code TEXT;
UPDATE settings SET setup_code = lower(hex(randomblob(8))) WHERE id = 1;
