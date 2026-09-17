const ROUTES = [
  { pattern: /^\/$/, name: "home", params: () => ({}) },
  { pattern: /^\/log$/, name: "log", params: () => ({}) },
  { pattern: /^\/lists$/, name: "lists", params: () => ({}) },
  { pattern: /^\/settings$/, name: "settings", params: () => ({}) }
];

let renderer = null;

export function currentRoute() {
  const path = location.pathname;
  for (const route of ROUTES) {
    const match = path.match(route.pattern);
    if (match) return { name: route.name, params: route.params(match) };
  }
  return { name: "home", params: {} };
}

export function navigate(path, replace = false) {
  if (path === location.pathname) {
    if (renderer) renderer();
    return;
  }
  if (replace) history.replaceState({}, "", path);
  else history.pushState({}, "", path);
  if (renderer) renderer();
}

export function startRouter(fn) {
  renderer = fn;
  addEventListener("popstate", () => renderer());
  document.addEventListener("click", (event) => {
    const link = event.target.closest("a[data-link]");
    if (!link) return;
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const href = link.getAttribute("href");
    if (!href || !href.startsWith("/")) return;
    event.preventDefault();
    navigate(href);
  });
  renderer();
}
