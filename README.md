# Adblock

DNS-level ad and tracker blocking on Cloudflare Workers. The Worker is the resolver:
it answers DNS-over-HTTPS, blocks what is on the lists, and forwards everything else
to whichever upstream resolvers you type in yourself.

## How it works

```
device ──DoH──▶ Worker ──▶ blocklist lookup (in memory, bundled)
                  │
                  ├─ blocked → 0.0.0.0 / :: / NXDOMAIN, never leaves Cloudflare
                  └─ allowed → your upstream resolver (DoH, encrypted)
```

Blocklists are compiled at build time, not at runtime: the Workers free plan allows
10 ms CPU per invocation — and the same 10 ms for a Cron Trigger — which is nowhere near
enough to download and parse a 180 000-line filter, but is 6 000× more than a lookup
needs. `npm run lists` reads the source URLs, merges and sorts them, and writes
`src/blocklist.txt`, which is bundled into the Worker and searched with a binary search
over the sorted text.

The sources themselves are edited in the app, on the Protection tab, and stored in D1.
Every build reads them from `GET /api/sources`, writes them back into `blocklists.json`,
and recompiles. With a Cloudflare deploy hook saved in the app, the Worker's own cron fires
a build twice a day, and a change to the sources fires one immediately. Nothing has to be
pressed. The schedule is deliberately not tighter than that: every deploy replaces every
isolate, and with it the in-memory answer cache, so a rebuild that gains a few hours of
list freshness costs every cached answer. The nightly GitHub Action is a third refresh
point. The hook is stored in D1 and never sent back to
the browser. That endpoint is the only unauthenticated read in the
app: it returns public blocklist URLs and nothing else. A source added in the app
therefore takes effect at the next build, which the app says plainly rather than
pretending the change is live.

## Setup

1. `npm install`
2. The database id in `wrangler.jsonc` points at the `adblock` D1 database.
3. `npm run deploy` — builds the lists, applies migrations, deploys.
4. Set the dashboard password on first run. Read the one-time setup code from the D1
   console with `SELECT setup_code FROM settings;`, open the app, and enter that code
   together with the password you want. The Worker stores only a salted PBKDF2-SHA256
   derivation of it and clears the setup code, so the screen cannot be used twice. Until a password exists the
   dashboard refuses every request, and the code is the only way to set one, so nobody
   who finds the URL first can claim it.
   A `DASHBOARD_PASSWORD` secret on the Worker still wins if one is set, which is the
   better place for it when the dashboard lets you save it.
5. Open the app, sign in, and add a device to get its DoH URL and profile. It already
   resolves through `https://cloudflare-dns.com/dns-query`; change or extend that list in
   Settings whenever you want.

A GitHub Action rebuilds the lists every night and commits them when they changed,
which makes Workers Builds deploy the fresh list. `npm run lists` does the same by hand.

## Who can reach what

| Path | Who |
| --- | --- |
| `/dns-query/<device token>` | open by design: DoH clients cannot log in. The 32-character token is the credential, and a request without a known token is refused, so the resolver cannot be used by strangers. |
| everything else | the dashboard password, at least 12 characters, stored as `pbkdf2$<iterations>$<salt>$<hash>` so a copy of the database cannot be run through a rainbow table. A hash written by an older version is re-derived silently on the next successful sign-in. The iteration count lives inside the record, so raising it needs no migration: 10,000 is what fits the free plan's 10 ms CPU budget with room for the two derivations a password change costs — measured in workerd at 1–2 ms each, against 14 ms for 100,000 and 29 ms for 210,000. A successful sign-in sets an HMAC-signed, `HttpOnly` `Secure` cookie for 30 days; the signing key is the stored record, so changing the password invalidates every cookie ever issued. Wrong guesses are counted per client IP, ten per ten minutes, which stops a script without letting anyone lock the owner out of their own dashboard. |

No Cloudflare Access, no Zero Trust, no third-party login: Access cannot exclude a single
path on a Worker, and its hostname-level policies need a Zero Trust plan with payment
details on file. The password check costs 0.2 ms of the 10 ms CPU budget and never runs
on a DNS query.

## Devices

| Device | How |
| --- | --- |
| iPhone / iPad / Mac | Install the generated `.mobileconfig`. System-wide, works on mobile data. It excludes `captive.apple.com` and `3gppnetwork.org` from encrypted DNS so hotel and airport sign-in pages still appear and Wi-Fi calling keeps working, and its identifiers are derived from the device token, so downloading it again replaces the profile instead of adding a second one. |
| Windows 11 | `netsh dns add encryption server=<ip> dohtemplate=<url> autoupgrade=yes udpfallback=no`, then set the adapter's DNS to that IP. |
| Android | Private DNS only speaks DoT for custom hostnames, so it cannot use this endpoint. Use any DoH client app and paste the URL. |
| Android TV, consoles, routers | Not supported: they need plain DNS on port 53, which Workers cannot serve. |

The device address is the credential, so the app shows it masked and reveals it on
request. Copy puts the full address on the clipboard without ever putting it on screen.

## Upstream resolvers

`https://cloudflare-dns.com/dns-query` ships as the default so the resolver works the
moment it is deployed. Replace it with any DoH URL you type, in order, first one that answers wins, for example
`https://dns.example.net/dns-query`.

Plain DNS on port 53 is not offered. It is unencrypted, which is the one thing this
resolver exists to avoid, and it costs a fresh TCP handshake on every query that cannot
be reused between requests. A resolver that is not an `https://` URL is refused.

## Toolchain

Zero runtime dependencies. Build tooling stays on the latest stable release: wrangler
pinned to an exact version in `package.json`, Node on the Active LTS line in `.nvmrc`.

Clock times are always rendered on a 24-hour cycle (`hourCycle: "h23"`), in every
language, regardless of what the locale would pick by default.
