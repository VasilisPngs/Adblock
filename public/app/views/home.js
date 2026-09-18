import { el, clear, formatNumber, toast } from "../dom.js";
import { t, relativeTime, sourceLabel } from "../i18n.js";
import { currentState, saveSettings, loadTop } from "../api.js";
import { navigate } from "../router.js";

function masterSwitch(enabled) {
  return el("div", { class: "card tight" }, [
    el("div", { class: "switch" }, [
      el("div", { class: "grow" }, [
        el("div", { class: "switch-label", text: t("blockingTitle") }),
        el("div", { class: "tiny", text: enabled ? t("blockingOn") : t("blockingOff") })
      ]),
      el(
        "button",
        {
          class: "switch-track",
          type: "button",
          "aria-pressed": enabled ? "true" : "false",
          "aria-label": t("blockingTitle"),
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
  const { today, list, settings } = state;
  const total = today.allow + today.block;
  const share = total > 0 ? (today.block / total) * 100 : 0;

  container.append(masterSwitch(settings.enabled));

  if (settings.resolvers.length === 0) {
    container.append(
      el("div", { class: "banner" }, [
        el("span", { text: t("noResolver") }),
        el("button", { class: "btn small", type: "button", text: t("settingsTitle"), onclick: () => navigate("/settings") })
      ])
    );
  }

  container.append(
    el("div", { class: "stat-grid" }, [
      el("div", { class: "stat" }, [
        el("b", { class: "num", text: today.block.toLocaleString() }),
        el("span", { class: "tiny", text: t("blockedToday") })
      ]),
      el("div", { class: "stat" }, [
        el("b", { class: "num", text: today.allow.toLocaleString() }),
        el("span", { class: "tiny", text: t("allowedToday") })
      ]),
      el("div", { class: "stat" }, [
        el("b", { class: "num", text: `${formatNumber(share, 1)}%` }),
        el("span", { class: "tiny", text: t("blockRate") })
      ]),
      el("div", { class: "stat" }, [
        el("b", { class: "num", text: today.error.toLocaleString() }),
        el("span", { class: "tiny", text: t("errorsToday") })
      ])
    ])
  );

  container.append(
    el("div", { class: "card" }, [
      el("div", { class: "row between" }, [
        el("span", { text: t("listSummary", { domains: list.domains.toLocaleString() }) }),
        el("span", { class: "tiny", text: t("customRules", { count: state.customRules }) })
      ]),
      el("div", { class: "tiny", text: t("listBuilt", { when: relativeTime(Date.parse(list.builtAt)) }) })
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
      for (const row of top.slice(0, 12)) {
        rows.append(
          el("div", { class: "list-item" }, [
            el("span", { class: "grow" }, [
              el("div", { style: row.action === "block" ? "color:var(--danger)" : "", text: row.name }),
              el("div", { class: "tiny", text: sourceLabel(row.action === "block" ? "list" : "none") })
            ]),
            el("span", { class: "num tiny", text: String(row.total) })
          ])
        );
      }
    })
    .catch(() => {
      clear(rows);
      rows.append(el("div", { class: "empty", text: t("requestFailed") }));
    });
}
