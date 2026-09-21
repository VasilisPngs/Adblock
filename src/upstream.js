const DOH_TIMEOUT = 2500;
const PENALTY_MS = 30000;
const PENALTY_MAX = 32;

const penalties = new Map();

export function parseResolver(value) {
  const trimmed = String(value || "").trim();
  if (!/^https:\/\//i.test(trimmed)) return null;
  try {
    return { target: new URL(trimmed).toString() };
  } catch {
    return null;
  }
}

function order(resolvers) {
  if (resolvers.length < 2) return resolvers;
  const now = Date.now();
  const up = resolvers.filter((resolver) => (penalties.get(resolver.target) || 0) <= now);
  return up.length > 0 ? up : resolvers;
}

function penalise(target) {
  if (penalties.size >= PENALTY_MAX) penalties.delete(penalties.keys().next().value);
  penalties.set(target, Date.now() + PENALTY_MS);
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
  const pool = order(resolvers);
  if (pool.length === 0) return { body: null, failure: "no_resolver" };
  let failure = "upstream_failed";
  const attempts = pool.map((resolver) =>
    ask(resolver, message).then(
      (body) => {
        penalties.delete(resolver.target);
        return body;
      },
      (error) => {
        penalise(resolver.target);
        failure = String(error && error.message).slice(0, 60);
        throw error;
      }
    )
  );
  try {
    return { body: await Promise.any(attempts), failure: null };
  } catch {
    return { body: null, failure };
  }
}
