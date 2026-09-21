import { el, clear, toast } from "./dom.js";
import { startShell, scrollViewTop } from "./shell.js";
import { t, applyLanguage, i18nEvents } from "./i18n.js";
import { applyTheme, themeEvents } from "./theme.js";
import { currentRoute, startRouter } from "./router.js";
import { apiEvents, currentStatus, refresh, signIn, setUpPassword } from "./api.js";
import { renderHome } from "./views/home.js";
import { renderLog } from "./views/log.js";
import { renderProtection } from "./views/protection.js";
import { renderSettings } from "./views/settings.js";

const view = document.getElementById("view");
const pill = document.getElementById("state-pill");
const tabs = [...document.querySelectorAll(".tab")];

const VIEWS = { home: renderHome, log: renderLog, protection: renderProtection, settings: renderSettings };
const TAB_LABELS = { home: "tabHome", log: "tabLog", protection: "tabProtection", settings: "tabSettings" };
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

let authMode = "login";

function setAuthMode(mode) {
  authMode = mode;
  render(true);
}

function loginCard() {
  let password = "";
  const input = el("input", {
    type: "password",
    autocomplete: "current-password",
    placeholder: t("passwordLabel"),
    oninput: (event) => {
      password = event.target.value;
    }
  });
  const submit = async () => {
    if (!password) return;
    input.blur();
    try {
      await signIn(password);
    } catch (error) {
      input.value = "";
      password = "";
      if (error.code === "setup_required") setAuthMode("setup");
      else if (error.code === "too_many_attempts") toast(t("tooManyAttempts"));
      else if (Number.isInteger(error.remaining)) toast(t("wrongPasswordLeft", { count: error.remaining }));
      else toast(t("wrongPassword"));
    }
  };
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") submit();
  });
  return el("div", { class: "card" }, [
    el("h2", { text: t("signInTitle") }),
    el("div", { class: "tiny", text: t("signInNote") }),
    el("div", { class: "resolver-row" }, [
      input,
      el("button", { class: "btn small primary", type: "button", text: t("statusSignIn"), onclick: submit })
    ]),
    el("button", { class: "btn small ghost", type: "button", text: t("setupTitle"), onclick: () => setAuthMode("setup") })
  ]);
}

function setupCard() {
  let code = "";
  let password = "";
  const codeInput = el("input", {
    type: "text",
    autocapitalize: "none",
    spellcheck: "false",
    placeholder: t("setupCode"),
    oninput: (event) => {
      code = event.target.value;
    }
  });
  const passwordInput = el("input", {
    type: "password",
    autocomplete: "new-password",
    placeholder: t("newPassword"),
    oninput: (event) => {
      password = event.target.value;
    }
  });
  const submit = async () => {
    if (!code.trim() || !password) return;
    codeInput.blur();
    passwordInput.blur();
    try {
      await setUpPassword(code.trim(), password);
    } catch (error) {
      passwordInput.value = "";
      password = "";
      if (error.code === "wrong_code") toast(t("wrongCode"));
      else if (error.code === "weak_password") toast(t("weakPassword", { count: error.detail }));
      else if (error.code === "already_configured") setAuthMode("login");
      else if (error.code === "too_many_attempts") toast(t("tooManyAttempts"));
      else toast(t("requestFailed"));
    }
  };
  passwordInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") submit();
  });
  return el("div", { class: "card" }, [
    el("h2", { text: t("setupTitle") }),
    el("div", { class: "tiny", text: t("setupNote") }),
    codeInput,
    passwordInput,
    el("button", { class: "btn primary", type: "button", text: t("setupSubmit"), onclick: submit }),
    el("button", { class: "btn small ghost", type: "button", text: t("statusSignIn"), onclick: () => setAuthMode("login") })
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
  clear(view);
  if (currentStatus() === "auth") {
    view.append(authMode === "setup" ? setupCard() : loginCard());
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
  refresh().catch(() => {});
});

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") refresh().catch(() => {});
});

async function boot() {
  startShell();
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
