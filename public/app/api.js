const TIMEOUT = 15000;

export const apiEvents = new EventTarget();

let state = null;
let status = "loading";

export const currentState = () => state;
export const currentStatus = () => status;

function setStatus(next) {
  if (status === next) return;
  status = next;
  apiEvents.dispatchEvent(new CustomEvent("status"));
}

async function call(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: options.body ? { "content-type": "application/json" } : undefined,
    redirect: "manual",
    cache: "no-store",
    signal: AbortSignal.timeout(TIMEOUT)
  });
  if (response.type === "opaqueredirect" || response.status === 401 || response.status === 403) {
    setStatus("auth");
    const error = new Error("auth_required");
    error.code = "auth";
    throw error;
  }
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload.error || `http_${response.status}`);
    error.code = payload.error || `http_${response.status}`;
    error.detail = payload.detail;
    error.remaining = payload.remaining;
    throw error;
  }
  return payload;
}

export async function refresh() {
  try {
    state = await call("/api/state");
    setStatus(state.settings.enabled ? "on" : "off");
    apiEvents.dispatchEvent(new CustomEvent("changed"));
    return state;
  } catch (error) {
    if (error.code !== "auth") setStatus("failed");
    throw error;
  }
}

export async function saveSettings(patch, quiet = false) {
  const result = await call("/api/settings", { method: "POST", body: JSON.stringify(patch) });
  if (state) state.settings = result.settings;
  setStatus(result.settings.enabled ? "on" : "off");
  if (!quiet) apiEvents.dispatchEvent(new CustomEvent("changed"));
  return result.settings;
}

export function loadLog(params) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params || {})) if (value) query.set(key, value);
  return call(`/api/log?${query.toString()}`);
}

export async function setSource(url, name) {
  await call("/api/sources", { method: "POST", body: JSON.stringify({ url, name }) });
  await refresh();
}

export async function removeSource(url) {
  await call("/api/sources", { method: "POST", body: JSON.stringify({ action: "remove", url }) });
  await refresh();
}

export function loadRules() {
  return call("/api/rules");
}

export async function setRule(host, action) {
  await call("/api/rules", { method: "POST", body: JSON.stringify({ host, action }) });
  await refresh();
}

export async function addDevice(name, platform) {
  const result = await call("/api/devices", { method: "POST", body: JSON.stringify({ name, platform }) });
  await refresh();
  return result.token;
}


export async function removeDevice(token) {
  await call("/api/devices", { method: "POST", body: JSON.stringify({ action: "remove", token }) });
  await refresh();
}
