const TAB_SELECTOR = ".tabbar";
const TOP_SELECTOR = ".topbar";

function installed() {
  return matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;
}

function screenHeight() {
  const long = Math.max(screen.width, screen.height);
  const short = Math.min(screen.width, screen.height);
  return innerHeight >= innerWidth ? long : short;
}

function syncShell() {
  const root = document.documentElement;
  if (installed()) root.style.setProperty("--shell-min", `${Math.max(screenHeight(), innerHeight) + 1}px`);
  else root.style.removeProperty("--shell-min");
  const top = document.querySelector(TOP_SELECTOR);
  if (top) root.style.setProperty("--topbar", `${Math.round(top.getBoundingClientRect().height)}px`);
  const bar = document.querySelector(TAB_SELECTOR);
  if (!bar) return;
  const bottom = getComputedStyle(bar).getPropertyValue("--bottom-bar").trim() === "1";
  if (bottom) root.style.setProperty("--tabbar", `${Math.round(bar.getBoundingClientRect().height)}px`);
  else root.style.removeProperty("--tabbar");
}

export function scrollViewTop() {
  scrollTo({ top: 0, behavior: "instant" });
}

export function startShell() {
  for (const name of ["gesturestart", "gesturechange", "gestureend"]) {
    addEventListener(name, (event) => event.preventDefault(), { passive: false });
  }
  syncShell();
  const observer = new ResizeObserver(syncShell);
  for (const selector of [TOP_SELECTOR, TAB_SELECTOR]) {
    const node = document.querySelector(selector);
    if (node) observer.observe(node);
  }
  addEventListener("resize", syncShell);
  addEventListener("orientationchange", syncShell);
  addEventListener("pageshow", syncShell);
}
