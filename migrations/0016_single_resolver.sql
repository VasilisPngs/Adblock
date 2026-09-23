ALTER TABLE settings ADD COLUMN resolver TEXT NOT NULL DEFAULT 'https://cloudflare-dns.com/dns-query';
UPDATE settings SET resolver = coalesce(json_extract(resolvers, '$[0]'), resolver);
ALTER TABLE settings DROP COLUMN resolvers;
