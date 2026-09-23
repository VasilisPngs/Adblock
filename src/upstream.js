const DOH_TIMEOUT = 2500;
const label = (text) => [text.length, ...new TextEncoder().encode(text)];
const PROBE = Uint8Array.from([0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, ...label("example"), ...label("com"), 0, 0, 1, 0, 1]);

export function parseResolver(value) {
  const trimmed = String(value || "").trim();
  if (!/^https:\/\//i.test(trimmed)) return null;
  try {
    return new URL(trimmed).toString();
  } catch {
    return null;
  }
}

export async function resolve(resolver, message) {
  try {
    const response = await fetch(resolver, {
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
    return { body, failure: null };
  } catch (error) {
    return { body: null, failure: String(error && error.message).slice(0, 60) };
  }
}

export async function probe(resolver) {
  const { body } = await resolve(resolver, PROBE);
  return Boolean(body && body[2] & 0x80 && (body[3] & 0x0f) === 0);
}
