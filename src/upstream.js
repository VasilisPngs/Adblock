const DOH_TIMEOUT = 8000;

export function parseResolver(value) {
  const trimmed = String(value || "").trim();
  if (!/^https:\/\//i.test(trimmed)) return null;
  try {
    return { target: new URL(trimmed).toString() };
  } catch {
    return null;
  }
}

export async function resolve(resolver, message) {
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
  return new Uint8Array(await response.arrayBuffer());
}
