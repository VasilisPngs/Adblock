import { t } from "./i18n.js";

export function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === null || value === undefined || value === false) continue;
    if (key === "class") node.className = value;
    else if (key === "text") node.textContent = value;
    else if (key === "dataset") Object.assign(node.dataset, value);
    else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key in node) node[key] = value;
    else node.setAttribute(key, value);
  }
  append(node, children);
  return node;
}

function append(node, children) {
  for (const child of [].concat(children)) {
    if (child === null || child === undefined || child === false || child === "") continue;
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) node.firstChild.remove();
  return node;
}

export function formatNumber(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) return "-";
  const rounded = Math.round(value * 10 ** digits) / 10 ** digits;
  return String(rounded);
}

export function toast(message) {
  const host = document.getElementById("toast-host");
  const node = el("div", { class: "toast", text: message });
  host.append(node);
  setTimeout(() => {
    node.style.opacity = "0";
    node.style.transition = "opacity .25s ease";
    setTimeout(() => node.remove(), 260);
  }, 2200);
}

function openSheet(build, onClose) {
  const host = document.getElementById("sheet-host");
  const sheet = el("div", { class: "sheet" });
  const backdrop = el("div", { class: "sheet-backdrop" }, sheet);
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    backdrop.style.opacity = "0";
    backdrop.style.transition = "opacity .2s ease";
    setTimeout(() => backdrop.remove(), 200);
    document.removeEventListener("keydown", onKey);
    if (onClose) onClose();
  };
  const onKey = (event) => {
    if (event.key === "Escape") close();
  };
  backdrop.addEventListener("click", (event) => {
    if (event.target === backdrop) close();
  });
  document.addEventListener("keydown", onKey);
  append(sheet, build(close));
  host.append(backdrop);
  const focusable = sheet.querySelector("input, select, textarea, button");
  if (focusable && !matchMedia("(pointer: coarse)").matches) focusable.focus();
  return close;
}

export function confirmSheet(title, message, confirmLabel) {
  return new Promise((resolve) => {
    let answer = false;
    openSheet(
      (close) => [
        el("h2", { text: title }),
        el("p", { class: "muted", text: message }),
        el("div", { class: "row" }, [
          el("button", { class: "btn grow", type: "button", text: t("cancel"), onclick: close }),
          el("button", {
            class: "btn primary grow",
            type: "button",
            text: confirmLabel || t("remove"),
            onclick: () => {
              answer = true;
              close();
            }
          })
        ])
      ],
      () => resolve(answer)
    );
  });
}

