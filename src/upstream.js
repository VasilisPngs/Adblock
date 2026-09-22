const DOH_TIMEOUT = 2500;
const HEDGE_MS = 150;

export function parseResolver(value) {
  const trimmed = String(value || "").trim();
  if (!/^https:\/\//i.test(trimmed)) return null;
  try {
    return { target: new URL(trimmed).toString() };
  } catch {
    return null;
  }
}

async function ask(resolver, message) {
  const response = await fetch(resolver.target, {
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

export async function resolve(resolvers, message) {
  if (resolvers.length === 0) return { body: null, failure: "no_resolver" };
  const hedge = resolvers.length > 1 ? resolvers[1] : resolvers[0];
  let failure = "upstream_failed";

  const attempt = (resolver) =>
    ask(resolver, message).catch((error) => {
      failure = String(error && error.message).slice(0, 60);
      throw error;
    });

  const attempts = [attempt(resolvers[0])];
  const early = await Promise.race([
    attempts[0].then((body) => body, () => null),
    new Promise((done) => setTimeout(() => done(null), HEDGE_MS))
  ]);
  if (early) return { body: early, failure: null };

  attempts.push(attempt(hedge));
  try {
    return { body: await Promise.any(attempts), failure: null };
  } catch {
    return { body: null, failure };
  }
}
