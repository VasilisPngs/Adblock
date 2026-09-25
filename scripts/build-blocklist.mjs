import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const DOMAIN = /^[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)+$/;
const SIMPLE_MODIFIER = /^(important|all|dnsrewrite=[^,]*)$/;
const HOSTS_PREFIX = /^(0\.0\.0\.0|127\.0\.0\.1|::1?)\s+/;

function normalize(value) {
  const host = value.trim().toLowerCase().replace(/\.$/, "");
  return DOMAIN.test(host) ? host : null;
}

function parseList(text) {
  const block = new Set();
  const allow = new Set();
  let skipped = 0;
  for (const raw of text.split("\n")) {
    const line = raw.trim();
    if (!line || line.startsWith("!") || line.startsWith("#")) continue;
    if (line.includes("*") || line.startsWith("/") || line.includes("##")) {
      skipped += 1;
      continue;
    }
    const exception = line.startsWith("@@");
    let body = exception ? line.slice(2) : line;
    if (body.startsWith("||")) body = body.slice(2);
    const modifier = body.indexOf("$");
    if (modifier !== -1) {
      const options = body.slice(modifier + 1);
      body = body.slice(0, modifier);
      if (!SIMPLE_MODIFIER.test(options)) {
        skipped += 1;
        continue;
      }
    }
    body = body.replace(/\^$/, "");
    if (HOSTS_PREFIX.test(body)) body = body.split(/\s+/)[1] || "";
    const host = normalize(body);
    if (!host) {
      skipped += 1;
      continue;
    }
    (exception ? allow : block).add(host);
  }
  return { block, allow, skipped };
}

function collapse(hosts) {
  const sorted = [...hosts].sort();
  const kept = [];
  const known = new Set(hosts);
  for (const host of sorted) {
    let parent = host;
    let covered = false;
    let dot = parent.indexOf(".");
    while (dot !== -1) {
      parent = parent.slice(dot + 1);
      if (!parent.includes(".")) break;
      if (known.has(parent)) {
        covered = true;
        break;
      }
      dot = parent.indexOf(".");
    }
    if (!covered) kept.push(host);
  }
  return kept;
}

function storedSources() {
  const output = execFileSync(
    "npx",
    ["wrangler", "d1", "execute", "DB", "--remote", "--json", "--command", "SELECT url, name FROM sources ORDER BY created_at, url"],
    { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"], timeout: 120000 }
  );
  return (JSON.parse(output)[0]?.results || []).filter((source) => typeof source.url === "string");
}

function configuredSources() {
  const config = JSON.parse(readFileSync(join(root, "blocklists.json"), "utf8"));
  try {
    const sources = storedSources();
    writeFileSync(join(root, "blocklists.json"), `${JSON.stringify({ sources }, null, 2)}\n`);
    console.log(`sources: ${sources.length} from D1`);
    return sources;
  } catch (error) {
    console.log(`sources: D1 unreachable (${String(error.message).split("\n")[0]}), keeping blocklists.json`);
  }
  return (config.sources || []).filter((source) => source.enabled !== false);
}

async function main() {
  const sources = configuredSources();

  const block = new Set();
  const allow = new Set();
  const report = [];

  for (const source of sources) {
    const response = await fetch(source.url, { headers: { "user-agent": "adblock-list-builder" } });
    if (!response.ok) throw new Error(`${source.url} -> HTTP ${response.status}`);
    const text = await response.text();
    const parsed = parseList(text);
    for (const host of parsed.block) block.add(host);
    for (const host of parsed.allow) allow.add(host);
    report.push({
      name: source.name || source.url,
      url: source.url,
      domains: parsed.block.size,
      exceptions: parsed.allow.size,
      skipped: parsed.skipped
    });
  }

  for (const host of allow) block.delete(host);
  const kept = collapse(block);
  const blob = kept.join("\n");
  writeFileSync(join(root, "src", "blocklist.txt"), blob);
  writeFileSync(
    join(root, "src", "blocklist-meta.json"),
    `${JSON.stringify({ builtAt: new Date().toISOString(), domains: kept.length, bytes: blob.length, sources: report }, null, 2)}\n`
  );

  for (const line of report) {
    console.log(`${line.name}: ${line.domains} domains, ${line.exceptions} exceptions, ${line.skipped} skipped`);
  }
  console.log(`merged: ${block.size} -> ${kept.length} after collapsing subdomains`);
  console.log(`written: src/blocklist.txt (${(blob.length / 1048576).toFixed(2)} MB)`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await main();
