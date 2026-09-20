const LIMIT = 5;
const seen = new Set();
let sent = 0;

function post(kind, message, stack) {
  if (sent >= LIMIT) return;
  const key = `${kind}|${message}`;
  if (seen.has(key)) return;
  seen.add(key);
  sent += 1;
  const body = JSON.stringify({
    kind,
    message: String(message || "").slice(0, 300),
    stack: String(stack || "").slice(0, 1000),
    route: location.pathname
  });
  try {
    if (navigator.sendBeacon) {
      navigator.sendBeacon("/api/report", new Blob([body], { type: "application/json" }));
      return;
    }
  } catch {}
  fetch("/api/report", { method: "POST", headers: { "content-type": "application/json" }, body, keepalive: true }).catch(() => {});
}

export function startReporting() {
  addEventListener("error", (event) => {
    if (event.error) post("error", event.error.message, event.error.stack);
    else if (event.message) post("error", event.message, `${event.filename}:${event.lineno}`);
  });
  addEventListener("unhandledrejection", (event) => {
    const reason = event.reason;
    post("rejection", reason && reason.message ? reason.message : String(reason), reason && reason.stack);
  });
}
