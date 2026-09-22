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
import { parseResolver, resolve } from "./upstream.js";
import {
  checkPassword,
  hashPassword,
  hashText,
  outdatedHash,
  equalText,
  issueSession,
  validSession,
  sessionCookie,
  MIN_PASSWORD_LENGTH
} from "./auth.js";
import meta from "./blocklist-meta.json";

const CACHE_TTL_MS = 60000;
const D1_RETRY_MS = 5000;
const BLOCK_TTL = 300;
const TTL_FLOOR = 300;
const TTL_CEILING = 3600;
const MAX_MESSAGE_BYTES = 4096;
const LOG_LIMIT = 200;
const LOG_RETENTION_MS = 86400000;
const MAX_RESOLVERS = 2;
const LOGIN_WINDOW_MS = 600000;
const LOGIN_ATTEMPTS = 10;
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

function fetchUpstream(key, resolvers, message) {
  const pending = inflight.get(key);
  if (pending) return pending;
  const attempt = resolve(resolvers, message).finally(() => inflight.delete(key));
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

let cache = { at: 0, settings: null, rules: null, tokens: null, auth: null };
let d1DownUntil = 0;

const DEGRADED = {
  at: 0,
  settings: {
    enabled: false,
    resolvers: ["https://cloudflare-dns.com/dns-query"],
    logEnabled: false,
    deployHookSet: false
  },
  deployHook: null,
  rules: { allow: new Set(), block: new Set() },
  tokens: null,
  auth: { hash: null, setupCode: null }
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

async function readState(env) {
  const [settings, rules, devices] = await Promise.all([
    env.DB.prepare(
      "SELECT enabled, resolvers, log_enabled, password_hash, setup_code, deploy_hook FROM settings WHERE id = 1"
    ).first(),
    env.DB.prepare("SELECT host, action FROM rules").all(),
    env.DB.prepare("SELECT token FROM devices").all()
  ]);
  const allow = new Set();
  const block = new Set();
  for (const row of rules.results || []) (row.action === "allow" ? allow : block).add(row.host);
  cache = {
    at: Date.now(),
    settings: {
      enabled: Boolean(settings?.enabled),
      resolvers: JSON.parse(settings?.resolvers || "[]"),
      logEnabled: Boolean(settings?.log_enabled),
      deployHookSet: Boolean(settings?.deploy_hook)
    },
    deployHook: settings?.deploy_hook || null,
    rules: { allow, block },
    tokens: new Set((devices.results || []).map((row) => row.token)),
    auth: { hash: settings?.password_hash || null, setupCode: settings?.setup_code || null }
  };
  return cache;
}

function loadState(env) {
  if (cache.settings && Date.now() - cache.at < CACHE_TTL_MS) return Promise.resolve(cache);
  return readState(env);
}

async function dnsState(env) {
  if (Date.now() < d1DownUntil) return cache.settings ? cache : DEGRADED;
  if (cache.settings && Date.now() - cache.at < CACHE_TTL_MS) return cache;
  if (cache.settings) {
    if (!stateRefresh) {
      stateRefresh = readState(env)
        .catch(() => {
          d1DownUntil = Date.now() + D1_RETRY_MS;
        })
        .finally(() => {
          stateRefresh = null;
        });
    }
    return cache;
  }
  try {
    return await readState(env);
  } catch {
    d1DownUntil = Date.now() + D1_RETRY_MS;
    return DEGRADED;
  }
}

function invalidate() {
  cache = { at: 0, settings: null, rules: null, tokens: null, auth: null };
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

  const verdict = settings.enabled ? decide(question.name, rules) : { action: "allow", rule: null, source: "off" };
  const forward = stripClientSubnet(message, question);

  let body;
  let ttl = BLOCK_TTL;
  let failure = null;

  if (verdict.action === "block") {
    body = blockedResponse(message, question, BLOCK_TTL);
  } else {
    const resolvers = settings.resolvers.map(parseResolver).filter(Boolean);
    const key = cacheKey(question);
    const entry = answers.get(key);
    const usable = entry && entry.body.length >= question.end;

    const accept = (fresh) => {
      const cloaked = cloakedBy(fresh, settings, rules);
      if (cloaked) return cloaked;
      const answer = boostTtl(new Uint8Array(fresh), TTL_FLOOR);
      const life = ttlOf(answer);
      remember(key, answer, life);
      return { answer, life };
    };

    if (resolvers.length === 0) {
      failure = "no_resolver";
      body = servfail(message);
      ttl = 0;
    } else if (usable && started < entry.expires) {
      body = replay(entry, message, question, Math.floor((started - entry.storedAt) / 1000));
      ttl = Math.max(1, Math.ceil((entry.expires - started) / 1000));
      verdict.source = "cache";
    } else if (usable && started < entry.expires + STALE_GRACE_MS) {
      body = replay(entry, message, question, null);
      ttl = STALE_TTL;
      verdict.source = "stale";
      ctx.waitUntil(
        fetchUpstream(key, resolvers, forward).then((fresh) => {
          if (!fresh.body) return;
          if (accept(fresh.body).rule) answers.delete(key);
        })
      );
    } else {
      const fresh = await fetchUpstream(key, resolvers, forward);
      failure = fresh.failure;
      if (!fresh.body) {
        body = servfail(message);
        ttl = 0;
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

  const action = failure ? "error" : verdict.action;
  const hour = Math.floor(started / HOUR_MS);
  const type = QUERY_TYPES[question.type] || String(question.type);

  if (settings.logEnabled && firstThisHour(`${token}|${question.name}|${type}|${action}`, hour)) {
    ctx.waitUntil(
      logQuery(env, {
        at: started,
        token,
        name: question.name,
        type,
        action,
        source: failure || verdict.source,
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

async function sessionSecret(env) {
  if (env.DASHBOARD_PASSWORD) return hashText(env.DASHBOARD_PASSWORD);
  return (await loadState(env)).auth.hash;
}

const signedIn = (token) =>
  new Response(JSON.stringify({ ok: true }), {
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "set-cookie": sessionCookie(token)
    }
  });

function clientIp(request) {
  return request.headers.get("cf-connecting-ip") || "unknown";
}

async function attemptsLeft(env, ip) {
  const failures = await env.DB.prepare("SELECT COUNT(*) AS total FROM login_attempts WHERE ip = ?1 AND at >= ?2")
    .bind(ip, Date.now() - LOGIN_WINDOW_MS)
    .first();
  return LOGIN_ATTEMPTS - (failures?.total || 0);
}

const recordFailure = (env, ip) =>
  env.DB.prepare("INSERT INTO login_attempts (at, ip) VALUES (?1, ?2)").bind(Date.now(), ip).run().catch(() => {});

const clearFailures = (env, ip) =>
  env.DB.prepare("DELETE FROM login_attempts WHERE ip = ?1").bind(ip).run().catch(() => {});

async function handleLogin(request, env) {
  const secret = await sessionSecret(env);
  if (!secret) return json({ error: "setup_required" }, 503);

  const ip = clientIp(request);
  const remaining = await attemptsLeft(env, ip);
  if (remaining <= 0) return json({ error: "too_many_attempts" }, 429);

  const payload = await request.json().catch(() => null);
  const password = payload && typeof payload.password === "string" ? payload.password : "";
  if (!(await checkPassword(password, secret))) {
    await recordFailure(env, ip);
    return json({ error: "wrong_password", remaining: remaining - 1 }, 401);
  }
  await clearFailures(env, ip);
  if (env.DASHBOARD_PASSWORD || !outdatedHash(secret)) return signedIn(await issueSession(secret));
  const upgraded = await hashPassword(password);
  await env.DB.prepare("UPDATE settings SET password_hash = ?1 WHERE id = 1").bind(upgraded).run();
  invalidate();
  return signedIn(await issueSession(upgraded));
}

async function handleSetup(request, env) {
  if (await sessionSecret(env)) return json({ error: "already_configured" }, 409);

  const ip = clientIp(request);
  const remaining = await attemptsLeft(env, ip);
  if (remaining <= 0) return json({ error: "too_many_attempts" }, 429);

  const payload = await request.json().catch(() => null);
  const code = payload && typeof payload.code === "string" ? payload.code.trim().toLowerCase() : "";
  const password = payload && typeof payload.password === "string" ? payload.password : "";
  const { auth } = await loadState(env);
  if (!equalText(code, auth.setupCode)) {
    await recordFailure(env, ip);
    return json({ error: "wrong_code", remaining: remaining - 1 }, 401);
  }
  if (password.length < MIN_PASSWORD_LENGTH) return json({ error: "weak_password", detail: MIN_PASSWORD_LENGTH }, 400);

  const hash = await hashPassword(password);
  await env.DB.prepare("UPDATE settings SET password_hash = ?1, setup_code = NULL WHERE id = 1").bind(hash).run();
  invalidate();
  await clearFailures(env, ip);
  return signedIn(await issueSession(hash));
}

async function handlePassword(request, env) {
  if (env.DASHBOARD_PASSWORD) return json({ error: "managed_by_secret" }, 409);
  const payload = await request.json().catch(() => null);
  const current = payload && typeof payload.current === "string" ? payload.current : "";
  const next = payload && typeof payload.next === "string" ? payload.next : "";
  const { auth } = await loadState(env);
  if (!(await checkPassword(current, auth.hash))) return json({ error: "wrong_password" }, 401);
  if (next.length < MIN_PASSWORD_LENGTH) return json({ error: "weak_password", detail: MIN_PASSWORD_LENGTH }, 400);

  const hash = await hashPassword(next);
  await env.DB.prepare("UPDATE settings SET password_hash = ?1 WHERE id = 1").bind(hash).run();
  invalidate();
  return signedIn(await issueSession(hash));
}

const REPORT_WINDOW_MS = 60000;
const REPORT_LIMIT = 20;
let reportWindow = 0;
let reportCount = 0;

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
  await env.DB.prepare("INSERT INTO errors (at, kind, message, stack, route, agent) VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
    .bind(
      now,
      String(payload.kind || "error").slice(0, 20),
      message.slice(0, 300),
      String(payload.stack || "").slice(0, 1000) || null,
      String(payload.route || "").slice(0, 120) || null,
      (request.headers.get("user-agent") || "").slice(0, 200) || null
    )
    .run()
    .catch(() => {});
  return json({ ok: true });
}

function handleLogout() {
  return new Response(JSON.stringify({ ok: true }), {
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "set-cookie": sessionCookie("", 0)
    }
  });
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
  const resolvers = Array.isArray(payload.resolvers)
    ? payload.resolvers.map((value) => String(value).trim()).filter(Boolean).slice(0, MAX_RESOLVERS)
    : current.resolvers;
  const invalid = resolvers.filter((value) => !parseResolver(value));
  if (invalid.length > 0) return json({ error: "invalid_resolver", detail: invalid }, 400);

  const enabled = payload.enabled === undefined ? current.enabled : Boolean(payload.enabled);
  const logEnabled = payload.logEnabled === undefined ? current.logEnabled : Boolean(payload.logEnabled);

  if (typeof payload.deployHook === "string") {
    const hook = payload.deployHook.trim();
    if (hook && !/^https:\/\//.test(hook)) return json({ error: "invalid_url" }, 400);
    await env.DB.prepare("UPDATE settings SET deploy_hook = ?1 WHERE id = 1").bind(hook || null).run();
  }

  await env.DB.prepare(
    "UPDATE settings SET enabled = ?1, resolvers = ?2, log_enabled = ?3, updated_at = ?4 WHERE id = 1"
  )
    .bind(enabled ? 1 : 0, JSON.stringify(resolvers), logEnabled ? 1 : 0, Date.now())
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

async function handleSourcesRead(env) {
  const rows = await listSources(env);
  return json({ sources: rows.results || [] });
}

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

async function handleRebuild(request, env, ctx) {
  return json({ started: await rebuild(env, ctx) });
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
  if (action === "block" || action === "allow" || action === "error") {
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

const API = {
  "/api/state": { method: "GET", handler: handleState },
  "/api/settings": { method: "POST", handler: handleSettings },
  "/api/rules": { method: "ANY", handler: handleRules },
  "/api/log": { method: "GET", handler: handleLog },
  "/api/devices": { method: "POST", handler: handleDevices },
  "/api/password": { method: "POST", handler: handlePassword },
  "/api/sources": { method: "POST", handler: handleSources },
  "/api/rebuild": { method: "POST", handler: handleRebuild }
};

const OPEN = {
  "/api/login": handleLogin,
  "/api/setup": handleSetup,
  "/api/logout": () => handleLogout(),
  "/api/report": handleReport
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
        return json({ error: "dns_failed", detail: String(error && error.message).slice(0, 120) }, 500);
      }
    }

    if (url.pathname === "/dns-query" || url.pathname.startsWith("/dns-query/")) {
      return json({ error: "unknown_device" }, 403);
    }

    const open = OPEN[url.pathname];
    if (open) {
      if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
      try {
        return await open(request, env);
      } catch (error) {
        return json({ error: "request_failed", detail: String(error && error.message).slice(0, 160) }, 500);
      }
    }

    if (url.pathname === "/api/sources" && request.method === "GET") return handleSourcesRead(env);

    const route = API[url.pathname];
    if (route || url.pathname === "/profile.mobileconfig") {
      if (!(await validSession(request, await sessionSecret(env)))) return json({ error: "unauthorized" }, 401);
      if (url.pathname === "/profile.mobileconfig") return handleProfile(env, url);
      if (route.method !== "ANY" && request.method !== route.method && !(route.method === "POST" && request.method === "DELETE")) {
        return json({ error: "method_not_allowed" }, 405);
      }
      try {
        return await route.handler(request, env, ctx);
      } catch (error) {
        return json({ error: "request_failed", detail: String(error && error.message).slice(0, 160) }, 500);
      }
    }

    if (url.pathname.startsWith("/api/")) return json({ error: "not_found" }, 404);

    return env.ASSETS.fetch(request);
  },

  async scheduled(controller, env, ctx) {
    await rebuild(env, ctx);
    ctx.waitUntil(
      env.DB.batch([
        env.DB.prepare(
          `DELETE FROM queries WHERE id IN (SELECT id FROM queries WHERE at < ?1 ORDER BY id LIMIT ${PRUNE_LIMIT})`
        ).bind(controller.scheduledTime - LOG_RETENTION_MS),
        env.DB.prepare("DELETE FROM login_attempts WHERE at < ?1").bind(controller.scheduledTime - LOGIN_WINDOW_MS),
        env.DB.prepare("DELETE FROM errors WHERE at < ?1").bind(controller.scheduledTime - ERROR_RETENTION_MS)
      ]).catch(() => {})
    );
  }
};
