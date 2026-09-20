const TAB_SELECTOR = ".tabbar";
const TOP_SELECTOR = ".topbar";

function syncBars() {
  const root = document.documentElement;
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
  syncBars();
  const observer = new ResizeObserver(syncBars);
  for (const selector of [TOP_SELECTOR, TAB_SELECTOR]) {
    const node = document.querySelector(selector);
    if (node) observer.observe(node);
  }
  addEventListener("resize", syncBars);
  addEventListener("orientationchange", syncBars);
}
