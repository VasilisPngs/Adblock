import { el, toast, confirmSheet } from "../dom.js";
import { t, relativeTime, languages, language, setLanguage } from "../i18n.js";
import { themeMode, themeModes, setTheme } from "../theme.js";
import { currentState, saveSettings, addDevice, removeDevice, signOut, changePassword } from "../api.js";

function field(label, control) {
  return el("label", { class: "field" }, [el("span", { class: "tiny", text: label }), control]);
}

function passwordCard() {
  let current = "";
  let next = "";
  const currentInput = el("input", {
    type: "password",
    autocomplete: "current-password",
    placeholder: t("currentPassword"),
    oninput: (event) => {
      current = event.target.value;
    }
  });
  const nextInput = el("input", {
    type: "password",
    autocomplete: "new-password",
    placeholder: t("newPassword"),
    oninput: (event) => {
      next = event.target.value;
    }
  });
  const submit = async () => {
    if (!current || !next) return;
    currentInput.blur();
    nextInput.blur();
    try {
      await changePassword(current, next);
      currentInput.value = "";
      nextInput.value = "";
      current = "";
      next = "";
      toast(t("passwordChanged"));
    } catch (error) {
      if (error.code === "weak_password") toast(t("weakPassword", { count: error.detail }));
      else if (error.code === "managed_by_secret") toast(t("managedBySecret"));
      else if (error.code === "wrong_password") toast(t("wrongPassword"));
      else toast(t("requestFailed"));
    }
  };
  return el("div", { class: "card" }, [
    el("h2", { text: t("passwordTitle") }),
    currentInput,
    nextInput,
    el("button", { class: "btn small primary", type: "button", text: t("changePassword"), onclick: submit })
  ]);
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
          el("div", { style: "overflow-wrap:anywhere", text: device.name }),
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

  container.append(
    el("div", { class: "card" }, [
      el("div", { class: "switch boxed" }, [
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

  container.append(passwordCard());

  container.append(
    el("div", { class: "card" }, [
      el("button", {
        class: "btn ghost",
        type: "button",
        text: t("signOut"),
        onclick: async () => {
          try {
            await signOut();
          } catch {
            toast(t("requestFailed"));
          }
        }
      })
    ])
  );
}
