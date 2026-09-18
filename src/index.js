import { readQuestion, blockedResponse, servfail, base64UrlDecode, minimumTtl, QUERY_TYPES } from "./dns.js";
import { decide, bundledSize } from "./blocklist.js";
import { parseResolver, resolve, isCloudflareAddress } from "./upstream.js";
import { checkPassword, issueSession, validSession, sessionCookie } from "./auth.js";
import meta from "./blocklist-meta.json";

const CACHE_TTL_MS = 20000;
const BLOCK_TTL = 60;
const MAX_MESSAGE_BYTES = 4096;
const LOG_LIMIT = 200;

let cache = { at: 0, settings: null, rules: null, tokens: null };

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

async function loadState(env) {
  if (cache.settings && Date.now() - cache.at < CACHE_TTL_MS) return cache;
  const [settings, rules, devices] = await Promise.all([
    env.DB.prepare("SELECT enabled, resolvers, block_mode, log_enabled, log_days FROM settings WHERE id = 1").first(),
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
      blockMode: settings?.block_mode || "zero",
      logEnabled: Boolean(settings?.log_enabled),
      logDays: settings?.log_days ?? 7
    },
    rules: { allow, block },
    tokens: new Set((devices.results || []).map((row) => row.token))
  };
  return cache;
}

function invalidate() {
  cache = { at: 0, settings: null, rules: null, tokens: null };
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
  const { settings, rules, tokens } = await loadState(env);
  if (!tokens.has(token)) return json({ error: "unknown_device" }, 403);

  const message = await readMessage(request, url);
  if (!message) return json({ error: "bad_request" }, 400);
  const question = readQuestion(message);
  if (!question) return json({ error: "bad_query" }, 400);

  const verdict = settings.enabled ? decide(question.name, rules) : { action: "allow", rule: null, source: "off" };

  let body;
  let ttl = BLOCK_TTL;
  let failure = null;

  if (verdict.action === "block") {
    body = blockedResponse(message, question, settings.blockMode, BLOCK_TTL);
  } else {
    const resolvers = settings.resolvers.map(parseResolver).filter(Boolean);
    if (resolvers.length === 0) {
      failure = "no_resolver";
      body = servfail(message);
      ttl = 0;
    } else {
      for (const resolver of resolvers) {
        try {
          body = await resolve(resolver, message);
          failure = null;
          break;
        } catch (error) {
          failure = String(error && error.message).slice(0, 60);
        }
      }
      if (!body) {
        body = servfail(message);
        ttl = 0;
      } else {
        ttl = Math.min(minimumTtl(body) || 0, 3600);
      }
    }
  }

  if (settings.logEnabled) {
    ctx.waitUntil(
      logQuery(env, {
        at: started,
        token,
        name: question.name,
        type: QUERY_TYPES[question.type] || String(question.type),
        action: failure ? "error" : verdict.action,
        source: failure || verdict.source,
        rule: verdict.rule,
        ms: Date.now() - started
      })
    );
  }
  ctx.waitUntil(
    env.DB.prepare("UPDATE devices SET last_seen_at = ?2 WHERE token = ?1").bind(token, started).run().catch(() => {})
  );

  return dnsResponse(body, ttl);
}

async function handleLogin(request, env) {
  if (!env.DASHBOARD_PASSWORD) return json({ error: "setup_required" }, 503);
  const payload = await request.json().catch(() => null);
  const password = payload && typeof payload.password === "string" ? payload.password : "";
  if (!(await checkPassword(password, env.DASHBOARD_PASSWORD))) return json({ error: "wrong_password" }, 401);
  return new Response(JSON.stringify({ ok: true }), {
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "set-cookie": sessionCookie(await issueSession(env.DASHBOARD_PASSWORD))
    }
  });
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
  const now = Date.now();
  const since = now - 86400000;
  const [counts, top, devices, rules] = await Promise.all([
    env.DB.prepare(
      "SELECT action, COUNT(*) AS total FROM queries WHERE at >= ?1 GROUP BY action"
    ).bind(since).all(),
    env.DB.prepare(
      "SELECT name, action, COUNT(*) AS total FROM queries WHERE at >= ?1 GROUP BY name, action ORDER BY total DESC LIMIT 20"
    ).bind(since).all(),
    env.DB.prepare("SELECT token, name, created_at, last_seen_at FROM devices ORDER BY created_at").all(),
    env.DB.prepare("SELECT COUNT(*) AS total FROM rules").first()
  ]);
  const totals = { allow: 0, block: 0, error: 0 };
  for (const row of counts.results || []) totals[row.action] = row.total;
  return json({
    settings,
    list: { domains: meta.domains, builtAt: meta.builtAt, sources: meta.sources, bundled: bundledSize() },
    today: totals,
    top: top.results || [],
    devices: devices.results || [],
    customRules: rules?.total || 0,
    host: new URL(request.url).host
  });
}

async function handleSettings(request, env) {
  const payload = await request.json().catch(() => null);
  if (!payload) return json({ error: "invalid_json" }, 400);
  const current = (await loadState(env)).settings;
  const resolvers = Array.isArray(payload.resolvers)
    ? payload.resolvers.map((value) => String(value).trim()).filter(Boolean).slice(0, 8)
    : current.resolvers;
  const invalid = resolvers.filter((value) => !parseResolver(value));
  const cloudflareTcp = resolvers.filter((value) => {
    const parsed = parseResolver(value);
    return parsed && parsed.kind === "tcp" && parsed.family === 4 && isCloudflareAddress(parsed.target);
  });
  if (invalid.length > 0) return json({ error: "invalid_resolver", detail: invalid }, 400);
  if (cloudflareTcp.length > 0) return json({ error: "cloudflare_ip_needs_doh", detail: cloudflareTcp }, 400);

  const enabled = payload.enabled === undefined ? current.enabled : Boolean(payload.enabled);
  const blockMode = payload.blockMode === "nxdomain" || payload.blockMode === "zero" ? payload.blockMode : current.blockMode;
  const logEnabled = payload.logEnabled === undefined ? current.logEnabled : Boolean(payload.logEnabled);
  const logDays = Number.isInteger(payload.logDays) ? Math.max(1, Math.min(90, payload.logDays)) : current.logDays;

  await env.DB.prepare(
    "UPDATE settings SET enabled = ?1, resolvers = ?2, block_mode = ?3, log_enabled = ?4, log_days = ?5, updated_at = ?6 WHERE id = 1"
  )
    .bind(enabled ? 1 : 0, JSON.stringify(resolvers), blockMode, logEnabled ? 1 : 0, logDays, Date.now())
    .run();
  invalidate();
  return json({ ok: true, settings: (await loadState(env)).settings });
}

async function handleRules(request, env) {
  const url = new URL(request.url);
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
  void url;
  return json({ ok: true });
}

async function handleLog(request, env) {
  const url = new URL(request.url);
  const action = url.searchParams.get("action");
  const query = (url.searchParams.get("q") || "").trim().toLowerCase();
  const token = url.searchParams.get("token");
  const clauses = ["at >= ?1"];
  const binds = [Date.now() - 86400000 * 7];
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
    `SELECT at, token, name, type, action, source, rule, ms FROM queries WHERE ${clauses.join(" AND ")} ORDER BY at DESC LIMIT ${LOG_LIMIT}`
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
  const name = String(payload.name || "").trim().slice(0, 40);
  if (!name) return json({ error: "invalid_name" }, 400);
  const token = crypto.randomUUID().replace(/-/g, "");
  await env.DB.prepare("INSERT INTO devices (token, name, created_at) VALUES (?1, ?2, ?3)").bind(token, name, Date.now()).run();
  invalidate();
  return json({ ok: true, token });
}

function mobileconfig(host, token, name) {
  const endpoint = `https://${host}/dns-query/${token}`;
  const identifier = `gr.adblock.${token.slice(0, 12)}`;
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
      <key>PayloadDescription</key>
      <string>Encrypted DNS for ${name}</string>
      <key>PayloadDisplayName</key>
      <string>${name}</string>
      <key>PayloadIdentifier</key>
      <string>${identifier}.dns</string>
      <key>PayloadType</key>
      <string>com.apple.dnsSettings.managed</string>
      <key>PayloadUUID</key>
      <string>${crypto.randomUUID().toUpperCase()}</string>
      <key>PayloadVersion</key>
      <integer>1</integer>
    </dict>
  </array>
  <key>PayloadDisplayName</key>
  <string>${host} · ${name}</string>
  <key>PayloadIdentifier</key>
  <string>${identifier}</string>
  <key>PayloadRemovalDisallowed</key>
  <false/>
  <key>PayloadType</key>
  <string>Configuration</string>
  <key>PayloadUUID</key>
  <string>${crypto.randomUUID().toUpperCase()}</string>
  <key>PayloadVersion</key>
  <integer>1</integer>
</dict>
</plist>
`;
}

async function handleProfile(request, env, url) {
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
  "/api/devices": { method: "POST", handler: handleDevices }
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

    if (url.pathname === "/api/login" || url.pathname === "/api/logout") {
      if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
      return url.pathname === "/api/login" ? handleLogin(request, env) : handleLogout();
    }

    const route = API[url.pathname];
    if (route || url.pathname === "/profile.mobileconfig") {
      if (!(await validSession(request, env.DASHBOARD_PASSWORD))) return json({ error: "unauthorized" }, 401);
      if (url.pathname === "/profile.mobileconfig") return handleProfile(request, env, url);
      if (route.method !== "ANY" && request.method !== route.method && !(route.method === "POST" && request.method === "DELETE")) {
        return json({ error: "method_not_allowed" }, 405);
      }
      try {
        return await route.handler(request, env);
      } catch (error) {
        return json({ error: "request_failed", detail: String(error && error.message).slice(0, 160) }, 500);
      }
    }

    if (url.pathname.startsWith("/api/")) return json({ error: "not_found" }, 404);

    return env.ASSETS.fetch(request);
  },

  async scheduled(controller, env, ctx) {
    const { settings } = await loadState(env);
    ctx.waitUntil(
      env.DB.prepare("DELETE FROM queries WHERE at < ?1")
        .bind(controller.scheduledTime - settings.logDays * 86400000)
        .run()
        .catch(() => {})
    );
  }
};
