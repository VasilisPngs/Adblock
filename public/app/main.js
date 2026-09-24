import { el, clear } from "./dom.js";
import { startShell, scrollViewTop } from "./shell.js";
import { t, applyLanguage, i18nEvents } from "./i18n.js";
import { applyTheme, themeEvents } from "./theme.js";
import { currentRoute, startRouter } from "./router.js";
import { apiEvents, currentStatus, refresh } from "./api.js";
import { renderHome, updateHome } from "./views/home.js";
import { refreshLog } from "./views/log.js";
import { renderProtection } from "./views/protection.js";
import { renderSettings } from "./views/settings.js";

const view = document.getElementById("view");
const pill = document.getElementById("state-pill");
const tabs = [...document.querySelectorAll(".tab")];

const VIEWS = { home: renderHome, protection: renderProtection, settings: renderSettings };
const TAB_LABELS = { home: "tabHome", protection: "tabProtection", settings: "tabSettings" };
const PILL_LABELS = { on: "statusOn", off: "statusOff", loading: "statusLoading", failed: "statusFailed", auth: "statusSignIn" };
const PILL_STATE = { on: "idle", off: "pending", loading: "syncing", failed: "error", auth: "auth" };

let lastRoute = "";

function isEditing() {
  const active = document.activeElement;
  return Boolean(active) && view.contains(active) && ["INPUT", "TEXTAREA", "SELECT"].includes(active.tagName);
}

function paintPill() {
  const status = currentStatus();
  pill.textContent = t(PILL_LABELS[status] || "statusLoading");
  pill.dataset.status = PILL_STATE[status] || "idle";
}

function signIn() {
  location.href = `/?signin=${Date.now()}`;
}

function signInCard() {
  return el("div", { class: "card" }, [
    el("h2", { text: t("signInTitle") }),
    el("div", { class: "tiny", text: t("signInNote") }),
    el("button", { class: "btn primary", type: "button", text: t("statusSignIn"), onclick: signIn })
  ]);
}

function render(force = false) {
  if (!force && isEditing()) return;
  const route = currentRoute();
  for (const tab of tabs) {
    tab.setAttribute("aria-current", tab.dataset.route === route.name ? "page" : "false");
    const label = tab.querySelector("span");
    if (label) label.textContent = t(TAB_LABELS[tab.dataset.route]);
  }
  if (!force && route.name === "home" && lastRoute === "home" && currentStatus() !== "auth" && updateHome(view)) return;
  clear(view);
  if (currentStatus() === "auth") {
    view.append(signInCard());
    return;
  }
  (VIEWS[route.name] || renderHome)(view, route.params);
  if (route.name !== lastRoute) {
    lastRoute = route.name;
    view.classList.remove("enter");
    void view.offsetWidth;
    view.classList.add("enter");
    scrollViewTop();
  }
}

pill.addEventListener("click", () => {
  if (currentStatus() === "auth") signIn();
  else refresh().then(refreshLog).catch(() => {});
});

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") refresh().then(refreshLog).catch(() => {});
});

async function boot() {
  startShell();
  if (new URL(location.href).searchParams.has("signin")) history.replaceState({}, "", location.pathname);
  applyLanguage();
  applyTheme();
  apiEvents.addEventListener("changed", () => render());
  apiEvents.addEventListener("status", () => {
    paintPill();
    render();
  });
  i18nEvents.addEventListener("changed", () => {
    paintPill();
    render(true);
  });
  themeEvents.addEventListener("changed", () => render(true));
  startRouter(() => render());
  paintPill();
  await refresh().catch(() => {});
  setInterval(() => {
    if (document.visibilityState === "visible" && currentRoute().name === "home") refresh().catch(() => {});
  }, 60000);
}

boot();
