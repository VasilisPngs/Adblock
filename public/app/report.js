const LIMIT = 8;
const seen = new Set();
const nativeFetch = globalThis.fetch.bind(globalThis);
let sent = 0;

function post(kind, message, detail) {
  const text = String(message == null ? "" : message).slice(0, 300).trim();
  if (!text || sent >= LIMIT) return;
  const key = `${kind}|${text}`;
  if (seen.has(key)) return;
  seen.add(key);
  sent += 1;
  const body = JSON.stringify({
    kind,
    message: text,
    stack: String(detail || "").slice(0, 1000),
    route: location.pathname
  });
  try {
    if (navigator.sendBeacon && navigator.sendBeacon("/api/report", new Blob([body], { type: "application/json" }))) return;
  } catch {}
  nativeFetch("/api/report", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
    keepalive: true
  }).catch(() => {});
}

function apiPath(input) {
  try {
    const raw = typeof input === "string" ? input : input && input.url ? input.url : String(input);
    const url = new URL(raw, location.href);
    if (url.origin !== location.origin) return "";
    if (!url.pathname.startsWith("/api/") || url.pathname === "/api/report") return "";
    return url.pathname;
  } catch {
    return "";
  }
}

addEventListener(
  "error",
  (event) => {
    const target = event.target;
    if (target && target !== globalThis && target.tagName) {
      post("resource", `${target.tagName.toLowerCase()} failed to load`, target.currentSrc || target.src || target.href || "");
      return;
    }
    if (event.error) post("error", event.error.message, event.error.stack);
    else post("error", event.message, `${event.filename}:${event.lineno}`);
  },
  true
);

addEventListener("unhandledrejection", (event) => {
  const reason = event.reason;
  post("rejection", reason && reason.message ? reason.message : String(reason), reason && reason.stack);
});

globalThis.fetch = async (input, init) => {
  const path = apiPath(input);
  try {
    const response = await nativeFetch(input, init);
    if (path && !response.ok && response.status !== 401 && response.status !== 403) post("api", `${response.status} ${path}`, "");
    return response;
  } catch (error) {
    if (path) post("network", `${path} unreachable`, error && error.message);
    throw error;
  }
};

function insets() {
  const probe = document.createElement("div");
  probe.style.cssText =
    "position:fixed;left:0;top:0;width:0;height:0;visibility:hidden;" +
    "padding-top:env(safe-area-inset-top);padding-bottom:env(safe-area-inset-bottom)";
  document.documentElement.append(probe);
  const style = getComputedStyle(probe);
  const value = [parseFloat(style.paddingTop) || 0, parseFloat(style.paddingBottom) || 0];
  probe.remove();
  return value;
}

function reportViewport() {
  const [top, bottom] = insets();
  const root = document.documentElement;
  const bar = document.querySelector(".tabbar");
  post(
    "viewport",
    [
      `iw=${innerWidth}`,
      `ih=${innerHeight}`,
      `cw=${root.clientWidth}`,
      `ch=${root.clientHeight}`,
      `sw=${screen.width}`,
      `sh=${screen.height}`,
      `sat=${top}`,
      `sab=${bottom}`,
      `scroll=${root.scrollHeight - root.clientHeight}`,
      `barBottom=${bar ? Math.round(bar.getBoundingClientRect().bottom) : -1}`,
      `standalone=${matchMedia("(display-mode: standalone)").matches || navigator.standalone === true}`,
      `dpr=${devicePixelRatio}`
    ].join(" "),
    ""
  );
}

addEventListener("load", () => setTimeout(reportViewport, 1200));
