import { el, clear } from "./dom.js";
import { t, applyLanguage, i18nEvents } from "./i18n.js";
import { applyTheme, themeEvents } from "./theme.js";
import { currentRoute, startRouter, navigate } from "./router.js";
import { apiEvents, currentStatus, refresh, signIn } from "./api.js";
import { renderHome } from "./views/home.js";
import { renderLog } from "./views/log.js";
import { renderLists } from "./views/lists.js";
import { renderSettings } from "./views/settings.js";

const view = document.getElementById("view");
const pill = document.getElementById("state-pill");
const tabs = [...document.querySelectorAll(".tab")];

const VIEWS = { home: renderHome, log: renderLog, lists: renderLists, settings: renderSettings };
const TAB_LABELS = { home: "tabHome", log: "tabLog", lists: "tabLists", settings: "tabSettings" };
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

function render() {
  if (isEditing()) return;
  const route = currentRoute();
  for (const tab of tabs) {
    tab.setAttribute("aria-current", tab.dataset.route === route.name ? "page" : "false");
    const label = tab.querySelector("span");
    if (label) label.textContent = t(TAB_LABELS[tab.dataset.route]);
  }
  clear(view);
  if (currentStatus() === "auth") {
    view.append(
      el("div", { class: "empty" }, [
        el("p", { text: t("statusSignIn") }),
        el("button", { class: "btn primary", type: "button", text: t("statusSignIn"), onclick: signIn })
      ])
    );
    return;
  }
  (VIEWS[route.name] || renderHome)(view, route.params);
  if (route.name !== lastRoute) {
    lastRoute = route.name;
    view.classList.remove("enter");
    void view.offsetWidth;
    view.classList.add("enter");
    scrollTo({ top: 0, behavior: "instant" });
  }
}

pill.addEventListener("click", () => {
  if (currentStatus() === "auth") signIn();
  else refresh().catch(() => {});
});

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") refresh().catch(() => {});
});

async function boot() {
  if (new URL(location.href).searchParams.has("signin")) history.replaceState({}, "", location.pathname);
  applyLanguage();
  applyTheme();
  apiEvents.addEventListener("changed", render);
  apiEvents.addEventListener("status", () => {
    paintPill();
    render();
  });
  i18nEvents.addEventListener("changed", () => {
    paintPill();
    render();
  });
  themeEvents.addEventListener("changed", render);
  startRouter(render);
  paintPill();
  await refresh().catch(() => {});
  setInterval(() => {
    if (document.visibilityState === "visible" && currentRoute().name === "home") refresh().catch(() => {});
  }, 30000);
  void navigate;
}

boot();
