const STORAGE_KEY = "adblock.lang";
const LANGUAGES = ["en", "el"];

const STRINGS = {
  tabHome: ["Home", "Αρχική"],
  tabLog: ["Activity", "Κινήσεις"],
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
  setupTitle: ["First run", "Πρώτη εκκίνηση"],
  setupNote: [
    "Cloudflare → Storage & Databases → D1 → adblock → Console, and run: SELECT setup_code FROM settings;",
    "Cloudflare → Storage & Databases → D1 → adblock → Console, και τρέξε: SELECT setup_code FROM settings;"
  ],
  setupCode: ["Setup code", "Κωδικός εγκατάστασης"],
  newPassword: ["New password", "Νέος κωδικός"],
  setupSubmit: ["Set the password", "Ορισμός κωδικού"],
  wrongCode: ["Wrong setup code.", "Λάθος κωδικός εγκατάστασης."],
  weakPassword: [
    "At least {count} characters, and nothing easy to guess.",
    "Τουλάχιστον {count} χαρακτήρες, και τίποτα που μαντεύεται εύκολα."
  ],
  passwordTitle: ["Dashboard password", "Κωδικός πίνακα"],
  currentPassword: ["Current password", "Τρέχων κωδικός"],
  changePassword: ["Change password", "Αλλαγή κωδικού"],
  passwordChanged: ["Password changed. Other devices are signed out.", "Ο κωδικός άλλαξε. Οι άλλες συσκευές αποσυνδέθηκαν."],
  managedBySecret: [
    "The password comes from the DASHBOARD_PASSWORD secret, so change it there.",
    "Ο κωδικός έρχεται από το secret DASHBOARD_PASSWORD, άλλαξέ τον εκεί."
  ],
  signOut: ["Sign out", "Αποσύνδεση"],

  blockingTitle: ["Blocking", "Μπλοκάρισμα"],
  blockingOn: ["Everything on the lists is blocked.", "Μπλοκάρεται ό,τι υπάρχει στις λίστες."],
  blockingOff: ["Every request passes through unfiltered.", "Όλα τα αιτήματα περνούν χωρίς φίλτρο."],

  queriesTotal: ["Queries", "Ερωτήματα"],
  blockedTotal: ["Blocked queries", "Μπλοκαρισμένα ερωτήματα"],

  listBuilt: ["Built {when}", "Χτίστηκε {when}"],

  topDomains: ["Most blocked", "Τα πιο μπλοκαρισμένα"],
  noActivity: ["No queries yet.", "Κανένα ερώτημα ακόμα."],

  logTitle: ["Activity", "Κινήσεις"],
  filterAll: ["All", "Όλα"],
  filterBlocked: ["Blocked", "Μπλοκαρισμένα"],
  filterAllowed: ["Allowed", "Πέρασαν"],
  filterErrors: ["Failures", "Αποτυχίες"],
  searchDomains: ["Search domains", "Αναζήτηση domain"],
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
  viaOff: ["Blocking paused", "Μπλοκάρισμα σε παύση"],
  viaNoResolver: ["No resolver set", "Δεν έχει οριστεί resolver"],

  protectionTitle: ["Protection", "Προστασία"],
  noSources: ["No list is set, so only your own rules apply.", "Δεν έχει οριστεί λίστα, οπότε ισχύουν μόνο οι δικοί σου κανόνες."],
  sourceUrl: ["https://… list URL", "https://… URL λίστας"],
  sourceName: ["Name, or leave it blank", "Όνομα, ή άφησέ το κενό"],
  addSource: ["Add list", "Προσθήκη λίστας"],
  sourcePending: ["Applies on the next build", "Θα ισχύσει στο επόμενο build"],
  sourceBuilding: ["Building now", "Χτίζεται τώρα"],
  rebuildTitle: ["Rebuild", "Ανανέωση λιστών"],
  deployHook: ["Deploy hook URL", "Deploy hook URL"],
  deployHookNote: [
    "Cloudflare → Worker → Settings → Builds → Deploy Hooks. Once it is saved, changing a list starts a build at once instead of waiting for the next scheduled one, which takes two to four minutes.",
    "Cloudflare → Worker → Settings → Builds → Deploy Hooks. Μόλις αποθηκευτεί, κάθε αλλαγή λίστας ξεκινά build αμέσως, χωρίς να περιμένει το επόμενο προγραμματισμένο. Το build θέλει δύο με τέσσερα λεπτά."
  ],
  deployHookSaved: ["Deploy hook saved", "Το deploy hook αποθηκεύτηκε"],
  deployHookReady: [
    "The lists rebuild by themselves every three hours, and again whenever you change one. This button is only for when you do not want to wait.",
    "Οι λίστες ξαναχτίζονται μόνες τους κάθε τρεις ώρες, και ξανά όποτε αλλάξεις κάποια. Αυτό το κουμπί είναι μόνο για όταν δεν θες να περιμένεις."
  ],
  changeHook: ["Change hook", "Αλλαγή hook"],
  rebuildNow: ["Rebuild now", "Ανανέωση τώρα"],
  rebuildStarted: ["Build started", "Το build ξεκίνησε"],
  rebuildNoHook: ["Save a deploy hook first.", "Αποθήκευσε πρώτα ένα deploy hook."],
  invalidUrl: ["That is not an https URL.", "Δεν είναι https URL."],
  sourcesTitle: ["Sources", "Πηγές"],
  sourceDomains: ["{count} domains", "{count} domains"],
  myRules: ["Your rules", "Οι κανόνες σου"],
  noRules: ["No rules of your own yet.", "Δεν έχεις δικούς σου κανόνες ακόμα."],
  hostPlaceholder: ["domain.com", "domain.com"],

  settingsTitle: ["Settings", "Ρυθμίσεις"],
  resolversTitle: ["Upstream resolvers", "Resolvers"],
  resolversNote: [
    "A DoH URL or a plain IP. Tried in order, the first that answers wins.",
    "DoH URL ή σκέτη IP. Δοκιμάζονται με σειρά, κερδίζει ο πρώτος που απαντά."
  ],
  resolverPlaceholder: ["https://… or 1.2.3.4", "https://… ή 1.2.3.4"],
  addResolver: ["Add resolver", "Προσθήκη resolver"],
  logging: ["Keep an activity log", "Καταγραφή κινήσεων"],
  logDays: ["Days kept", "Μέρες διατήρησης"],
  devicesTitle: ["Devices", "Συσκευές"],
  devicesNote: [
    "Each device gets its own address, so the log can tell them apart.",
    "Κάθε συσκευή παίρνει δική της διεύθυνση, ώστε το ιστορικό να τις ξεχωρίζει."
  ],
  deviceName: ["Device name", "Όνομα συσκευής"],
  platformApple: ["Apple", "Apple"],
  platformOther: ["Other", "Άλλη"],
  addDevice: ["Add device", "Προσθήκη συσκευής"],
  copyUrl: ["Copy address", "Αντιγραφή διεύθυνσης"],
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
  invalidResolver: ["Not a DoH URL or an IP address: {detail}", "Δεν είναι DoH URL ούτε IP: {detail}"],
  cloudflareNeedsDoh: [
    "{detail} belongs to Cloudflare. Workers cannot open TCP to their own network, so give the DoH URL instead.",
    "Το {detail} ανήκει στην Cloudflare. Οι Workers δεν ανοίγουν TCP προς το δικό τους δίκτυο, βάλε το DoH URL αντ' αυτού."
  ],
  noResolver: [
    "No resolver is set, so nothing can be answered.",
    "Δεν έχει οριστεί resolver, άρα τίποτα δεν μπορεί να απαντηθεί."
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
  const map = { list: "viaList", custom: "viaCustom", allow: "viaAllow", none: "viaNone", off: "viaOff", cname: "viaCname", cache: "viaCache", stale: "viaStale", no_resolver: "viaNoResolver" };
  return map[source] ? t(map[source]) : source;
}
