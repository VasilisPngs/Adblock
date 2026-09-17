import { connect } from "cloudflare:sockets";

const CLOUDFLARE_V4 = [
  [0x67f00000, 0xfffc0000],
  [0x68100000, 0xfffe0000],
  [0x6c300000, 0xffff0000],
  [0x681a0000, 0xffff0000],
  [0xc629d000, 0xfffff000],
  [0xc7b01800, 0xfffffc00],
  [0xbc726400, 0xfffffc00],
  [0xbe5df000, 0xfffff000],
  [0xc0e1c800, 0xfffffc00],
  [0xc1000000, 0xffffff00],
  [0xc1ffec00, 0xfffffe00],
  [0xc2ad9200, 0xffffff00],
  [0xc4162000, 0xfffff800],
  [0x6812ff00, 0xffffff00],
  [0x01010101, 0xffffffff],
  [0x01000001, 0xffffffff]
];

const TCP_TIMEOUT = 5000;
const DOH_TIMEOUT = 8000;

function toLong(address) {
  const parts = address.split(".");
  if (parts.length !== 4) return null;
  let value = 0;
  for (const part of parts) {
    const octet = Number(part);
    if (!Number.isInteger(octet) || octet < 0 || octet > 255) return null;
    value = (value << 8) | octet;
  }
  return value >>> 0;
}

export function isCloudflareAddress(address) {
  const value = toLong(address);
  if (value === null) return false;
  return CLOUDFLARE_V4.some(([network, mask]) => (value & mask) >>> 0 === network >>> 0);
}

export function parseResolver(value) {
  const trimmed = String(value || "").trim();
  if (!trimmed) return null;
  if (/^https:\/\//i.test(trimmed)) {
    try {
      return { kind: "doh", target: new URL(trimmed).toString() };
    } catch {
      return null;
    }
  }
  const bare = trimmed.replace(/^\[|\]$/g, "");
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(bare) && toLong(bare) !== null) {
    return { kind: "tcp", target: bare, family: 4 };
  }
  if (/^[0-9a-f:]+$/i.test(bare) && bare.includes(":")) {
    return { kind: "tcp", target: bare, family: 6 };
  }
  return null;
}

async function askDoh(target, message) {
  const response = await fetch(target, {
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

async function askTcp(resolver, message) {
  if (resolver.family === 4 && isCloudflareAddress(resolver.target)) throw new Error("cloudflare_ip_needs_doh");
  const address = resolver.family === 6 ? `[${resolver.target}]:53` : `${resolver.target}:53`;
  const socket = connect(address, { secureTransport: "off", allowHalfOpen: false });
  try {
    const writer = socket.writable.getWriter();
    const framed = new Uint8Array(message.length + 2);
    framed[0] = (message.length >> 8) & 0xff;
    framed[1] = message.length & 0xff;
    framed.set(message, 2);
    await writer.write(framed);
    writer.releaseLock();

    const reader = socket.readable.getReader();
    const chunks = [];
    let received = 0;
    let expected = null;
    const deadline = Date.now() + TCP_TIMEOUT;
    while (Date.now() < deadline) {
      const { value, done } = await reader.read();
      if (done) break;
      chunks.push(value);
      received += value.length;
      if (expected === null && received >= 2) {
        const head = chunks[0];
        expected = head.length >= 2 ? (head[0] << 8) | head[1] : null;
      }
      if (expected !== null && received >= expected + 2) break;
    }
    reader.releaseLock();
    if (expected === null) throw new Error("upstream_empty");
    const merged = new Uint8Array(received);
    let offset = 0;
    for (const chunk of chunks) {
      merged.set(chunk, offset);
      offset += chunk.length;
    }
    return merged.subarray(2, 2 + expected);
  } finally {
    try {
      await socket.close();
    } catch {}
  }
}

export async function resolve(resolver, message) {
  if (resolver.kind === "doh") return askDoh(resolver.target, message);
  return askTcp(resolver, message);
}
