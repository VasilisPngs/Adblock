const encoder = new TextEncoder();

export const SESSION_COOKIE = "adblock_session";
export const SESSION_MAX_AGE = 30 * 24 * 60 * 60;

async function digest(value) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value)));
}

function equalBytes(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) diff |= a[i] ^ b[i];
  return diff === 0;
}

function base64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function readCookie(header, name) {
  if (!header) return null;
  for (const part of header.split(";")) {
    const split = part.indexOf("=");
    if (split < 0) continue;
    if (part.slice(0, split).trim() === name) return part.slice(split + 1).trim();
  }
  return null;
}

async function sign(secret, payload) {
  const key = await crypto.subtle.importKey(
    "raw",
    await digest(`${secret}|session`),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  return base64Url(new Uint8Array(await crypto.subtle.sign("HMAC", key, encoder.encode(payload))));
}

export async function checkPassword(password, secret) {
  if (!secret || !password) return false;
  const [given, expected] = await Promise.all([digest(password), digest(secret)]);
  return equalBytes(given, expected);
}

export async function issueSession(secret) {
  const payload = String(Date.now() + SESSION_MAX_AGE * 1000);
  return `${payload}.${await sign(secret, payload)}`;
}

export async function validSession(request, secret) {
  if (!secret) return false;
  const token = readCookie(request.headers.get("cookie"), SESSION_COOKIE);
  if (!token) return false;
  const split = token.lastIndexOf(".");
  if (split < 1) return false;
  const payload = token.slice(0, split);
  const expires = Number(payload);
  if (!Number.isFinite(expires) || expires <= Date.now()) return false;
  const expected = await sign(secret, payload);
  return equalBytes(encoder.encode(token.slice(split + 1)), encoder.encode(expected));
}

export function sessionCookie(token, maxAge = SESSION_MAX_AGE) {
  return `${SESSION_COOKIE}=${token}; Path=/; Max-Age=${maxAge}; HttpOnly; Secure; SameSite=Lax`;
}
