import { el, toast, confirmSheet } from "../dom.js";
import { t, relativeTime, languages, language, setLanguage } from "../i18n.js";
import { themeMode, themeModes, setTheme } from "../theme.js";
import { currentState, saveSettings, addDevice, removeDevice } from "../api.js";

function field(label, control) {
  return el("label", { class: "field" }, [el("span", { class: "tiny", text: label }), control]);
}

function deviceCard(state) {
  const card = el("div", { class: "card" }, [
    el("h2", { text: t("devicesTitle") }),
    el("div", { class: "tiny", text: t("devicesNote") })
  ]);
  const list = el("div", { class: "list" });
  for (const device of state.devices) {
    const url = `https://${state.host}/dns-query/${device.token}`;
    const masked = `https://${state.host}/dns-query/${"\u2022".repeat(8)}${device.token.slice(-5)}`;
    const address = el("div", {
      class: "tiny",
      style: "overflow-wrap:anywhere;color:var(--accent-text)",
      text: masked
    });
    let shown = false;
    const reveal = el("button", {
      class: "btn small ghost",
      type: "button",
      text: t("revealAddress"),
      onclick: () => {
        shown = !shown;
        address.textContent = shown ? url : masked;
        reveal.textContent = shown ? t("hideAddress") : t("revealAddress");
      }
    });
    list.append(
      el("div", { class: "list-item" }, [
        el("span", { class: "grow" }, [
          el("div", { style: "overflow-wrap:anywhere", text: device.name }),
          el("div", {
            class: "tiny",
            text: device.last_seen_at ? t("lastSeen", { when: relativeTime(device.last_seen_at) }) : t("neverSeen")
          }),
          address
        ]),
        el("span", { class: "device-actions" }, [
          reveal,
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
          el("a", { class: "btn small ghost accent", href: `/profile.mobileconfig?token=${device.token}`, text: t("appleProfile") }),
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
    autocomplete: "off",
    placeholder: t("deviceName"),
    oninput: (event) => {
      name = event.target.value;
    }
  });
  card.append(
    el("div", { class: "resolver-row" }, [
      input,
      el("button", {
        class: "btn primary",
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

  container.append(el("div", { class: "list-bar" }, [el("h1", { text: t("settingsTitle") })]));

  container.append(
    el("div", { class: "card" }, [
      el("h2", { text: t("appearance") }),
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

  const loggingTrack = el(
    "button",
    {
      class: "switch-track",
      type: "button",
      "aria-pressed": settings.logEnabled ? "true" : "false",
      "aria-label": t("logging"),
      onclick: async () => {
        const next = loggingTrack.getAttribute("aria-pressed") !== "true";
        loggingTrack.setAttribute("aria-pressed", next ? "true" : "false");
        try {
          await saveSettings({ logEnabled: next }, true);
        } catch {
          loggingTrack.setAttribute("aria-pressed", currentState().settings.logEnabled ? "true" : "false");
          toast(t("requestFailed"));
        }
      }
    },
    [el("span", { class: "switch-knob" })]
  );
  container.append(
    el("div", { class: "card" }, [
      el("div", { class: "switch boxed" }, [el("span", { class: "grow", text: t("logging") }), loggingTrack]),
      el("div", { class: "tiny", text: t("loggingNote") })
    ])
  );

  container.append(deviceCard(state));
}
