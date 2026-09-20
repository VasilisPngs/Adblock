const TAB_SELECTOR = ".tabbar";

function syncTabbar() {
  const bar = document.querySelector(TAB_SELECTOR);
  const root = document.documentElement;
  if (!bar) return;
  const bottom = getComputedStyle(bar).getPropertyValue("--bottom-bar").trim() === "1";
  if (bottom) root.style.setProperty("--tabbar", `${Math.round(bar.getBoundingClientRect().height)}px`);
  else root.style.removeProperty("--tabbar");
}

export function scrollViewTop() {
  const view = document.querySelector(".view");
  if (view) view.scrollTo({ top: 0, behavior: "instant" });
}

export function startShell() {
  for (const name of ["gesturestart", "gesturechange", "gestureend"]) {
    addEventListener(name, (event) => event.preventDefault(), { passive: false });
  }
  const bar = document.querySelector(TAB_SELECTOR);
  if (!bar) return;
  syncTabbar();
  new ResizeObserver(syncTabbar).observe(bar);
  addEventListener("resize", syncTabbar);
  addEventListener("orientationchange", syncTabbar);
}
