import bundled from "./blocklist.txt";

function lineAt(source, position) {
  let start = source.lastIndexOf("\n", position);
  start = start === -1 ? 0 : start + 1;
  let end = source.indexOf("\n", start);
  if (end === -1) end = source.length;
  return { start, end, value: source.slice(start, end) };
}

function sortedHas(source, needle) {
  let low = 0;
  let high = source.length;
  while (low < high) {
    const middle = (low + high) >> 1;
    const line = lineAt(source, middle);
    if (line.value === needle) return true;
    if (line.value < needle) {
      if (line.end >= high) return false;
      low = line.end + 1;
    } else {
      if (line.start <= low) return false;
      high = line.start - 1;
    }
  }
  return false;
}

export function* ancestors(name) {
  let host = name;
  yield host;
  let dot = host.indexOf(".");
  while (dot !== -1 && dot + 1 < host.length) {
    host = host.slice(dot + 1);
    if (!host.includes(".")) return;
    yield host;
    dot = host.indexOf(".");
  }
}

const DENIED_NAMES = new Set([
  "use-application-dns.net",
  "mask.icloud.com",
  "mask-h2.icloud.com",
  "mask-api.icloud.com",
  "resolver.arpa"
]);

export function decide(name, rules) {
  for (const host of ancestors(name)) {
    if (rules.allow.has(host)) return { action: "allow", rule: host, source: "allow" };
    if (DENIED_NAMES.has(host)) return { action: "block", rule: host, source: "bypass", rcode: 3 };
    if (rules.block.has(host)) return { action: "block", rule: host, source: "custom" };
  }
  for (const host of ancestors(name)) {
    if (sortedHas(bundled, host)) return { action: "block", rule: host, source: "list" };
  }
  return { action: "allow", rule: null, source: "none" };
}
