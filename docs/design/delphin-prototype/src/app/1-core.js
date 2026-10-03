/* ==========================================================================
   Delphin · interactive prototype — v3
   --------------------------------------------------------------------------
   1. Data & assets          6. Panels (ports, results, operators, policies)
   2. State & persistence    7. Screens (customer, Arabic, staff)
   3. Formatting helpers     8. Actions (rows, primary CTA, staff)
   4. Pricing & fares        9. Navigation & rendering
   5. Components            10. Motion
                            11. Shell (library, inspector, toast, dialog)
                            12. Boot
   Business rules are unchanged from v2; v3 reorganises the code, fixes the
   shell's UX defects and adds the motion layer.
   ========================================================================== */

/* 1. Data & assets ======================================================== */

const screens = DATA.spec.customer_screens,
  staff = DATA.spec.staff_screens;
const byId = Object.fromEntries([...screens, ...staff].map((s) => [s.id, s]));
const order = [...screens, ...staff].map((s) => s.id);
const required = ["C16", "C23", "C25", "C36", "C38", "C59", "C64"];
const exclusive = new Set(["C07", "C26", "C54", "C55", "C60", "C65", "C66"]);
const passengerLabels = ["Adultes", "Enfants", "Bébés"];

// Booking journey stages: drive the step rail and the library filters.
const STAGES = [
  ["Voyage", ["C01", "C02", "C03", "C04", "C05", "C06", "C07", "C08", "C09", "C10", "C11", "C12", "C13", "C14", "C15", "C16"]],
  ["Détails", ["C18", "C19", "C20", "C21", "C22", "C23", "C24", "C53", "C54", "C55", "C56", "C57", "C58", "C59"]],
  ["Paiement", ["C25", "C26", "C27", "C28", "C29", "C60", "C61", "C62"]],
  ["Billet", ["C30", "C31", "C33", "C34", "C43", "C63", "C64"]],
];

const fareFixtures = [
  { company: "Compagnie A", duration: 20, departure: "19:00", arrival: "15:00", extra: 0 },
  { company: "Compagnie B", duration: 22, departure: "08:00", arrival: "06:00", extra: 2700 },
];

// Images live in one stylesheet, parsed once: re-renders never push hundreds of
// kilobytes of base64 through innerHTML or re-resolve it during style recalc.
const IMAGES = {
  hero: "data:image/jpeg;base64," + DATA.assets["delphin_mediterranean_hero.png"],
  logo: "data:image/png;base64," + DATA.assets["delphin-primary.png"],
  reverse: "data:image/png;base64," + DATA.assets["delphin-reverse.png"],
};
const imageStyles = document.createElement("style");
imageStyles.id = "images";
imageStyles.textContent = `.hero-media{background-image:url("${IMAGES.hero}")}.brand-mark.reverse{background-image:url("${IMAGES.reverse}")}`;
// Right after the main stylesheet, wherever the host put it, so these rules win the cascade.
document.getElementById("styles").after(imageStyles);

/* 2. State & persistence ================================================== */

const seed = {
  id: "C01",
  lang: "FR",
  fields: {},
  checks: {},
  counts: { Adultes: 2, Enfants: 1, Bébés: 0, "Cabines privées": 1, "Nombre d’animaux": 1 },
  filter: { budget: 100000, company: "Toutes", duration: 30, sort: "price" },
  origin: "Alger",
  dest: "Marseille",
  departureISO: "2027-08-18",
  returnISO: "2027-08-30",
  depart: "18 août 2027",
  returnDate: "30 août 2027",
  portContext: "origin",
  dateContext: "departureISO",
  tripMode: 0,
  vehicle: true,
  pets: false,
  cabin: "Cabine privée · 4 places",
  guideOperator: "CL",
  traveler: 0,
  history: [],
  saved: false,
  refundStage: 1,
  tab: 0,
  priceDelta: 0,
  quoteExpiry: 0,
  paymentOutcome: "unknown",
  attempt: 1,
  alertEnabled: false,
};
const clone = (v) => JSON.parse(JSON.stringify(v));
const json = JSON.stringify;
let state = clone(seed);

try {
  localStorage.removeItem("delphin-demo-v1");
  const pref = JSON.parse(localStorage.getItem("delphin-demo-v2") || "null");
  if (pref) {
    for (const k of ["lang", "filter", "origin", "dest", "departureISO", "returnISO", "tripMode", "vehicle", "pets", "cabin", "counts"])
      if (k in pref) state[k] = pref[k];
    state.filter = { ...seed.filter, ...state.filter };
    state.counts = { ...seed.counts, ...state.counts };
  }
} catch (e) {}

function preferences() {
  return {
    lang: state.lang,
    filter: state.filter,
    origin: state.origin,
    dest: state.dest,
    departureISO: state.departureISO,
    returnISO: state.returnISO,
    tripMode: state.tripMode,
    vehicle: state.vehicle,
    pets: state.pets,
    cabin: state.cabin,
    counts: state.counts,
  };
}
function save() {
  try {
    localStorage.setItem("delphin-demo-v2", JSON.stringify(preferences()));
  } catch (e) {}
}

/* 3. Formatting helpers =================================================== */

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = (s) =>
  String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const normalize = (s) =>
  String(s)
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();

function money(n) {
  return Math.round(n).toLocaleString("fr-FR") + " DZD";
}
function dateText(v) {
  const d = new Date(v + "T12:00:00Z");
  return Number.isNaN(d.getTime())
    ? "Choisir une date"
    : new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "long", year: "numeric", timeZone: "UTC" }).format(d);
}
function validDate(iso) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(iso)) return false;
  const d = new Date(iso + "T12:00:00Z");
  return Number.isFinite(d.getTime()) && d.toISOString().slice(0, 10) === iso;
}
function check(k, def = false) {
  return k in state.checks ? state.checks[k] : def;
}
function key(i) {
  return state.id + "-" + i;
}
function fieldKey(label) {
  return state.id + "|" + (state.id === "C22" ? state.traveler + "|" : "") + label;
}
function rowRoute(label) {
  return DATA.rowlinks[state.id]?.[label];
}
function country(port) {
  return DATA.ports.find((p) => p.name === port)?.country || "À confirmer";
}
function connections(port) {
  return [...new Set(DATA.route_catalog.filter((r) => r.a === port || r.b === port).map((r) => (r.a === port ? r.b : r.a)))];
}
function totalTravelers() {
  return passengerLabels.reduce((n, k) => n + state.counts[k], 0);
}
function groupText() {
  // "1 enfant", not "1 enfants": the labels are plural nouns.
  return passengerLabels
    .filter((k) => state.counts[k] > 0)
    .map((k) => state.counts[k] + " " + (state.counts[k] === 1 ? k.slice(0, -1) : k).toLowerCase())
    .join(", ");
}
function stageOf(id) {
  return STAGES.findIndex((s) => s[1].includes(id));
}

/* 4. Pricing & fares ====================================================== */

function invalidateQuote() {
  state.priceDelta = 0;
  state.quoteExpiry = 0;
  for (const k of Object.keys(state.checks)) if (/^(C16|C23|C25|C59)-/.test(k)) delete state.checks[k];
  state.traveler = 0;
}
function startQuote() {
  state.quoteExpiry = Date.now() + 300000;
  state.priceDelta = 0;
  for (const k of Object.keys(state.checks)) if (/^(C16|C25)-/.test(k)) delete state.checks[k];
}
function priceParts() {
  const legs = state.tripMode === 1 ? 2 : 1;
  const travelers =
    (state.counts.Adultes * 16600 + state.counts.Enfants * 5000 + state.counts["Bébés"] * 1500) * legs + (state.fareExtra || 0);
  const vehicle = state.vehicle ? 13000 * legs : 0;
  const cabin = (/Siège|Fauteuil/.test(state.cabin) ? totalTravelers() * 1600 : 9400 * Math.max(1, state.counts["Cabines privées"])) * legs;
  const fees = 1800 * legs;
  const options = ((check("C24-0") ? totalTravelers() * 1600 : 0) + (check("C24-1") ? 1200 : 0) + (check("C24-2") ? 2000 : 0)) * legs;
  return { travelers, vehicle, cabin, fees, options, adjustment: state.priceDelta };
}
function basePrice() {
  const p = priceParts();
  return p.travelers + p.vehicle + p.cabin + p.fees + p.options;
}
function quoteTotal() {
  return basePrice() + state.priceDelta;
}
function filterCheck(label) {
  const i = byId.C12.elements.findIndex((a) => a[0] === "choice" && a[1] === label);
  return check("C12-" + i, label === "Cabine privée" ? state.cabin.includes("Cabine") : label === "Véhicule accepté" ? state.vehicle : false);
}
function fixtureTotal(index) {
  return basePrice() - (state.fareExtra || 0) + fareFixtures[index].extra * (state.tripMode === 1 ? 2 : 1);
}
function fareVisible(index) {
  const f = fareFixtures[index];
  return (
    fixtureTotal(index) <= state.filter.budget &&
    (!filterCheck("Départ le soir") || f.departure === "19:00") &&
    (state.filter.company === "Toutes" || state.filter.company === f.company) &&
    f.duration <= state.filter.duration
  );
}
function filteredCount() {
  return [0, 1].filter(fareVisible).length;
}
function activeFilterCount() {
  return (
    (state.filter.budget < 100000 ? 1 : 0) +
    (filterCheck("Départ le soir") ? 1 : 0) +
    (state.filter.company !== "Toutes" ? 1 : 0) +
    (state.filter.duration < 30 ? 1 : 0)
  );
}

/* Field values, choices & validation gates -------------------------------- */

function fieldValue(label, value) {
  if (label === "Depuis") return state.origin + " · " + country(state.origin);
  if (label === "Vers") return state.dest + " · " + country(state.dest);
  if (label === "Voyageurs" && state.id !== "C25") return groupText();
  if (state.id === "C24" && label === "Sous-total des options") return money(priceParts().options);
  if (label === "Départ") return dateText(state.departureISO);
  if (label === "Retour") return dateText(state.returnISO);
  if (label === "Véhicule & séjour") return (state.vehicle ? "Voiture" : "Piéton") + " · " + state.cabin;
  if (label === "Opérateur") return fareFixtures[state.selectedFare || 0].company + " · démo";
  if (label === "Vos prestations") return groupText() + " · " + (state.vehicle ? "voiture" : "piéton") + " · " + state.cabin;
  if (state.id === "C12" && label === "Compagnie") return state.filter.company;
  if (state.id === "C12" && label === "Durée maximale") return state.filter.duration + " heures";
  if (state.id === "C22" && label === "Naissance" && !(fieldKey(label) in state.fields)) {
    const a = state.counts.Adultes,
      c = state.counts.Enfants;
    return state.traveler < a ? "1990-06-14" : state.traveler < a + c ? "2019-03-12" : "2026-10-15";
  }
  if (state.id === "C26" && label === "Moyen sélectionné") return selectedChoice("C26")?.[1] || "Choisir un moyen";
  if (/Total|Montant à autoriser|Montant en vérification|Montant du voyage/.test(label) && /DZD/.test(value)) return money(quoteTotal());
  if (state.id === "C25") {
    const p = priceParts();
    const map = { Voyageurs: p.travelers, Véhicule: p.vehicle, Hébergement: p.cabin, "Frais de service": p.fees, Options: p.options };
    if (label in map) return money(map[label]);
  }
  if (/Validité du devis|Devis valide jusqu’à/.test(label))
    return state.quoteExpiry
      ? new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit", timeZone: "Africa/Algiers" }).format(new Date(state.quoteExpiry)) +
          " · heure d’Alger"
      : "Revalidation nécessaire";
  if (label === "Aller · Alger → Marseille") return dateText(state.departureISO) + " · " + state.origin + " → " + state.dest;
  if (label === "Retour · Marseille → Alger") return dateText(state.returnISO) + " · " + state.dest + " → " + state.origin;
  if (state.id === "C63" && label === "Port de départ") return state.origin + " · terminal à confirmer";
  return state.fields[fieldKey(label)] ?? value;
}
function setField(label, value) {
  if (/mot de passe|code authentificateur|code bancaire/i.test(label)) return;
  state.fields[fieldKey(label)] = value;
  updatePrimary();
}
function selectedChoice(id) {
  return byId[id].elements.find((a, i) => a[0] === "choice" && check(id + "-" + i, a[3]));
}
function gateReason() {
  if (required.includes(state.id) && !byId[state.id].elements.every((a, i) => a[0] !== "choice" || check(key(i), a[3])))
    return "Confirmez les éléments obligatoires ci-dessus.";
  if (state.id === "C09") {
    const choices = byId.C09.elements.map((a, i) => ({ a, i })).filter((v) => v.a[0] === "choice");
    const needed = choices.slice(0, 3).some((v) => check(key(v.i), false));
    const sharing = choices.find((v) => /transmet|partag|consent/i.test(v.a[1] + " " + v.a[2]));
    if (needed && sharing && !check(key(sharing.i), false)) return "Confirmez le partage des informations nécessaires à l’assistance.";
  }
  if (state.id === "C25" && (!state.quoteExpiry || Date.now() > state.quoteExpiry)) return "Le devis doit être revalidé avant le paiement.";
  if (state.id === "C21") {
    const e = byId.C21.elements.filter((a) => a[0] === "field");
    if (e.length >= 2) {
      const a = fieldValue(e[0][1], e[0][2]),
        b = fieldValue(e[1][1], e[1][2]);
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(a) || a !== b) return "Saisissez deux adresses e-mail identiques et valides.";
    }
  }
  if (state.id === "C22") {
    for (const label of ["Nom · comme sur le document", "Prénom", "Document exigé par le trajet"])
      if (!fieldValue(label, byId.C22.elements.find((a) => a[1] === label)?.[2]).trim()) return "Complétez les informations du voyageur.";
    const birth = fieldValue(
        "Naissance",
        state.traveler < state.counts.Adultes ? "1990-06-14" : state.traveler < state.counts.Adultes + state.counts.Enfants ? "2019-03-12" : "2026-10-15",
      ),
      expiry = fieldValue("Expiration du document", "2029-06-01");
    const last = state.tripMode === 1 ? state.returnISO : state.departureISO;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(birth) || birth > state.departureISO || !validDate(birth)) return "Vérifiez la date de naissance.";
    if (!validDate(expiry) || expiry < last) return "Le document doit être valide à chaque départ. Les règles du pays peuvent exiger davantage.";
  }
  return "";
}
function canContinue() {
  return !gateReason();
}
// v3: the element that blocks the primary action, so the UI can lead the user to it.
function gateTarget(reason) {
  const body = $("#body");
  if (!body || !reason) return null;
  const choices = $$(".choice", body);
  const fields = $$(".field", body);
  const byLabel = (re) => fields.find((f) => re.test($(".field-label", f)?.textContent || ""));
  const empty = (f) => {
    const i = $("input", f);
    return i && !i.value.trim();
  };
  if (/obligatoires/.test(reason)) return choices.find((c) => !$("input", c)?.checked);
  if (/partage/.test(reason)) return choices.find((c) => /transmet|partag|consent/i.test(c.textContent));
  if (/revalidé/.test(reason)) return $$("button", body).find((b) => /Revalider/.test(b.textContent));
  if (/e-mail/.test(reason)) {
    const mails = fields.filter((f) => $("input[type=email]", f));
    return mails.find((f) => empty(f) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test($("input", f).value)) || mails[1] || mails[0];
  }
  if (/Complétez/.test(reason)) return fields.find(empty);
  if (/naissance/.test(reason)) return byLabel(/Naissance/);
  if (/document doit être valide/.test(reason)) return byLabel(/Expiration/);
  return null;
}
