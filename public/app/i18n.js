const STORAGE_KEY = "adblock.lang";
const LANGUAGES = ["en", "el"];

const STRINGS = {
  tabHome: ["Home", "Αρχική"],
  tabProtection: ["Protection", "Προστασία"],
  tabSettings: ["Settings", "Ρυθμίσεις"],

  statusOn: ["Active", "Ενεργό"],
  statusOff: ["Paused", "Σε παύση"],
  statusLoading: ["Loading", "Φόρτωση"],
  statusFailed: ["Offline", "Χωρίς σύνδεση"],
  statusSignIn: ["Sign in", "Σύνδεση"],

  signInTitle: ["Sign in", "Σύνδεση"],
  signInNote: [
    "The dashboard is private. Your devices keep resolving DNS either way.",
    "Ο πίνακας είναι ιδιωτικός. Οι συσκευές σου συνεχίζουν κανονικά."
  ],

  protection: ["Protection", "Προστασία"],
  protectionOn: ["Protection is enabled", "Προστασία ενεργή"],
  protectionOff: ["Protection is disabled", "Προστασία ανενεργή"],

  listBuilt: ["Built {when}", "Χτίστηκε {when}"],

  noActivity: ["No queries yet.", "Κανένα ερώτημα ακόμα."],

  logTitle: ["Activity", "Δραστηριότητα"],
  filterAll: ["All", "Όλα"],
  filterBlocked: ["Blocked", "Μπλοκαρισμένα"],
  filterAllowed: ["Allowed", "Πέρασαν"],
  searchDomains: ["Search last 24 hours", "Αναζήτηση τελευταίου 24ώρου"],
  allDevices: ["All devices", "Όλες οι συσκευές"],
  actionAllow: ["Allow", "Εξαίρεση"],
  actionBlock: ["Block", "Μπλοκάρισμα"],
  actionClear: ["Remove rule", "Αφαίρεση κανόνα"],
  ruleAdded: ["Rule saved", "Ο κανόνας αποθηκεύτηκε"],
  ruleRemoved: ["Rule removed", "Ο κανόνας αφαιρέθηκε"],
  viaList: ["Blocked by the list", "Μπλοκαρίστηκε από τη λίστα"],
  viaCname: ["Blocked behind a CNAME", "Μπλοκαρίστηκε πίσω από CNAME"],
  viaCache: ["Allowed, from cache", "Πέρασε από τη μνήμη"],
  viaStale: ["Allowed, from cache, refreshing", "Πέρασε από τη μνήμη, ανανεώνεται"],
  viaCustom: ["Blocked by your rule", "Μπλοκαρίστηκε από δικό σου κανόνα"],
  viaAllow: ["Allowed by your rule", "Πέρασε από δικό σου κανόνα"],
  viaNone: ["Allowed", "Πέρασε"],
  viaOff: ["Protection off", "Προστασία ανενεργή"],

  protectionTitle: ["Protection", "Προστασία"],
  noSources: ["No list is set, so only your own rules apply.", "Δεν έχει οριστεί λίστα, οπότε ισχύουν μόνο οι δικοί σου κανόνες."],
  sourceUrl: ["https://… list URL", "https://… URL λίστας"],
  sourceName: ["Name, or leave it blank", "Όνομα, ή άφησέ το κενό"],
  addSource: ["Add list", "Προσθήκη λίστας"],
  sourcePending: ["Applies on the next build", "Θα ισχύσει στο επόμενο build"],
  sourceBuilding: ["Building now", "Χτίζεται τώρα"],
  invalidUrl: ["That is not an https URL.", "Δεν είναι https URL."],
  sourcesTitle: ["Sources", "Πηγές"],
  sourceDomains: ["{count} domains", "{count} domains"],
  myRules: ["Your rules", "Οι κανόνες σου"],
  noRules: ["No rules of your own yet.", "Δεν έχεις δικούς σου κανόνες ακόμα."],
  hostPlaceholder: ["domain.com", "domain.com"],

  settingsTitle: ["Settings", "Ρυθμίσεις"],
  appearance: ["Appearance", "Εμφάνιση"],
  resolverTitle: ["Upstream resolver", "Resolver"],
  resolverNote: [
    "One encrypted DoH URL. Every query that is not blocked or cached is sent there once, with no second resolver and no retry.",
    "Ένα κρυπτογραφημένο DoH URL. Κάθε ερώτημα που δεν μπλοκάρεται και δεν είναι στην cache στέλνεται εκεί μία φορά, χωρίς δεύτερο resolver και χωρίς επανάληψη."
  ],
  resolverPlaceholder: ["https://…/dns-query", "https://…/dns-query"],
  logging: ["Log DNS queries", "Καταγραφή ερωτημάτων DNS"],
  loggingNote: ["Kept for 24 hours, then deleted automatically.", "Κρατιούνται για 24 ώρες και μετά σβήνονται αυτόματα."],
  devicesTitle: ["Devices", "Συσκευές"],
  devicesNote: [
    "Each device gets its own address, so Activity can tell them apart.",
    "Κάθε συσκευή παίρνει δική της διεύθυνση, ώστε η Δραστηριότητα να τις ξεχωρίζει."
  ],
  deviceName: ["Device name", "Όνομα συσκευής"],
  platformApple: ["Apple", "Apple"],
  platformOther: ["Other", "Άλλη"],
  addDevice: ["Add device", "Προσθήκη συσκευής"],
  revealAddress: ["Show", "Εμφάνιση"],
  hideAddress: ["Hide", "Απόκρυψη"],
  copyUrl: ["Copy", "Αντιγραφή"],
  appleProfile: ["Apple profile", "Προφίλ Apple"],
  copied: ["Copied", "Αντιγράφηκε"],
  removeDevice: ["Remove device", "Αφαίρεση συσκευής"],
  removeDeviceBody: [
    "\"{name}\" stops resolving through this app.",
    "Το «{name}» σταματά να περνά από αυτή την εφαρμογή."
  ],
  neverSeen: ["Never used", "Δεν χρησιμοποιήθηκε ποτέ"],
  lastSeen: ["Last query {when}", "Τελευταίο ερώτημα {when}"],
  theme: ["Theme", "Θέμα"],
  themeSystem: ["System", "Σύστημα"],
  themeLight: ["Light", "Φωτεινό"],
  themeDark: ["Dark", "Σκούρο"],
  themeBlack: ["Black", "Μαύρο"],
  languageLabel: ["Language", "Γλώσσα"],
  save: ["Save", "Αποθήκευση"],
  saved: ["Saved", "Αποθηκεύτηκε"],
  remove: ["Remove", "Αφαίρεση"],
  cancel: ["Cancel", "Άκυρο"],
  requestFailed: ["Something went wrong.", "Κάτι πήγε στραβά."],
  invalidResolver: ["Not a DoH URL. It must start with https://", "Δεν είναι DoH URL. Πρέπει να ξεκινά με https://"],
  resolverUnreachable: [
    "That resolver did not answer a test query, so it was not saved.",
    "Ο resolver δεν απάντησε σε δοκιμαστικό ερώτημα, οπότε δεν αποθηκεύτηκε."
  ],
  justNow: ["just now", "μόλις τώρα"],
  minutesAgo: ["{count} minutes ago", "πριν από {count} λεπτά"],
  hoursAgo: ["{count} hours ago", "πριν από {count} ώρες"],
  daysAgo: ["{count} days ago", "πριν από {count} μέρες"]
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

export function relativeTime(value) {
  if (!value) return t("neverSeen");
  const elapsed = Date.now() - value;
  if (elapsed < 60000) return t("justNow");
  if (elapsed < 3600000) return t("minutesAgo", { count: Math.round(elapsed / 60000) });
  if (elapsed < 86400000) return t("hoursAgo", { count: Math.round(elapsed / 3600000) });
  return t("daysAgo", { count: Math.round(elapsed / 86400000) });
}

export function sourceLabel(source) {
  const map = { list: "viaList", custom: "viaCustom", allow: "viaAllow", none: "viaNone", off: "viaOff", cname: "viaCname", cache: "viaCache", stale: "viaStale" };
  return map[source] ? t(map[source]) : source;
}
