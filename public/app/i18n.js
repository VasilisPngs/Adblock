const STORAGE_KEY = "adblock.lang";
const LANGUAGES = ["en", "el"];

const STRINGS = {
  tabHome: ["Home", "Αρχική"],
  tabLog: ["Activity", "Κινήσεις"],
  tabLists: ["Lists", "Λίστες"],
  tabSettings: ["Settings", "Ρυθμίσεις"],

  statusOn: ["Protected", "Προστασία"],
  statusOff: ["Paused", "Σε παύση"],
  statusLoading: ["Loading", "Φόρτωση"],
  statusFailed: ["Not reachable", "Χωρίς σύνδεση"],
  statusSignIn: ["Sign in", "Σύνδεση"],

  signInTitle: ["Sign in", "Σύνδεση"],
  signInNote: [
    "The dashboard is private. Your devices keep resolving either way.",
    "Ο πίνακας είναι ιδιωτικός. Οι συσκευές σου συνεχίζουν να λύνουν DNS κανονικά."
  ],
  passwordLabel: ["Password", "Κωδικός"],
  wrongPassword: ["Wrong password.", "Λάθος κωδικός."],
  wrongPasswordLeft: [
    "Wrong password. {count} attempts left before a pause.",
    "Λάθος κωδικός. Απομένουν {count} προσπάθειες πριν την παύση."
  ],
  tooManyAttempts: [
    "Too many wrong attempts. Try again in a few minutes.",
    "Πολλές λάθος προσπάθειες. Δοκίμασε ξανά σε λίγα λεπτά."
  ],
  passwordMissing: [
    "No password is set yet. Add DASHBOARD_PASSWORD as a secret on the Worker.",
    "Δεν έχει οριστεί κωδικός. Πρόσθεσε το DASHBOARD_PASSWORD ως secret στον Worker."
  ],
  signOut: ["Sign out", "Αποσύνδεση"],

  blockingTitle: ["Blocking", "Μπλοκάρισμα"],
  blockingOn: ["Everything on the lists is blocked.", "Μπλοκάρεται ό,τι υπάρχει στις λίστες."],
  blockingOff: ["Every request passes through untouched.", "Κάθε αίτημα περνά ανέπαφο."],

  blockedToday: ["Blocked today", "Μπλοκαρίστηκαν σήμερα"],
  allowedToday: ["Allowed today", "Πέρασαν σήμερα"],
  blockRate: ["Blocked share", "Ποσοστό μπλοκαρίσματος"],
  errorsToday: ["Failures", "Αποτυχίες"],

  listSummary: ["{domains} domains in the list", "{domains} domains στη λίστα"],
  listBuilt: ["Built {when}", "Χτίστηκε {when}"],
  customRules: ["{count} of your own rules", "{count} δικοί σου κανόνες"],

  topDomains: ["Most asked today", "Τα πιο ζητημένα σήμερα"],
  noActivity: ["No queries yet.", "Κανένα ερώτημα ακόμα."],

  logTitle: ["Activity", "Κινήσεις"],
  filterAll: ["All", "Όλα"],
  filterBlocked: ["Blocked", "Μπλοκαρισμένα"],
  filterAllowed: ["Allowed", "Πέρασαν"],
  filterErrors: ["Failures", "Αποτυχίες"],
  searchDomains: ["Search domains", "Αναζήτηση domain"],
  allDevices: ["All devices", "Όλες οι συσκευές"],
  actionAllow: ["Allow", "Επιτρέπω"],
  actionBlock: ["Block", "Μπλοκάρω"],
  actionClear: ["Remove rule", "Αφαίρεση κανόνα"],
  ruleAdded: ["Rule saved", "Ο κανόνας αποθηκεύτηκε"],
  ruleRemoved: ["Rule removed", "Ο κανόνας αφαιρέθηκε"],
  viaList: ["List", "Λίστα"],
  viaCustom: ["Your rule", "Δικός σου κανόνας"],
  viaAllow: ["Your rule", "Δικός σου κανόνας"],
  viaNone: ["Passed", "Πέρασε"],
  viaOff: ["Blocking paused", "Μπλοκάρισμα σε παύση"],

  listsTitle: ["Lists", "Λίστες"],
  sourcesTitle: ["Sources", "Πηγές"],
  sourceDomains: ["{count} domains", "{count} domains"],
  sourcesNote: [
    "Sources live in blocklists.json and are compiled on every deploy.",
    "Οι πηγές ζουν στο blocklists.json και χτίζονται σε κάθε deploy."
  ],
  myRules: ["Your rules", "Οι κανόνες σου"],
  noRules: ["Nothing of your own yet.", "Τίποτα δικό σου ακόμα."],
  addRule: ["Add", "Πρόσθεσε"],
  hostPlaceholder: ["domain.com", "domain.com"],

  settingsTitle: ["Settings", "Ρυθμίσεις"],
  resolversTitle: ["Upstream resolvers", "Resolvers"],
  resolversNote: [
    "A DoH URL or a plain IP. Tried in order, the first that answers wins.",
    "DoH URL ή σκέτη IP. Δοκιμάζονται με σειρά, κερδίζει ο πρώτος που απαντά."
  ],
  resolverPlaceholder: ["https://… or 1.2.3.4", "https://… ή 1.2.3.4"],
  addResolver: ["Add resolver", "Πρόσθεσε resolver"],
  blockAnswer: ["Answer for blocked names", "Απάντηση στα μπλοκαρισμένα"],
  answerZero: ["0.0.0.0", "0.0.0.0"],
  answerNxdomain: ["NXDOMAIN", "NXDOMAIN"],
  logging: ["Keep an activity log", "Κρατά ιστορικό κινήσεων"],
  logDays: ["Days kept", "Μέρες διατήρησης"],
  devicesTitle: ["Devices", "Συσκευές"],
  devicesNote: [
    "Each device gets its own address, so the log can tell them apart.",
    "Κάθε συσκευή παίρνει δική της διεύθυνση, ώστε το ιστορικό να τις ξεχωρίζει."
  ],
  deviceName: ["Device name", "Όνομα συσκευής"],
  addDevice: ["Add device", "Πρόσθεσε συσκευή"],
  copyUrl: ["Copy address", "Αντιγραφή διεύθυνσης"],
  appleProfile: ["Apple profile", "Προφίλ Apple"],
  copied: ["Copied", "Αντιγράφηκε"],
  removeDevice: ["Remove device", "Αφαίρεση συσκευής"],
  removeDeviceBody: [
    "«{name}» stops resolving through this app.",
    "Το «{name}» σταματά να περνά από αυτή την εφαρμογή."
  ],
  neverSeen: ["Never used", "Δεν χρησιμοποιήθηκε"],
  lastSeen: ["Last query {when}", "Τελευταίο ερώτημα {when}"],
  theme: ["Theme", "Θέμα"],
  themeSystem: ["System", "Σύστημα"],
  themeLight: ["Light", "Φωτεινό"],
  themeDark: ["Dark", "Σκούρο"],
  themeBlack: ["True black", "Απόλυτο μαύρο"],
  languageLabel: ["Language", "Γλώσσα"],
  save: ["Save", "Αποθήκευση"],
  saved: ["Saved", "Αποθηκεύτηκε"],
  remove: ["Remove", "Αφαίρεση"],
  cancel: ["Cancel", "Άκυρο"],
  requestFailed: ["That did not go through.", "Δεν πέρασε."],
  invalidResolver: ["Not a DoH URL or an IP address: {detail}", "Δεν είναι DoH URL ούτε IP: {detail}"],
  cloudflareNeedsDoh: [
    "{detail} belongs to Cloudflare. Workers cannot open TCP to their own network, so give the DoH URL instead.",
    "Το {detail} ανήκει στην Cloudflare. Οι Workers δεν ανοίγουν TCP προς το δικό τους δίκτυο, βάλε το DoH URL αντ' αυτού."
  ],
  noResolver: [
    "No resolver is set, so nothing can be answered.",
    "Δεν έχει οριστεί resolver, άρα τίποτα δεν μπορεί να απαντηθεί."
  ],
  justNow: ["just now", "τώρα"],
  minutesAgo: ["{count} min ago", "πριν {count} λεπτά"],
  hoursAgo: ["{count} h ago", "πριν {count} ώρες"],
  daysAgo: ["{count} days ago", "πριν {count} μέρες"]
};

const PLURALS = {
  query: [["query", "queries"], ["ερώτημα", "ερωτήματα"]],
  rule: [["rule", "rules"], ["κανόνας", "κανόνες"]],
  device: [["device", "devices"], ["συσκευή", "συσκευές"]]
};

export const i18nEvents = new EventTarget();

function detect() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (LANGUAGES.includes(stored)) return stored;
  } catch {}
  return (navigator.language || "en").toLowerCase().startsWith("el") ? "el" : "en";
}

let current = detect();
let index = LANGUAGES.indexOf(current);

export const language = () => current;
export const languages = () => [...LANGUAGES];
export const locale = () => (current === "el" ? "el-GR" : "en-GB");

export function setLanguage(next) {
  if (!LANGUAGES.includes(next) || next === current) return;
  current = next;
  index = LANGUAGES.indexOf(next);
  try {
    localStorage.setItem(STORAGE_KEY, next);
  } catch {}
  document.documentElement.lang = next;
  i18nEvents.dispatchEvent(new CustomEvent("changed"));
}

export function applyLanguage() {
  document.documentElement.lang = current;
}

export function t(key, params) {
  const entry = STRINGS[key];
  if (!entry) return key;
  const value = entry[index] || entry[0];
  if (!params) return value;
  return value.replace(/\{(\w+)\}/g, (match, name) => (params[name] === undefined ? match : String(params[name])));
}

export function tn(count, key) {
  const entry = PLURALS[key];
  if (!entry) return `${count} ${key}`;
  const forms = entry[index] || entry[0];
  return `${count} ${count === 1 ? forms[0] : forms[1]}`;
}

export function relativeTime(value) {
  if (!value) return t("neverSeen");
  const elapsed = Date.now() - value;
  if (elapsed < 60000) return t("justNow");
  if (elapsed < 3600000) return t("minutesAgo", { count: Math.round(elapsed / 60000) });
  if (elapsed < 86400000) return t("hoursAgo", { count: Math.round(elapsed / 3600000) });
  return t("daysAgo", { count: Math.round(elapsed / 86400000) });
}

export function sourceLabel(source) {
  const map = { list: "viaList", custom: "viaCustom", allow: "viaAllow", none: "viaNone", off: "viaOff" };
  return map[source] ? t(map[source]) : source;
}
