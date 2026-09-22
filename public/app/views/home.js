import { el, toast } from "../dom.js";
import { t } from "../i18n.js";
import { currentState, saveSettings } from "../api.js";
import { navigate } from "../router.js";
import { renderLog } from "./log.js";

function paint(label, track, enabled) {
  label.textContent = enabled ? t("protectionOn") : t("protectionOff");
  track.setAttribute("aria-pressed", enabled ? "true" : "false");
}

export function renderHome(container) {
  const state = currentState();
  if (!state) return;
  const { settings } = state;
  const label = el("div", { class: "switch-label grow" });
  const track = el(
    "button",
    {
      class: "switch-track",
      type: "button",
      "aria-label": t("protection"),
      onclick: async () => {
        const next = track.getAttribute("aria-pressed") !== "true";
        paint(label, track, next);
        try {
          await saveSettings({ enabled: next });
        } catch {
          paint(label, track, currentState().settings.enabled);
          toast(t("requestFailed"));
        }
      }
    },
    [el("span", { class: "switch-knob" })]
  );
  paint(label, track, settings.enabled);
  container.append(el("div", { class: "card tight home-switch" }, [el("div", { class: "switch" }, [label, track])]));
  if (settings.resolvers.length === 0) {
    container.append(
      el("div", { class: "banner" }, [
        el("span", { text: t("noResolver") }),
        el("button", { class: "btn small", type: "button", text: t("tabProtection"), onclick: () => navigate("/protection") })
      ])
    );
  }
  renderLog(container);
}

export function updateHome(container) {
  const state = currentState();
  const label = container.querySelector(".home-switch .switch-label");
  const track = container.querySelector(".home-switch .switch-track");
  if (!state || !label || !track) return false;
  if (Boolean(container.querySelector(".banner")) !== (state.settings.resolvers.length === 0)) return false;
  paint(label, track, state.settings.enabled);
  return true;
}
