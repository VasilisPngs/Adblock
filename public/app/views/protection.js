import { el, clear, toast } from "../dom.js";
import { t, relativeTime } from "../i18n.js";
import { currentState, loadRules, setRule, saveSettings, setSource, removeSource, rebuildNow } from "../api.js";

function sourceCard(state) {
  const compiled = new Map((state.list.compiled || []).map((entry) => [entry.url, entry]));
  const card = el("div", { class: "card" }, [
    el("div", { class: "row between" }, [
      el("h2", { text: t("sourcesTitle") }),
      el("span", { class: "tiny", text: t("listBuilt", { when: relativeTime(Date.parse(state.list.builtAt)) }) })
    ])
  ]);

  const list = el("div", { class: "list" });
  if ((state.list.sources || []).length === 0) {
    list.append(el("div", { class: "empty", text: t("noSources") }));
  }
  for (const source of state.list.sources || []) {
    const built = compiled.get(source.url);
    list.append(
      el("div", { class: "list-item" }, [
        el("span", { class: "grow" }, [
          el("div", { text: source.name || source.url }),
          el("div", { class: "tiny", style: "overflow-wrap:anywhere", text: source.url }),
          el("div", {
            class: "tiny",
            style: built ? "" : "color:var(--accent-text)",
            text: built
              ? t("sourceDomains", { count: built.domains.toLocaleString() })
              : t(state.settings.deployHookSet ? "sourceBuilding" : "sourcePending")
          })
        ]),
        el("button", {
          class: "btn small ghost danger",
          type: "button",
          text: t("remove"),
          onclick: async () => {
            try {
              await removeSource(source.url);
              toast(t("saved"));
            } catch {
              toast(t("requestFailed"));
            }
          }
        })
      ])
    );
  }
  card.append(list);

  let url = "";
  let name = "";
  const urlInput = el("input", {
    type: "text",
    inputMode: "url",
    autocapitalize: "none",
    autocomplete: "off",
    spellcheck: "false",
    placeholder: t("sourceUrl"),
    oninput: (event) => {
      url = event.target.value;
    }
  });
  const nameInput = el("input", {
    type: "text",
    autocomplete: "off",
    placeholder: t("sourceName"),
    oninput: (event) => {
      name = event.target.value;
    }
  });
  const submit = async () => {
    if (!url.trim()) return;
    urlInput.blur();
    nameInput.blur();
    try {
      await setSource(url.trim(), name.trim());
      toast(t("saved"));
    } catch (error) {
      toast(error.code === "invalid_url" ? t("invalidUrl") : t("requestFailed"));
    }
  };
  urlInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") submit();
  });

  card.append(urlInput);
  card.append(
    el("div", { class: "resolver-row" }, [
      nameInput,
      el("button", { class: "btn small primary", type: "button", text: t("addSource"), onclick: submit })
    ])
  );
  return card;
}

function rebuildCard(settings) {
  let hook = "";
  const input = el("input", {
    type: "password",
    autocomplete: "off",
    placeholder: t("deployHook"),
    oninput: (event) => {
      hook = event.target.value;
    }
  });
  const save = async () => {
    input.blur();
    try {
      await saveSettings({ deployHook: hook.trim() });
      input.value = "";
      hook = "";
      toast(t("deployHookSaved"));
    } catch (error) {
      toast(error.code === "invalid_url" ? t("invalidUrl") : t("requestFailed"));
    }
  };
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") save();
  });

  const field = el("div", { class: "resolver-row" }, [
    input,
    el("button", { class: "btn small", type: "button", text: t("save"), onclick: save })
  ]);
  const card = el("div", { class: "card" }, [
    el("div", { class: "row between" }, [
      el("h2", { text: t("rebuildTitle") }),
      el("button", {
        class: "btn small primary",
        type: "button",
        text: t("rebuildNow"),
        onclick: async () => {
          try {
            const { started } = await rebuildNow();
            toast(started ? t("rebuildStarted") : t("rebuildNoHook"));
          } catch {
            toast(t("requestFailed"));
          }
        }
      })
    ])
  ]);

  if (settings.deployHookSet) {
    card.append(el("div", { class: "tiny", text: t("deployHookReady") }));
    card.append(
      el("button", {
        class: "btn small ghost",
        type: "button",
        text: t("changeHook"),
        onclick: (event) => {
          event.currentTarget.remove();
          card.append(field);
          input.focus();
        }
      })
    );
    return card;
  }

  card.append(field);
  card.append(el("div", { class: "tiny", text: t("deployHookNote") }));
  return card;
}

function resolverCard(settings) {
  const card = el("div", { class: "card" }, [
    el("h2", { text: t("resolversTitle") }),
    el("div", { class: "tiny", text: t("resolversNote") })
  ]);
  let values = settings.resolvers.length > 0 ? [...settings.resolvers] : [""];
  const rows = el("div", { class: "list" });

  const commit = async () => {
    const resolvers = values.map((value) => value.trim()).filter(Boolean);
    try {
      const saved = await saveSettings({ resolvers });
      values = saved.resolvers.length > 0 ? [...saved.resolvers] : [""];
      paint();
      toast(t("saved"));
    } catch (error) {
      const detail = Array.isArray(error.detail) ? error.detail.join(", ") : error.detail || "";
      if (error.code === "invalid_resolver") toast(t("invalidResolver", { detail }));
      else toast(t("requestFailed"));
    }
  };

  const paint = () => {
    clear(rows);
    values.forEach((value, index) => {
      rows.append(
        el("div", { class: "resolver-row" }, [
          el("input", {
            type: "text",
            inputMode: "url",
            autocapitalize: "none",
            autocomplete: "off",
            spellcheck: "false",
            placeholder: t("resolverPlaceholder"),
            value,
            oninput: (event) => {
              values[index] = event.target.value;
            }
          }),
          el("button", {
            class: "btn small ghost",
            type: "button",
            text: t("remove"),
            onclick: () => {
              values.splice(index, 1);
              if (values.length === 0) values.push("");
              paint();
            }
          })
        ])
      );
    });
  };

  paint();
  card.append(rows);
  card.append(
    el("div", { class: "row", style: "gap:8px" }, [
      el("button", {
        class: "btn small grow",
        type: "button",
        text: t("addResolver"),
        onclick: () => {
          values.push("");
          paint();
        }
      }),
      el("button", { class: "btn small primary grow", type: "button", text: t("save"), onclick: commit })
    ])
  );
  return card;
}

function ruleCard() {
  const card = el("div", { class: "card" }, [el("h2", { text: t("myRules") })]);
  const list = el("div", { class: "list" });
  let pending = "";

  const paint = async () => {
    clear(list);
    try {
      const { rules } = await loadRules();
      if (rules.length === 0) {
        list.append(el("div", { class: "empty", text: t("noRules") }));
        return;
      }
      for (const rule of rules) {
        list.append(
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
      list.append(el("div", { class: "empty", text: t("requestFailed") }));
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
    autocomplete: "off",
    spellcheck: "false",
    placeholder: t("hostPlaceholder"),
    oninput: (event) => {
      pending = event.target.value;
    },
    onkeydown: (event) => {
      if (event.key === "Enter") submit("block");
    }
  });

  card.append(list);
  card.append(
    el("div", { class: "resolver-row" }, [
      input,
      el("button", { class: "btn small", type: "button", text: t("actionAllow"), onclick: () => submit("allow") }),
      el("button", { class: "btn small primary", type: "button", text: t("actionBlock"), onclick: () => submit("block") })
    ])
  );
  paint();
  return card;
}

export function renderProtection(container) {
  const state = currentState();
  if (!state) return;

  container.append(el("div", { class: "list-bar" }, [el("h1", { text: t("protectionTitle") })]));
  container.append(sourceCard(state));
  container.append(ruleCard());
  container.append(resolverCard(state.settings));
  container.append(rebuildCard(state.settings));
}
