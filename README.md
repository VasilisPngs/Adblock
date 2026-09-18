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
2. The database id in `wrangler.jsonc` points at the `adblock` D1 database.
3. `npm run deploy` — builds the lists, applies migrations, deploys.
4. Set the dashboard password: Worker → Settings → Variables and Secrets → add
   `DASHBOARD_PASSWORD` as a **Secret**. Until it is set, the dashboard refuses every
   request; there is no default and no way in without it.
5. Open the app, sign in, and add a device to get its DoH URL and profile. It already
   resolves through `https://cloudflare-dns.com/dns-query`; change or extend that list in
   Settings whenever you want.

A GitHub Action rebuilds the lists every night and commits them when they changed,
which makes Workers Builds deploy the fresh list. `npm run lists` does the same by hand.

## Who can reach what

| Path | Who |
| --- | --- |
| `/dns-query/<device token>` | open by design: DoH clients cannot log in. The 32-character token is the credential, and a request without a known token is refused, so the resolver cannot be used by strangers. |
| everything else | the dashboard password. A successful sign-in sets an HMAC-signed, `HttpOnly` `Secure` cookie for 30 days; changing the password invalidates every cookie ever issued. |

No Cloudflare Access, no Zero Trust, no third-party login: Access cannot exclude a single
path on a Worker, and its hostname-level policies need a Zero Trust plan with payment
details on file. The password check costs 0.2 ms of the 10 ms CPU budget and never runs
on a DNS query.

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
