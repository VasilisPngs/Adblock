const RESOLVER = "https://cloudflare-dns.com/dns-query";
const DOH_TIMEOUT = 2500;
const HEDGE_MS = 150;

async function ask(message) {
  const response = await fetch(RESOLVER, {
    method: "POST",
    headers: {
      "content-type": "application/dns-message",
      accept: "application/dns-message"
    },
    body: message,
    signal: AbortSignal.timeout(DOH_TIMEOUT)
  });
  if (!response.ok) throw new Error(`upstream_http_${response.status}`);
  const body = new Uint8Array(await response.arrayBuffer());
  if (body.length < 12) throw new Error("upstream_short");
  return body;
}

export async function resolve(message) {
  let failure = "upstream_failed";

  const attempt = () =>
    ask(message).catch((error) => {
      failure = String(error && error.message).slice(0, 60);
      throw error;
    });

  const attempts = [attempt()];
  const early = await Promise.race([
    attempts[0].then((body) => body, () => null),
    new Promise((done) => setTimeout(() => done(null), HEDGE_MS))
  ]);
  if (early) return { body: early, failure: null };

  attempts.push(attempt());
  try {
    return { body: await Promise.any(attempts), failure: null };
  } catch {
    return { body: null, failure };
  }
}
