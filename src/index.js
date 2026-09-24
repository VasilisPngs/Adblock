import {
  readQuestion,
  blockedResponse,
  servfail,
  base64UrlDecode,
  minimumTtl,
  cnameTargets,
  stripClientSubnet,
  boostTtl,
  decrementTtl,
  setTtl,
  QUERY_TYPES
} from "./dns.js";
import { decide } from "./blocklist.js";
import { parseResolver, probe, resolve } from "./upstream.js";
import { checkAccess } from "./access.js";
import meta from "./blocklist-meta.json";

const CACHE_TTL_MS = 60000;
const D1_RETRY_MS = 5000;
const BLOCK_TTL = 300;
const TTL_FLOOR = 300;
const TTL_CEILING = 3600;
const MAX_MESSAGE_BYTES = 4096;
const LOG_LIMIT = 200;
const LOG_RETENTION_MS = 86400000;
const DEFAULT_RESOLVER = "https://cloudflare-dns.com/dns-query";
const HOUR_MS = 3600000;
const SEEN_INTERVAL_MS = 300000;
const PRUNE_LIMIT = 20000;
const ERROR_RETENTION_MS = 30 * 86400000;

const ANSWER_CACHE_MAX = 4000;
const STALE_GRACE_MS = 60000;
const STALE_TTL = 30;

const answers = new Map();
const inflight = new Map();

const ttlOf = (body) => Math.min(Math.max(minimumTtl(body) || 0, TTL_FLOOR), TTL_CEILING);
const rcodeOf = (body) => body[3] & 0x0f;
const cacheable = (body) => rcodeOf(body) === 0 || rcodeOf(body) === 3;

function cacheKey(question) {
  return `${question.name}|${question.type}|${question.class}`;
}

function remember(key, body, ttl) {
  if (answers.size >= ANSWER_CACHE_MAX) answers.delete(answers.keys().next().value);
  answers.delete(key);
  answers.set(key, { body, storedAt: Date.now(), expires: Date.now() + ttl * 1000 });
}

function cloakedBy(body, settings, rules) {
  if (!settings.enabled) return null;
  for (const target of cnameTargets(body)) {
    const result = decide(target, rules);
    if (result.action === "block") return result;
  }
  return null;
}

function adopt(source, message, question) {
  const body = new Uint8Array(source);
  body[0] = message[0];
  body[1] = message[1];
  body.set(message.subarray(12, question.end), 12);
  return body;
}

function replay(entry, message, question, age) {
  const body = adopt(entry.body, message, question);
  return age === null ? setTtl(body, STALE_TTL) : decrementTtl(body, age);
}

function fetchUpstream(key, resolver, message) {
  const pending = inflight.get(key);
  if (pending) return pending;
  const attempt = resolve(resolver, message).finally(() => inflight.delete(key));
  inflight.set(key, attempt);
  return attempt;
}

const LOG_MEMORY = 20000;

const seen = new Map();
const logged = new Map();

function firstThisHour(key, hour) {
  if (logged.get(key) === hour) return false;
  if (logged.size >= LOG_MEMORY) logged.clear();
  logged.set(key, hour);
  return true;
}

let cache = { at: 0, settings: null, rules: null, tokens: null };
let d1DownUntil = 0;

const DEGRADED = {
  at: 0,
  settings: {
    enabled: true,
    resolver: DEFAULT_RESOLVER,
    logEnabled: false,
    deployHookSet: false
  },
  deployHook: null,
  rules: { allow: new Set(), block: new Set() },
  tokens: null
};

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });

const dnsResponse = (body, ttl) =>
  new Response(body, {
    headers: {
      "content-type": "application/dns-message",
      "cache-control": ttl > 0 ? `max-age=${ttl}` : "no-store",
      "content-length": String(body.length)
    }
  });

let stateRefresh = null;
let generation = 0;

async function readState(env) {
  const current = generation;
  const [settings, rules, devices] = await Promise.all([
    env.DB.prepare(
      "SELECT enabled, resolver, log_enabled, deploy_hook FROM settings WHERE id = 1"
    ).first(),
    env.DB.prepare("SELECT host, action FROM rules").all(),
    env.DB.prepare("SELECT token FROM devices").all()
  ]);
  const allow = new Set();
  const block = new Set();
  for (const row of rules.results || []) (row.action === "allow" ? allow : block).add(row.host);
  const next = {
    at: Date.now(),
    settings: {
      enabled: Boolean(settings?.enabled),
      resolver: settings?.resolver || DEFAULT_RESOLVER,
      logEnabled: Boolean(settings?.log_enabled),
      deployHookSet: Boolean(settings?.deploy_hook)
    },
    deployHook: settings?.deploy_hook || null,
    rules: { allow, block },
    tokens: new Set((devices.results || []).map((row) => row.token))
  };
  if (current === generation) cache = next;
  return next;
}

function loadState(env) {
  if (cache.settings && Date.now() - cache.at < CACHE_TTL_MS) return Promise.resolve(cache);
  return readState(env);
}

async function dnsState(env) {
  if (Date.now() < d1DownUntil) return cache.settings ? cache : DEGRADED;
  if (cache.settings && Date.now() - cache.at < CACHE_TTL_MS) return cache;
  if (!stateRefresh) {
    stateRefresh = readState(env)
      .catch(() => {
        d1DownUntil = Date.now() + D1_RETRY_MS;
        return null;
      })
      .finally(() => {
        stateRefresh = null;
      });
  }
  if (cache.settings) return cache;
  return (await stateRefresh) || DEGRADED;
}

function invalidate() {
  generation += 1;
  stateRefresh = null;
  cache = { at: 0, settings: null, rules: null, tokens: null };
  answers.clear();
}

async function readMessage(request, url) {
  if (request.method === "POST") {
    const buffer = await request.arrayBuffer();
    if (buffer.byteLength === 0 || buffer.byteLength > MAX_MESSAGE_BYTES) return null;
    return new Uint8Array(buffer);
  }
  const encoded = url.searchParams.get("dns");
  if (!encoded) return null;
  try {
    const bytes = base64UrlDecode(encoded);
    return bytes.length > 0 && bytes.length <= MAX_MESSAGE_BYTES ? bytes : null;
  } catch {
    return null;
  }
}

function logQuery(env, entry) {
  return env.DB.prepare(
    "INSERT INTO queries (at, token, name, type, action, source, rule, ms) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)"
  )
    .bind(entry.at, entry.token, entry.name, entry.type, entry.action, entry.source, entry.rule, entry.ms)
    .run()
    .catch(() => {});
}

async function handleDns(request, env, ctx, url, token) {
  const started = Date.now();
  const { settings, rules, tokens } = await dnsState(env);
  if (tokens && !tokens.has(token)) return json({ error: "unknown_device" }, 403);

  const message = await readMessage(request, url);
  if (!message) return json({ error: "bad_request" }, 400);
  const question = readQuestion(message);
  if (!question) return json({ error: "bad_query" }, 400);
  const type = QUERY_TYPES[question.type] || String(question.type);

  const verdict = settings.enabled ? decide(question.name, rules) : { action: "allow", rule: null, source: "off" };
  const allowed = verdict.source === "allow";
  const forward = stripClientSubnet(message, question);

  let body;
  let ttl = BLOCK_TTL;
  let failure = null;

  if (verdict.action === "block") {
    body = blockedResponse(message, question, BLOCK_TTL);
  } else {
    const key = cacheKey(question);
    const entry = answers.get(key);
    const usable = entry && entry.body.length >= question.end;

    const accept = (fresh) => {
      const cloaked = allowed ? null : cloakedBy(fresh, settings, rules);
      if (cloaked) return cloaked;
      const answer = boostTtl(new Uint8Array(fresh), TTL_FLOOR);
      const life = ttlOf(answer);
      remember(key, answer, life);
      return { answer, life };
    };

    if (usable && started < entry.expires) {
      body = replay(entry, message, question, Math.floor((started - entry.storedAt) / 1000));
      ttl = Math.max(1, Math.ceil((entry.expires - started) / 1000));
      verdict.source = "cache";
    } else if (usable && started < entry.expires + STALE_GRACE_MS) {
      body = replay(entry, message, question, null);
      ttl = STALE_TTL;
      verdict.source = "stale";
      ctx.waitUntil(
        fetchUpstream(key, settings.resolver, forward).then((fresh) => {
          if (!fresh.body || !cacheable(fresh.body)) return;
          if (accept(fresh.body).rule) answers.delete(key);
        })
      );
    } else {
      const fresh = await fetchUpstream(key, settings.resolver, forward);
      failure = fresh.failure;
      if (!fresh.body) {
        body = servfail(message);
        ttl = 0;
        console.error(JSON.stringify({ servfail: { name: question.name, type, failure, ms: Date.now() - started } }));
      } else if (!cacheable(fresh.body)) {
        body = fresh.body.length >= question.end ? adopt(fresh.body, message, question) : fresh.body;
        ttl = 0;
        console.error(JSON.stringify({ servfail: { name: question.name, type, failure: `upstream_rcode_${rcodeOf(fresh.body)}`, ms: Date.now() - started } }));
      } else {
        const outcome = accept(fresh.body);
        if (outcome.rule) {
          verdict.action = "block";
          verdict.rule = outcome.rule;
          verdict.source = "cname";
          body = blockedResponse(message, question, BLOCK_TTL);
          ttl = BLOCK_TTL;
        } else {
          ttl = outcome.life;
          body = outcome.answer.length >= question.end ? adopt(outcome.answer, message, question) : outcome.answer;
        }
      }
    }
  }

  const action = verdict.action;
  const hour = Math.floor(started / HOUR_MS);

  if (settings.logEnabled && !failure && firstThisHour(`${token}|${question.name}|${type}|${action}`, hour)) {
    ctx.waitUntil(
      logQuery(env, {
        at: started,
        token,
        name: question.name,
        type,
        action,
        source: verdict.source,
        rule: verdict.rule,
        ms: Date.now() - started
      })
    );
  }
  if (started - (seen.get(token) || 0) >= SEEN_INTERVAL_MS) {
    seen.set(token, started);
    ctx.waitUntil(
      env.DB.prepare("UPDATE devices SET last_seen_at = ?2 WHERE token = ?1").bind(token, started).run().catch(() => {})
    );
  }

  return dnsResponse(body, ttl);
}

const REPORT_WINDOW_MS = 60000;
const REPORT_LIMIT = 20;
let reportWindow = 0;
let reportCount = 0;

function recordError(env, entry) {
  return env.DB.prepare("INSERT INTO errors (at, kind, message, stack, route, agent) VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
    .bind(
      Date.now(),
      String(entry.kind || "error").slice(0, 20),
      String(entry.message || "").slice(0, 300),
      String(entry.stack || "").slice(0, 1000) || null,
      String(entry.route || "").slice(0, 120) || null,
      String(entry.agent || "").slice(0, 200) || null
    )
    .run()
    .catch(() => {});
}

async function handleReport(request, env) {
  const now = Date.now();
  if (now - reportWindow > REPORT_WINDOW_MS) {
    reportWindow = now;
    reportCount = 0;
  }
  if (reportCount >= REPORT_LIMIT) return json({ ok: true });
  reportCount += 1;
  const payload = await request.json().catch(() => null);
  const message = payload && typeof payload.message === "string" ? payload.message.trim() : "";
  if (!message) return json({ ok: true });
  await recordError(env, { ...payload, message, agent: request.headers.get("user-agent") });
  return json({ ok: true });
}

async function handleState(request, env) {
  const { settings } = await loadState(env);
  const [devices, sources] = await Promise.all([
    env.DB.prepare("SELECT token, name, platform, created_at, last_seen_at FROM devices ORDER BY created_at").all(),
    listSources(env)
  ]);
  return json({
    settings,
    list: { builtAt: meta.builtAt, compiled: meta.sources, sources: sources.results || [] },
    devices: devices.results || [],
    host: new URL(request.url).host
  });
}

async function handleSettings(request, env) {
  const payload = await request.json().catch(() => null);
  if (!payload) return json({ error: "invalid_json" }, 400);
  const current = (await loadState(env)).settings;
  const resolver = payload.resolver === undefined ? current.resolver : parseResolver(payload.resolver);
  if (!resolver) return json({ error: "invalid_resolver" }, 400);
  if (resolver !== current.resolver && !(await probe(resolver))) return json({ error: "resolver_unreachable" }, 400);

  const enabled = payload.enabled === undefined ? current.enabled : Boolean(payload.enabled);
  const logEnabled = payload.logEnabled === undefined ? current.logEnabled : Boolean(payload.logEnabled);

  await env.DB.prepare(
    "UPDATE settings SET enabled = ?1, resolver = ?2, log_enabled = ?3, updated_at = ?4 WHERE id = 1"
  )
    .bind(enabled ? 1 : 0, resolver, logEnabled ? 1 : 0, Date.now())
    .run();
  invalidate();
  return json({ ok: true, settings: (await loadState(env)).settings });
}

async function handleRules(request, env) {
  if (request.method === "GET") {
    const rows = await env.DB.prepare("SELECT host, action, created_at FROM rules ORDER BY created_at DESC").all();
    return json({ rules: rows.results || [] });
  }
  const payload = await request.json().catch(() => null);
  if (!payload) return json({ error: "invalid_json" }, 400);
  const host = String(payload.host || "").trim().toLowerCase().replace(/^\.+|\.+$/g, "");
  if (!/^[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)+$/.test(host)) {
    return json({ error: "invalid_host" }, 400);
  }
  if (payload.action === "remove") {
    await env.DB.prepare("DELETE FROM rules WHERE host = ?1").bind(host).run();
  } else if (payload.action === "allow" || payload.action === "block") {
    await env.DB.prepare(
      "INSERT INTO rules (host, action, created_at) VALUES (?1, ?2, ?3) ON CONFLICT(host) DO UPDATE SET action = excluded.action"
    )
      .bind(host, payload.action, Date.now())
      .run();
  } else {
    return json({ error: "invalid_action" }, 400);
  }
  invalidate();
  return json({ ok: true });
}

const listSources = (env) =>
  env.DB.prepare("SELECT url, name FROM sources ORDER BY created_at, url").all();

async function listTitle(url) {
  try {
    const response = await fetch(url, { headers: { range: "bytes=0-4095", "user-agent": "adblock-list-builder" } });
    if (!response.ok || !response.body) return "";
    const reader = response.body.getReader();
    const { value } = await reader.read();
    await reader.cancel().catch(() => {});
    const match = new TextDecoder().decode(value || new Uint8Array()).match(/^[!#]\s*Title:\s*(.+)$/m);
    const title = match ? match[1].trim() : "";
    return title.length > 0 && title.length <= 120 ? title : "";
  } catch {
    return "";
  }
}

async function rebuild(env, ctx) {
  const { deployHook } = await loadState(env);
  if (!deployHook) return false;
  ctx.waitUntil(fetch(deployHook, { method: "POST" }).catch(() => {}));
  return true;
}

async function handleSources(request, env, ctx) {
  const payload = await request.json().catch(() => null);
  if (!payload) return json({ error: "invalid_json" }, 400);
  const url = String(payload.url || "").trim();
  if (payload.action === "remove") {
    await env.DB.prepare("DELETE FROM sources WHERE url = ?1").bind(url).run();
    return json({ ok: true, rebuilding: await rebuild(env, ctx) });
  }
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return json({ error: "invalid_url" }, 400);
  }
  if (parsed.protocol !== "https:") return json({ error: "invalid_url" }, 400);
  const typed = String(payload.name || "").trim();
  const name = typed || (await listTitle(parsed.toString()));
  await env.DB.prepare(
    "INSERT INTO sources (url, name, created_at) VALUES (?1, ?2, ?3) ON CONFLICT(url) DO UPDATE SET name = excluded.name"
  )
    .bind(parsed.toString(), name, Date.now())
    .run();
  return json({ ok: true, rebuilding: await rebuild(env, ctx) });
}

async function handleLog(request, env) {
  const url = new URL(request.url);
  const action = url.searchParams.get("action");
  const query = (url.searchParams.get("q") || "").trim().toLowerCase();
  const token = url.searchParams.get("token");
  const clauses = ["at >= ?1"];
  const binds = [Date.now() - LOG_RETENTION_MS];
  if (action === "block" || action === "allow") {
    clauses.push(`action = ?${binds.length + 1}`);
    binds.push(action);
  }
  if (query) {
    clauses.push(`name LIKE ?${binds.length + 1}`);
    binds.push(`%${query}%`);
  }
  if (token) {
    clauses.push(`token = ?${binds.length + 1}`);
    binds.push(token);
  }
  const rows = await env.DB.prepare(
    `SELECT at, token, name, type, action, source, rule, ms FROM queries WHERE ${clauses.join(" AND ")} ORDER BY id DESC LIMIT ${LOG_LIMIT}`
  )
    .bind(...binds)
    .all();
  return json({ log: rows.results || [] });
}

async function handleDevices(request, env) {
  const payload = await request.json().catch(() => null);
  if (!payload) return json({ error: "invalid_json" }, 400);
  if (payload.action === "remove") {
    await env.DB.prepare("DELETE FROM devices WHERE token = ?1").bind(String(payload.token || "")).run();
    invalidate();
    return json({ ok: true });
  }
  const name = String(payload.name || "").trim();
  if (!name) return json({ error: "invalid_name" }, 400);
  const platform = payload.platform === "other" ? "other" : "apple";
  const token = crypto.randomUUID().replace(/-/g, "");
  await env.DB.prepare("INSERT INTO devices (token, name, platform, created_at) VALUES (?1, ?2, ?3, ?4)")
    .bind(token, name, platform, Date.now())
    .run();
  invalidate();
  return json({ ok: true, token });
}

function mobileconfig(host, token, name) {
  const endpoint = `https://${host}/dns-query/${token}`;
  const identifier = `gr.adblock.${token.slice(0, 12)}`;
  const uuid = [
    token.slice(0, 8),
    token.slice(8, 12),
    token.slice(12, 16),
    token.slice(16, 20),
    token.slice(20, 32)
  ]
    .join("-")
    .toUpperCase();
  const escape = (value) => value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const label = escape(name);
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>PayloadContent</key>
  <array>
    <dict>
      <key>DNSSettings</key>
      <dict>
        <key>DNSProtocol</key>
        <string>HTTPS</string>
        <key>ServerURL</key>
        <string>${endpoint}</string>
      </dict>
      <key>OnDemandRules</key>
      <array>
        <dict>
          <key>Action</key>
          <string>EvaluateConnection</string>
          <key>ActionParameters</key>
          <array>
            <dict>
              <key>DomainAction</key>
              <string>NeverConnect</string>
              <key>Domains</key>
              <array>
                <string>captive.apple.com</string>
                <string>3gppnetwork.org</string>
              </array>
            </dict>
          </array>
        </dict>
        <dict>
          <key>Action</key>
          <string>Connect</string>
        </dict>
      </array>
      <key>PayloadDescription</key>
      <string>Encrypted DNS for ${label}</string>
      <key>PayloadDisplayName</key>
      <string>${label}</string>
      <key>PayloadIdentifier</key>
      <string>${identifier}.dnsSettings.managed</string>
      <key>PayloadOrganization</key>
      <string>Adblock</string>
      <key>PayloadType</key>
      <string>com.apple.dnsSettings.managed</string>
      <key>PayloadUUID</key>
      <string>${uuid}.dnsSettings.managed</string>
      <key>PayloadVersion</key>
      <integer>1</integer>
    </dict>
  </array>
  <key>PayloadDescription</key>
  <string>Sends every DNS query from this device to ${host}, encrypted, on Wi-Fi and on mobile data.</string>
  <key>PayloadDisplayName</key>
  <string>Adblock (${label})</string>
  <key>PayloadIdentifier</key>
  <string>${identifier}</string>
  <key>PayloadOrganization</key>
  <string>Adblock</string>
  <key>PayloadRemovalDisallowed</key>
  <false/>
  <key>PayloadScope</key>
  <string>System</string>
  <key>PayloadType</key>
  <string>Configuration</string>
  <key>PayloadUUID</key>
  <string>${uuid}</string>
  <key>PayloadVersion</key>
  <integer>1</integer>
</dict>
</plist>
`;
}

async function handleProfile(env, url) {
  const token = url.searchParams.get("token") || "";
  const device = await env.DB.prepare("SELECT name FROM devices WHERE token = ?1").bind(token).first();
  if (!device) return json({ error: "unknown_device" }, 404);
  return new Response(mobileconfig(url.host, token, device.name), {
    headers: {
      "content-type": "application/x-apple-aspen-config",
      "content-disposition": `attachment; filename="${device.name.replace(/[^\w.-]+/g, "-")}.mobileconfig"`,
      "cache-control": "no-store"
    }
  });
}

async function runScheduled(env, ctx, now) {
  await rebuild(env, ctx);
  await env.DB.batch([
    env.DB.prepare(`DELETE FROM queries WHERE id IN (SELECT id FROM queries WHERE at < ?1 ORDER BY id LIMIT ${PRUNE_LIMIT})`).bind(
      now - LOG_RETENTION_MS
    ),
    env.DB.prepare("DELETE FROM errors WHERE at < ?1").bind(now - ERROR_RETENTION_MS)
  ]);
}

const API = {
  "/api/state": { method: "GET", handler: handleState },
  "/api/settings": { method: "POST", handler: handleSettings },
  "/api/rules": { method: "ANY", handler: handleRules },
  "/api/log": { method: "GET", handler: handleLog },
  "/api/devices": { method: "POST", handler: handleDevices },
  "/api/sources": { method: "POST", handler: handleSources },
  "/api/report": { method: "POST", handler: handleReport }
};

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const dns = url.pathname.match(/^\/dns-query\/([0-9a-f]{8,64})$/);
    if (dns) {
      if (request.method !== "GET" && request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
      try {
        return await handleDns(request, env, ctx, url, dns[1]);
      } catch (error) {
        console.error(JSON.stringify({ dns_failed: String(error && error.message).slice(0, 200) }));
        return json({ error: "dns_failed", detail: String(error && error.message).slice(0, 200) }, 500);
      }
    }
    if (url.pathname === "/dns-query" || url.pathname.startsWith("/dns-query/")) return json({ error: "unknown_device" }, 403);

    const route = API[url.pathname];
    if (route || url.pathname === "/profile.mobileconfig") {
      const access = await checkAccess(request, url, env, ctx);
      if (access === "unavailable") return json({ error: "access_unavailable" }, 503);
      if (access !== "ok") return json({ error: "forbidden" }, 403);
      if (url.pathname === "/profile.mobileconfig") return handleProfile(env, url);
      if (route.method !== "ANY" && request.method !== route.method && !(route.method === "POST" && request.method === "DELETE")) {
        return json({ error: "method_not_allowed" }, 405);
      }
      try {
        return await route.handler(request, env, ctx);
      } catch (error) {
        return json({ error: "request_failed", detail: String(error && error.message).slice(0, 200) }, 500);
      }
    }

    if (url.pathname.startsWith("/api/")) return json({ error: "not_found" }, 404);

    return env.ASSETS.fetch(request);
  },

  async scheduled(controller, env, ctx) {
    ctx.waitUntil(
      runScheduled(env, ctx, controller.scheduledTime).catch((error) =>
        recordError(env, {
          kind: "cron",
          message: String(error && error.message ? error.message : error),
          stack: error && error.stack,
          route: "/cron"
        })
      )
    );
  }
};
