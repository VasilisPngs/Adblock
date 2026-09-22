import { el, toast } from "../dom.js";
import { t } from "../i18n.js";
import { currentState, saveSettings } from "../api.js";

const motion = matchMedia("(prefers-reduced-motion: no-preference)");

function paint(label, track, enabled) {
  const text = enabled ? t("protectionOn") : t("protectionOff");
  track.setAttribute("aria-pressed", enabled ? "true" : "false");
  if (label.textContent === text) return;
  const card = label.parentElement;
  const from = card.getBoundingClientRect().width;
  label.textContent = text;
  if (!motion.matches) return;
  const style = getComputedStyle(card);
  const timing = { duration: parseFloat(style.getPropertyValue("--speed")) * 1000, easing: style.getPropertyValue("--ease").trim() };
  card.animate({ width: [`${from}px`, `${card.getBoundingClientRect().width}px`] }, timing);
  label.animate({ opacity: [0, 1] }, timing);
}

export function renderHome(container) {
  const state = currentState();
  if (!state) return;
  const { settings } = state;
  const label = el("div", { class: "hero-label", text: settings.enabled ? t("protectionOn") : t("protectionOff") });
  const track = el(
    "button",
    {
      class: "switch-track large",
      type: "button",
      "aria-pressed": settings.enabled ? "true" : "false",
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
  container.append(el("div", { class: "hero" }, [el("div", { class: "card hero-card" }, [label, track])]));
}

export function updateHome(container) {
  const state = currentState();
  const label = container.querySelector(".hero-label");
  const track = container.querySelector(".hero .switch-track");
  if (!state || !label || !track) return false;
  paint(label, track, state.settings.enabled);
  return true;
}
