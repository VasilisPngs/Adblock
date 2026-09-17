import { el, clear, toast } from "../dom.js";
import { t, relativeTime } from "../i18n.js";
import { currentState, loadRules, setRule } from "../api.js";

export function renderLists(container) {
  const state = currentState();
  if (!state) return;

  container.append(el("h1", { text: t("listsTitle") }));

  const sources = el("div", { class: "card" }, [
    el("div", { class: "row between" }, [
      el("h2", { text: t("sourcesTitle") }),
      el("span", { class: "tiny", text: t("listBuilt", { when: relativeTime(Date.parse(state.list.builtAt)) }) })
    ])
  ]);
  const sourceList = el("div", { class: "list" });
  for (const source of state.list.sources || []) {
    sourceList.append(
      el("div", { class: "list-item" }, [
        el("span", { class: "grow", text: source.name }),
        el("span", { class: "tiny num", text: t("sourceDomains", { count: source.domains.toLocaleString() }) })
      ])
    );
  }
  sources.append(sourceList);
  sources.append(el("div", { class: "tiny", text: t("sourcesNote") }));
  container.append(sources);

  const rulesCard = el("div", { class: "card" }, [el("h2", { text: t("myRules") })]);
  const rulesList = el("div", { class: "list" });
  let pending = "";

  const paint = async () => {
    clear(rulesList);
    try {
      const { rules } = await loadRules();
      if (rules.length === 0) {
        rulesList.append(el("div", { class: "empty", text: t("noRules") }));
        return;
      }
      for (const rule of rules) {
        rulesList.append(
          el("div", { class: "list-item" }, [
            el("span", { class: "grow" }, [
              el("div", { style: rule.action === "block" ? "color:var(--danger)" : "", text: rule.host }),
              el("div", { class: "tiny", text: rule.action === "allow" ? t("actionAllow") : t("actionBlock") })
            ]),
            el("button", {
              class: "btn small ghost",
              type: "button",
              text: t("remove"),
              onclick: async () => {
                try {
                  await setRule(rule.host, "remove");
                  toast(t("ruleRemoved"));
                  paint();
                } catch {
                  toast(t("requestFailed"));
                }
              }
            })
          ])
        );
      }
    } catch {
      rulesList.append(el("div", { class: "empty", text: t("requestFailed") }));
    }
  };

  const submit = async (action) => {
    const host = pending.trim().toLowerCase();
    if (!host) return;
    try {
      await setRule(host, action);
      pending = "";
      input.value = "";
      toast(t("ruleAdded"));
      paint();
    } catch (error) {
      toast(error.code === "invalid_host" ? t("hostPlaceholder") : t("requestFailed"));
    }
  };

  const input = el("input", {
    type: "text",
    inputMode: "url",
    autocapitalize: "none",
    spellcheck: "false",
    placeholder: t("hostPlaceholder"),
    oninput: (event) => {
      pending = event.target.value;
    },
    onkeydown: (event) => {
      if (event.key === "Enter") submit("block");
    }
  });

  rulesCard.append(rulesList);
  rulesCard.append(
    el("div", { class: "resolver-row" }, [
      input,
      el("button", { class: "btn small", type: "button", text: t("actionAllow"), onclick: () => submit("allow") }),
      el("button", { class: "btn small primary", type: "button", text: t("actionBlock"), onclick: () => submit("block") })
    ])
  );
  container.append(rulesCard);
  paint();
}
