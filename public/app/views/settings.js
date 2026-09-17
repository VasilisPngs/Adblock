import { el, clear, toast, confirmSheet } from "../dom.js";
import { t, relativeTime, languages, language, setLanguage } from "../i18n.js";
import { themeMode, themeModes, setTheme } from "../theme.js";
import { currentState, saveSettings, addDevice, removeDevice } from "../api.js";

function field(label, control) {
  return el("label", { class: "field" }, [el("span", { class: "tiny", text: label }), control]);
}

function resolverCard(settings) {
  const card = el("div", { class: "card" }, [
    el("h2", { text: t("resolversTitle") }),
    el("div", { class: "tiny", text: t("resolversNote") })
  ]);
  let values = settings.resolvers.length > 0 ? [...settings.resolvers] : [""];
  const rows = el("div", {});

  const commit = async () => {
    const resolvers = values.map((value) => value.trim()).filter(Boolean);
    try {
      const saved = await saveSettings({ resolvers });
      values = saved.resolvers.length > 0 ? [...saved.resolvers] : [""];
      paint();
      toast(t("saved"));
    } catch (error) {
      const detail = Array.isArray(error.detail) ? error.detail.join(", ") : error.detail || "";
      if (error.code === "cloudflare_ip_needs_doh") toast(t("cloudflareNeedsDoh", { detail }));
      else if (error.code === "invalid_resolver") toast(t("invalidResolver", { detail }));
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
    el("div", { class: "row", style: "padding:0 14px 14px;gap:8px" }, [
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

function deviceCard(state) {
  const card = el("div", { class: "card" }, [
    el("h2", { text: t("devicesTitle") }),
    el("div", { class: "tiny", text: t("devicesNote") })
  ]);
  const list = el("div", { class: "list" });
  for (const device of state.devices) {
    const url = `https://${state.host}/dns-query/${device.token}`;
    list.append(
      el("div", { class: "list-item" }, [
        el("span", { class: "grow" }, [
          el("div", { text: device.name }),
          el("div", {
            class: "tiny",
            text: device.last_seen_at ? t("lastSeen", { when: relativeTime(device.last_seen_at) }) : t("neverSeen")
          }),
          el("div", { class: "tiny", style: "overflow-wrap:anywhere;color:var(--accent)", text: url })
        ]),
        el("span", { class: "row wrap", style: "gap:6px;justify-content:flex-end" }, [
          el("button", {
            class: "btn small ghost",
            type: "button",
            text: t("copyUrl"),
            onclick: async () => {
              try {
                await navigator.clipboard.writeText(url);
                toast(t("copied"));
              } catch {
                toast(url);
              }
            }
          }),
          el("a", { class: "btn small ghost", href: `/profile.mobileconfig?token=${device.token}`, text: t("appleProfile") }),
          el("button", {
            class: "btn small ghost danger",
            type: "button",
            text: t("remove"),
            onclick: async () => {
              const confirmed = await confirmSheet(t("removeDevice"), t("removeDeviceBody", { name: device.name }), t("remove"));
              if (!confirmed) return;
              try {
                await removeDevice(device.token);
              } catch {
                toast(t("requestFailed"));
              }
            }
          })
        ])
      ])
    );
  }
  card.append(list);

  let name = "";
  const input = el("input", {
    type: "text",
    placeholder: t("deviceName"),
    oninput: (event) => {
      name = event.target.value;
    }
  });
  card.append(
    el("div", { class: "resolver-row" }, [
      input,
      el("button", {
        class: "btn small primary",
        type: "button",
        text: t("addDevice"),
        onclick: async () => {
          if (!name.trim()) return;
          try {
            await addDevice(name.trim());
            name = "";
            input.value = "";
          } catch {
            toast(t("requestFailed"));
          }
        }
      })
    ])
  );
  return card;
}

export function renderSettings(container) {
  const state = currentState();
  if (!state) return;
  const { settings } = state;

  container.append(el("h1", { text: t("settingsTitle") }));
  container.append(resolverCard(settings));

  container.append(
    el("div", { class: "card" }, [
      field(
        t("blockAnswer"),
        el(
          "select",
          {
            onchange: async (event) => {
              try {
                await saveSettings({ blockMode: event.target.value });
                toast(t("saved"));
              } catch {
                toast(t("requestFailed"));
              }
            }
          },
          [
            el("option", { value: "zero", text: t("answerZero"), selected: settings.blockMode === "zero" }),
            el("option", { value: "nxdomain", text: t("answerNxdomain"), selected: settings.blockMode === "nxdomain" })
          ]
        )
      ),
      el("div", { class: "switch" }, [
        el("span", { class: "grow", text: t("logging") }),
        el(
          "button",
          {
            class: "switch-track",
            type: "button",
            "aria-pressed": settings.logEnabled ? "true" : "false",
            "aria-label": t("logging"),
            onclick: async () => {
              try {
                await saveSettings({ logEnabled: !settings.logEnabled });
              } catch {
                toast(t("requestFailed"));
              }
            }
          },
          [el("span", { class: "switch-knob" })]
        )
      ]),
      field(
        t("logDays"),
        el(
          "select",
          {
            onchange: async (event) => {
              try {
                await saveSettings({ logDays: Number(event.target.value) });
                toast(t("saved"));
              } catch {
                toast(t("requestFailed"));
              }
            }
          },
          [1, 3, 7, 14, 30].map((days) =>
            el("option", { value: String(days), text: String(days), selected: settings.logDays === days })
          )
        )
      )
    ])
  );

  container.append(deviceCard(state));

  container.append(
    el("div", { class: "card" }, [
      field(
        t("theme"),
        el(
          "select",
          { onchange: (event) => setTheme(event.target.value) },
          themeModes().map((mode) =>
            el("option", {
              value: mode,
              text: t(`theme${mode[0].toUpperCase()}${mode.slice(1)}`),
              selected: mode === themeMode()
            })
          )
        )
      ),
      field(
        t("languageLabel"),
        el(
          "select",
          { onchange: (event) => setLanguage(event.target.value) },
          languages().map((code) =>
            el("option", { value: code, text: code === "el" ? "Ελληνικά" : "English", selected: code === language() })
          )
        )
      )
    ])
  );
}
