# Adblock

DNS-level ad and tracker blocking on Cloudflare Workers. The Worker is the resolver:
it answers DNS-over-HTTPS, blocks what is on the lists, and forwards everything else
to the one upstream DoH resolver you choose.

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
Every build reads them straight from D1 with the build's own Cloudflare credentials,
writes them back into `blocklists.json`, and recompiles. With a Cloudflare deploy hook saved in the app, the Worker's own cron fires
a build twice a day, and a change to the sources fires one immediately. Nothing has to be
pressed. The schedule is deliberately not tighter than that: every deploy replaces every
isolate, and with it the in-memory answer cache, so a rebuild that gains a few hours of
list freshness costs every cached answer. The hook is stored in D1 and never sent back to
the browser. A source added in the app
therefore takes effect at the next build, which the app says plainly rather than
pretending the change is live.

## Setup

1. `npm install`
2. The database id in `wrangler.jsonc` points at the `adblock` D1 database.
3. Workers Builds on this repository runs `npm run deploy`: lists, migrations, Worker.
4. Protect the dashboard in Zero Trust (Free plan) with two self-hosted applications on
   `adblock.<subdomain>.workers.dev`, the public one first so DNS never stops:
   - `Adblock public`: path `dns-query`, policy Bypass, Everyone.
   - `Adblock`: the whole hostname, the same Allow policy as the reader and GymTracker.
     Put its Application Audience (AUD) tag in `ACCESS_AUD` in `wrangler.jsonc`.
   Until then the Worker refuses every API request, because it only trusts requests
   carrying a valid Access token for that audience.
5. Open `https://adblock.<subdomain>.workers.dev`, sign in through Access, and add a
   device to get its DoH URL and profile. It already
   resolves through `https://cloudflare-dns.com/dns-query`; change it on the Protection
   tab whenever you want.

`npm run lists` rebuilds the list by hand; without Cloudflare credentials it compiles the
sources already in `blocklists.json`.

## Who can reach what

| Path | Who |
| --- | --- |
| `/dns-query/<device token>` | open by design: DoH clients cannot log in. The 32-character token is the credential, and a request without a known token is refused, so the resolver cannot be used by strangers. |
| everything else | Cloudflare Access. The Worker also verifies the Access token itself and refuses every API call without one, so a misconfigured Access application fails closed. |

The open path needs a hostname-level Access application with a Bypass policy, because
Worker-level Access covers the whole Worker with no path exceptions. The most specific
path wins, so the bypass applies to `/dns-query` and the rest of the hostname stays
behind Access.

## Devices

| Device | How |
| --- | --- |
| iPhone / iPad / Mac | Install the generated `.mobileconfig`. System-wide, works on mobile data. It excludes `captive.apple.com` and `3gppnetwork.org` from encrypted DNS so hotel and airport sign-in pages still appear and Wi-Fi calling keeps working, and its identifiers are derived from the device token, so downloading it again replaces the profile instead of adding a second one. |
| Windows 11 | `netsh dns add encryption server=<ip> dohtemplate=<url> autoupgrade=yes udpfallback=no`, then set the adapter's DNS to that IP. |
| Android | Private DNS only speaks DoT for custom hostnames, so it cannot use this endpoint. Use any DoH client app and paste the URL. |
| Android TV, consoles, routers | Not supported: they need plain DNS on port 53, which Workers cannot serve. |

The device address is the credential, so the app shows it masked and reveals it on
request. Copy puts the full address on the clipboard without ever putting it on screen.

## Upstream resolver

One DoH URL, `https://cloudflare-dns.com/dns-query` by default, changeable on the
Protection tab. Every query that is not blocked or answered from the cache is sent there
once: no second resolver, no hedged request, no retry. If it fails or has not answered
within 2.5 s the device gets SERVFAIL and asks again on its own. A new URL is saved only
after it answers a test query for `example.com`, so a typo cannot cut every device off,
including the phone you would fix it from. A name you allow yourself is never blocked
by the CNAME check either: your rule wins over the list, wherever the answer points.

Plain DNS on port 53 is not offered. It is unencrypted, which is the one thing this
resolver exists to avoid, and it costs a fresh TCP handshake on every query that cannot
be reused between requests. A resolver that is not an `https://` URL is refused.

## Toolchain

Zero runtime dependencies. Build tooling stays on the latest stable release: wrangler
pinned to an exact version in `package.json`, Node on the Active LTS line in `.nvmrc`.

Clock times are always rendered on a 24-hour cycle (`hourCycle: "h23"`), in every
language, regardless of what the locale would pick by default.
