# Adblock

DNS-level ad and tracker blocking on Cloudflare Workers. The Worker is the resolver:
it answers DNS-over-HTTPS, blocks what is on the lists, and forwards everything else
to the upstream resolver you choose, with an optional second one as a fallback.

## How it works

```
device ──DoH──▶ Worker ──▶ blocklist lookup (in memory, bundled)
                  │
                  ├─ blocked → 0.0.0.0 / :: / NODATA, never leaves Cloudflare
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
3. Two Workers Builds connections on this repository, because a build always deploys to
   the Worker it is connected to: `adblock` runs `npm run deploy` (lists, migrations,
   resolver) and `adblock-app` runs `npm run deploy:app` (dashboard).
4. Protect the dashboard: Workers & Pages → `adblock-app` → Access → Protect this Worker
   behind Access → All traffic → your policy → Apply Access. Until then the dashboard
   refuses every API request, because the Worker only trusts requests Access authenticated.
5. Open `https://adblock-app.<subdomain>.workers.dev`, sign in through Access, and add a
   device to get its DoH URL and profile. It already
   resolves through `https://cloudflare-dns.com/dns-query`; change it or add a second
   resolver on the Protection tab whenever you want.

A GitHub Action rebuilds the lists every night and commits them when they changed,
which makes Workers Builds deploy the fresh list. `npm run lists` does the same by hand.

## Who can reach what

One codebase and one D1 database, deployed as two Workers chosen by the `ROLE` variable.

| Worker | Path | Who |
| --- | --- | --- |
| `adblock` | `/dns-query/<device token>` | open by design: DoH clients cannot log in. The 32-character token is the credential, and a request without a known token is refused, so the resolver cannot be used by strangers. |
| `adblock` | `GET /api/sources` | open: it lists the blocklist URLs, which the nightly build reads. |
| `adblock` | everything else | redirected to the dashboard. |
| `adblock-app` | everything | Cloudflare Access on the whole Worker, the same one-click protection as the reader and GymTracker. The Worker also refuses every API call that Access did not authenticate. |

The resolver and the dashboard are separate Workers because Worker-level Access covers the
whole Worker with no path exceptions, and leaving one path public needs hostname-level
Access, which requires a Zero Trust plan with payment details on file. The resolver reads
settings, rules and devices from D1 at most once a minute, so a change made in the
dashboard reaches DNS within a minute.

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
moment it is deployed. Up to two DoH URLs are accepted. Every query goes to the first;
if it fails or has not answered within 150 ms the second is asked as well, and whichever
answers first wins. With one resolver the second request goes to the same one.

Plain DNS on port 53 is not offered. It is unencrypted, which is the one thing this
resolver exists to avoid, and it costs a fresh TCP handshake on every query that cannot
be reused between requests. A resolver that is not an `https://` URL is refused.

## Toolchain

Zero runtime dependencies. Build tooling stays on the latest stable release: wrangler
pinned to an exact version in `package.json`, Node on the Active LTS line in `.nvmrc`.

Clock times are always rendered on a 24-hour cycle (`hourCycle: "h23"`), in every
language, regardless of what the locale would pick by default.
