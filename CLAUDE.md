# adblock

## Upstream resolver

The upstream DoH resolver is `https://cloudflare-dns.com/dns-query` and nothing else.

Never add, replace or suggest another resolver. Not as a second entry, not as a
fallback, not as a default in code, not as a recommendation in a report. If the
upstream path needs to be more robust, make it more robust against Cloudflare
alone — the request is hedged for exactly that reason.

## Blocklist

The configured source is whatever the `sources` table holds, and it is not to be
changed or swapped for another list without being asked. `src/blocklist.txt` is a
build artefact: every deploy regenerates it from that source, so never hand-edit
it and never treat a diff in it as a change of policy.
