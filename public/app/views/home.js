import { el, toast } from "../dom.js";
import { t } from "../i18n.js";
import { currentState, saveSettings } from "../api.js";
import { navigate } from "../router.js";

function paint(label, track, enabled) {
  label.textContent = enabled ? t("protectionOn") : t("protectionOff");
  track.setAttribute("aria-pressed", enabled ? "true" : "false");
}

export function renderHome(container) {
  const state = currentState();
  if (!state) return;
  const { settings } = state;
  const label = el("div", { class: "hero-label" });
  const track = el(
    "button",
    {
      class: "switch-track large",
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
  const hero = el("div", { class: "hero" }, [label, track]);
  if (settings.resolvers.length === 0) {
    hero.append(
      el("div", { class: "banner" }, [
        el("span", { text: t("noResolver") }),
        el("button", { class: "btn small", type: "button", text: t("settingsTitle"), onclick: () => navigate("/settings") })
      ])
    );
  }
  container.append(hero);
}

export function updateHome(container) {
  const state = currentState();
  const label = container.querySelector(".hero-label");
  const track = container.querySelector(".hero .switch-track");
  if (!state || !label || !track) return false;
  if (Boolean(container.querySelector(".hero .banner")) !== (state.settings.resolvers.length === 0)) return false;
  paint(label, track, state.settings.enabled);
  return true;
}
