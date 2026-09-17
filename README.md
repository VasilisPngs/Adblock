# Adblock

DNS-level ad and tracker blocking on Cloudflare Workers. The Worker is the resolver:
it answers DNS-over-HTTPS, blocks what is on the lists, and forwards everything else
to whichever upstream resolvers you type in yourself.

## How it works

```
device ──DoH──▶ Worker ──▶ blocklist lookup (in memory, bundled)
                  │
                  ├─ blocked → 0.0.0.0 / :: / NXDOMAIN, never leaves Cloudflare
                  └─ allowed → your upstream resolver (DoH URL or plain IP over TCP 53)
```

Blocklists are compiled at build time, not at runtime: the Workers free plan allows
10 ms CPU per invocation, which is nowhere near enough to download and parse a
180 000-line filter, but is 6 000× more than a lookup needs. `npm run lists` reads the
URLs in `blocklists.json`, merges and sorts them, and writes `src/blocklist.txt`, which
is bundled into the Worker and searched with a binary search over the sorted text.

## Setup

1. `npm install`
2. Create the database and put its id in `wrangler.jsonc`:
   `npx wrangler d1 create adblock`
3. `npm run deploy` — builds the lists, applies migrations, deploys.
4. Put the app behind Cloudflare Access, then add a second Access application for the
   path `/dns-query` with a **Bypass** policy. Devices cannot log in through a browser,
   so the DoH endpoint is protected by the secret token in its path instead.
5. Open the app and add a device to get its DoH URL and profile. It already resolves
   through `https://cloudflare-dns.com/dns-query`; change or extend that list in Settings
   whenever you want.

## Devices

| Device | How |
| --- | --- |
| iPhone / iPad / Mac | Install the generated `.mobileconfig`. System-wide, works on mobile data. |
| Windows 11 | `netsh dns add encryption server=<ip> dohtemplate=<url> autoupgrade=yes udpfallback=no`, then set the adapter's DNS to that IP. |
| Android | Private DNS only speaks DoT for custom hostnames, so it cannot use this endpoint. Use any DoH client app and paste the URL. |
| Android TV, consoles, routers | Not supported: they need plain DNS on port 53, which Workers cannot serve. |

## Upstream resolvers

`https://cloudflare-dns.com/dns-query` ships as the default so the resolver works the
moment it is deployed. Replace it with anything you type, in order, first one that
answers wins:

- a DoH URL, for example `https://dns.example.net/dns-query`
- a plain IPv4 or IPv6 address, queried over TCP port 53

Workers block outbound TCP to Cloudflare's own IP ranges, so Cloudflare's resolver has
to be given as its DoH URL rather than as `1.1.1.1`. The app rejects such an address
with a clear error instead of failing silently.

## Toolchain

Zero runtime dependencies. Build tooling stays on the latest stable release: wrangler
pinned to an exact version in `package.json`, Node on the Active LTS line in `.nvmrc`.

Clock times are always rendered on a 24-hour cycle (`hourCycle: "h23"`), in every
language, regardless of what the locale would pick by default.
