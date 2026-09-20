import { el, clear, toast } from "../dom.js";
import { t } from "../i18n.js";
import { currentState, saveSettings, loadTop } from "../api.js";
import { navigate } from "../router.js";

function masterSwitch(enabled) {
  return el("div", { class: "card tight" }, [
    el("div", { class: "switch" }, [
      el("div", { class: "switch-label grow", text: enabled ? t("protectionOn") : t("protectionOff") }),
      el(
        "button",
        {
          class: "switch-track",
          type: "button",
          "aria-pressed": enabled ? "true" : "false",
          "aria-label": t("protection"),
          onclick: async (event) => {
            const track = event.currentTarget;
            track.setAttribute("aria-pressed", enabled ? "false" : "true");
            try {
              await saveSettings({ enabled: !enabled });
            } catch {
              track.setAttribute("aria-pressed", enabled ? "true" : "false");
              toast(t("requestFailed"));
            }
          }
        },
        [el("span", { class: "switch-knob" })]
      )
    ])
  ]);
}

export function renderHome(container) {
  const state = currentState();
  if (!state) return;
  const { today, settings } = state;
  const total = today.allow + today.block + today.error;

  const bar = el("div", { class: "list-bar" });
  container.append(bar);

  bar.append(masterSwitch(settings.enabled));

  if (settings.resolvers.length === 0) {
    bar.append(
      el("div", { class: "banner" }, [
        el("span", { text: t("noResolver") }),
        el("button", { class: "btn small", type: "button", text: t("settingsTitle"), onclick: () => navigate("/settings") })
      ])
    );
  }

  bar.append(
    el("div", { class: "stat-grid" }, [
      el("div", { class: "stat" }, [
        el("b", { class: "num", text: total.toLocaleString() }),
        el("span", { class: "tiny", text: t("queriesTotal") })
      ]),
      el("div", { class: "stat" }, [
        el("b", { class: "num", style: "color:var(--danger)", text: today.block.toLocaleString() }),
        el("span", { class: "tiny", text: t("blockedTotal") })
      ])
    ])
  );

  const card = el("div", { class: "card" }, [el("h2", { text: t("topDomains") })]);
  const rows = el("div", { class: "list" });
  rows.append(el("div", { class: "empty", text: t("statusLoading") }));
  card.append(rows);
  container.append(card);

  loadTop()
    .then(({ top }) => {
      clear(rows);
      if (top.length === 0) {
        rows.append(el("div", { class: "empty", text: t("noActivity") }));
        return;
      }
      for (const row of top) {
        rows.append(
          el("div", { class: "list-item" }, [
            el("span", { class: "grow", style: "color:var(--danger);overflow-wrap:anywhere", text: row.name }),
            el("span", { class: "num tiny", text: row.total.toLocaleString() })
          ])
        );
      }
    })
    .catch(() => {
      clear(rows);
      rows.append(el("div", { class: "empty", text: t("requestFailed") }));
    });
}
