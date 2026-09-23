const DOH_TIMEOUT = 2500;

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
