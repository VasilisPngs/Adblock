import { el, clear, toast } from "../dom.js";
import { t, locale, sourceLabel } from "../i18n.js";
import { currentState, loadLog, loadRules, setRule } from "../api.js";

const FILTERS = [
  ["", "filterAll"],
  ["block", "filterBlocked"],
  ["allow", "filterAllowed"],
  ["error", "filterErrors"]
];

let filter = "";
let search = "";
let device = "";

function timeFormatter() {
  return new Intl.DateTimeFormat(locale(), { hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23" });
}

function rowNode(entry, ruleMap, deviceNames, repaint) {
  const classes = ["log-row"];
  if (entry.action === "block") classes.push("blocked");
  if (entry.action === "error") classes.push("error");
  const known = ruleMap.get(entry.name);
  const actions = el("div", { class: "log-actions" });

  const apply = async (host, action) => {
    try {
      await setRule(host, action);
      toast(action === "remove" ? t("ruleRemoved") : t("ruleAdded"));
      repaint();
    } catch {
      toast(t("requestFailed"));
    }
  };

  if (known) {
    actions.append(
      el("button", { class: "btn small ghost", type: "button", text: t("actionClear"), onclick: () => apply(entry.name, "remove") })
    );
  } else if (entry.action === "block") {
    actions.append(
      el("button", { class: "btn small", type: "button", text: t("actionAllow"), onclick: () => apply(entry.name, "allow") })
    );
  } else {
    actions.append(
      el("button", { class: "btn small", type: "button", text: t("actionBlock"), onclick: () => apply(entry.name, "block") })
    );
  }

  const meta = [timeFormatter().format(new Date(entry.at)), entry.type, sourceLabel(entry.source)];
  const deviceName = deviceNames.get(entry.token);
  if (deviceName) meta.push(deviceName);
  if (entry.rule && entry.rule !== entry.name) meta.push(entry.rule);
  if (known) meta.push(known === "allow" ? t("actionAllow") : t("actionBlock"));

  return el("div", { class: classes.join(" ") }, [
    el("div", { class: "log-name", text: entry.name }),
    el("div", { class: "log-meta", text: meta.join(" · ") }),
    actions
  ]);
}

export function renderLog(container) {
  const state = currentState();
  if (!state) return;
  const ruleMap = new Map();
  const deviceNames = new Map((state.devices || []).map((item) => [item.token, item.name]));
  const listNode = el("div", { class: "card tight" });

  const paint = async () => {
    clear(listNode);
    listNode.append(el("div", { class: "empty", text: t("statusLoading") }));
    try {
      const [{ log }, { rules }] = await Promise.all([
        loadLog({ action: filter, q: search, token: device }),
        loadRules()
      ]);
      ruleMap.clear();
      for (const rule of rules) ruleMap.set(rule.host, rule.action);
      clear(listNode);
      if (log.length === 0) {
        listNode.append(el("div", { class: "empty", text: t("noActivity") }));
        return;
      }
      for (const entry of log) listNode.append(rowNode(entry, ruleMap, deviceNames, paint));
    } catch {
      clear(listNode);
      listNode.append(el("div", { class: "empty", text: t("requestFailed") }));
    }
  };

  const chips = el("div", { class: "chips" });
  for (const [value, key] of FILTERS) {
    chips.append(
      el("button", {
        class: "chip",
        type: "button",
        "aria-pressed": value === filter ? "true" : "false",
        text: t(key),
        onclick: () => {
          filter = value;
          for (const node of chips.children) node.setAttribute("aria-pressed", "false");
          chips.children[FILTERS.findIndex((item) => item[0] === value)].setAttribute("aria-pressed", "true");
          paint();
        }
      })
    );
  }

  const bar = el("div", { class: "list-bar" });
  container.append(bar);
  bar.append(el("h1", { text: t("logTitle") }));
  bar.append(chips);

  const fields = el("div", { class: "row" }, [
    el("input", {
      type: "search",
      class: "grow",
      placeholder: t("searchDomains"),
      value: search,
      oninput: (event) => {
        search = event.target.value.trim();
        clearTimeout(controls.dataset.timer);
        controls.dataset.timer = String(setTimeout(paint, 250));
      }
    })
  ]);
  const controls = el("div", { class: "card tight" }, [fields]);
  if ((state.devices || []).length > 0) {
    fields.append(
      el(
        "select",
        {
          class: "grow",
          onchange: (event) => {
            device = event.target.value;
            paint();
          }
        },
        [
          el("option", { value: "", text: t("allDevices"), selected: device === "" }),
          ...state.devices.map((item) => el("option", { value: item.token, text: item.name, selected: device === item.token }))
        ]
      )
    );
  }
  bar.append(controls);
  container.append(listNode);
  paint();
}
