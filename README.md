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
over the sorted text. The compiled list is not committed: every build, production and
preview alike, compiles a fresh one, so the repository never carries a stale copy.

The sources themselves are edited in the app, on the Protection tab, and stored in D1.
Every build reads them straight from D1 with the build's own Cloudflare credentials,
writes them back into `blocklists.json`, and recompiles; with no source left the list is
empty and only your own rules apply. With a Cloudflare deploy hook
stored in D1 (set once, see Setup), the Worker's own cron fires a build twice a day, and a
change to the sources fires one immediately. Nothing has to be pressed, and the app has
no rebuild button. The schedule is deliberately not tighter than that: every deploy replaces every
isolate, and with it the in-memory answer cache, so a rebuild that gains a few hours of
list freshness costs every cached answer. The hook is stored in D1 and never sent back to
the browser. A source added in the app
therefore takes effect at the next build, which the app says plainly rather than
pretending the change is live.

## Setup

1. `npm install`
2. The database id in `wrangler.jsonc` points at the `adblock` D1 database.
3. Workers Builds on this repository runs `npm run deploy`: lists, migrations, Worker.
   Create a deploy hook under Worker → Settings → Builds → Deploy Hooks and store it once:
   `npx wrangler d1 execute DB --remote --command "UPDATE settings SET deploy_hook = '<hook URL>'"`.
4. Protect the dashboard in Zero Trust (Free plan) with two self-hosted applications on
   `adblock.<subdomain>.workers.dev`, the public one first so DNS never stops:
   - `Adblock public`: paths `dns-query` and `icons`, its own Bypass policy `Adblock public`
     for Everyone. The icons are open because iOS fetches the home-screen icon without the
     Access cookie.
   - `Adblock`: the whole hostname, its own Allow policy `Adblock` (the owner's email
     address), session of one month. No policy is shared with the other apps.
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

Every device gets its own DoH URL, and anything that can use DNS over HTTPS with a custom
URL can use it; nothing in the resolver depends on the kind of client. The rows below are
setup notes for common clients, not a list of what is allowed.

| Client | How |
| --- | --- |
| Any DoH client | Paste the device URL: browsers with a custom DoH setting (Chrome, Edge, Firefox), DoH apps, and routers or forwarders that send their upstream queries over DoH, which also covers everything behind them. |
| iPhone / iPad / Mac | Optionally install the generated `.mobileconfig`. System-wide, works on mobile data. It excludes `captive.apple.com` and `3gppnetwork.org` from encrypted DNS so hotel and airport sign-in pages still appear and Wi-Fi calling keeps working, and its identifiers are derived from the device token, so downloading it again replaces the profile instead of adding a second one. **Apple profile (27+)** delivers the same settings as Apple's declarative `com.apple.configuration.network.dns-settings` configuration inside a `com.apple.declarations` payload, which installs from Settings without MDM on iOS, iPadOS, macOS and visionOS 27 and later; it keeps the profile identifier, so either profile replaces the other. |
| Windows 11 | `netsh dns add encryption server=<ip> dohtemplate=<url> autoupgrade=yes udpfallback=no`, then set the adapter's DNS to that IP. |
| Android | Private DNS only speaks DoT for custom hostnames, so it cannot use this endpoint. Use a DoH client app, or a router that forwards over DoH. |
| Clients that only speak plain DNS or DoT | They cannot reach the Worker directly, because Workers only serve HTTPS. Point them at a router or forwarder that sends its queries over DoH. |

The device address is the credential, so the app shows it masked and reveals it on
request. Copy puts the full address on the clipboard without ever putting it on screen.

## Upstream resolver

One DoH URL, `https://cloudflare-dns.com/dns-query` by default, changeable on the
Protection tab. Every query that is not blocked or answered from the cache is sent there
once: no second resolver, no hedged request, no retry. If it fails or has not answered
within 2.5 s the device gets SERVFAIL and asks again on its own, and the failure is
written to Workers Logs with the name, type and reason. A new URL is saved only
after it answers a test query for `example.com`, so a typo cannot cut every device off,
including the phone you would fix it from. A name you allow yourself is never blocked
by the CNAME check either: your rule wins over the list, wherever the answer points.

Every query reaches the upstream in one shape: EDNS version 0, the client's DO and CD
bits, and no options. The client subnet, NSID, cookies, padding and anything else a
client adds are options the Worker does not implement, and RFC 6891 lets a responder
ignore those; a query for any EDNS version other than 0 gets BADVERS without leaving
the Worker. The cache holds DNS data only, keyed by question, DO and CD, never the OPT
record (RFC 6891 forbids caching it). Each response gets its own OPT, built from the
query that asked: none for a client without EDNS, the DO bit echoed, Extended DNS Errors
only on answers fresh from the upstream, and padding to 468-byte blocks (RFC 8467), up
to the 65,535-byte message limit, for clients that ask for it. One cached answer can
therefore serve every client correctly, whatever it sends.

If D1 cannot be read and the Worker has no settings in memory yet, DNS keeps resolving
and blocking with the bundled list, without your own rules and without logging, and D1
is tried again every 5 seconds. Blocking stays on; the device check is skipped until D1
answers again, because refusing every token would leave your own devices without DNS.

An upstream that is not an `https://` URL is refused: plain DNS on port 53 is
unencrypted, which is the one thing this resolver exists to avoid.

## Usage counts

Every DNS request is counted per device and hour: total, answered after waiting for the
upstream (`waited_upstream`), answered from the cache, and blocked. A stale answer served
from the cache counts as cached even though it refreshes the entry in the background, so
the total upstream traffic is the Worker's subrequest count in Cloudflare analytics, not
this column. With the activity log on, the Worker also
counts names, and every five minutes writes the ten most asked names per device and hour
together with the totals, adding to what other isolates wrote. Counting happens in memory
and D1 sees one write batch per isolate every five minutes, instead of a write per query,
which a burst of thousands of queries a minute would turn into the D1 free write quota.
An isolate that is shut down loses its last few minutes of counts, so the numbers are a
close floor, not an exact total. Rows are kept for 7 days and pruned by the cron. The
counts show which names a device asks for over and over, not which program on the device
asks: every program on a device shares its token.

## Toolchain

Zero runtime dependencies. Build tooling stays on the latest stable release: wrangler
pinned to an exact version in `package.json`, Node on the Active LTS line in `.nvmrc`.

Clock times are always rendered on a 24-hour cycle (`hourCycle: "h23"`), in every
language, regardless of what the locale would pick by default.
