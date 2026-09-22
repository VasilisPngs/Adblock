import { el, toast, confirmSheet } from "../dom.js";
import { t, relativeTime, languages, language, setLanguage } from "../i18n.js";
import { themeMode, themeModes, setTheme } from "../theme.js";
import { currentState, saveSettings, addDevice, removeDevice, signOut, changePassword, resetCounters } from "../api.js";

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
        el("span", { class: "row wrap", style: "gap:6px;justify-content:flex-end" }, [
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
          device.platform === "other"
            ? null
            : el("a", { class: "btn small ghost", href: `/profile.mobileconfig?token=${device.token}`, text: t("appleProfile") }),
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
  let platform = "apple";
  const input = el("input", {
    type: "text",
    autocomplete: "off",
    placeholder: t("deviceName"),
    oninput: (event) => {
      name = event.target.value;
    }
  });
  const platformInput = el(
    "select",
    {
      style: "width:auto",
      onchange: (event) => {
        platform = event.target.value;
      }
    },
    [
      el("option", { value: "apple", text: t("platformApple") }),
      el("option", { value: "other", text: t("platformOther") })
    ]
  );
  card.append(
    el("div", { class: "resolver-row" }, [
      input,
      platformInput,
      el("button", {
        class: "btn small primary",
        type: "button",
        text: t("addDevice"),
        onclick: async () => {
          if (!name.trim()) return;
          try {
            await addDevice(name.trim(), platform);
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
      el("div", { class: "tiny", text: t("loggingNote") })
    ])
  );

  container.append(
    el("div", { class: "card" }, [
      el("h2", { text: t("countersTitle") }),
      el("div", { class: "tiny", text: t("countersNote") }),
      el("button", {
        class: "btn ghost danger",
        type: "button",
        text: t("resetCounters"),
        onclick: async () => {
          const confirmed = await confirmSheet(t("resetCounters"), t("resetCountersBody"), t("reset"));
          if (!confirmed) return;
          try {
            await resetCounters();
            toast(t("countersCleared"));
          } catch {
            toast(t("requestFailed"));
          }
        }
      })
    ])
  );

  container.append(deviceCard(state));

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
