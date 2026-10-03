
/* 5. Components =========================================================== */

// One stroke icon set (24px grid, 1.7 stroke) shared by the app and the shell.
const ICON_PATHS = {
  search: '<circle cx="10.5" cy="10.5" r="6"/><path d="m15 15 5 5"/>',
  heart: '<path d="M20 5c-3-3-6-1-8 1-2-2-5-4-8-1-4 4 2 9 8 14 6-5 12-10 8-14Z"/>',
  ticket: '<path d="M4 5h16v5a2 2 0 0 0 0 4v5H4v-5a2 2 0 0 0 0-4Z"/><path d="M15 6v12" stroke-dasharray="2 2"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
  location: '<path d="M12 21s7-6.1 7-11.5a7 7 0 0 0-14 0C5 14.9 12 21 12 21Z"/><circle cx="12" cy="9.5" r="2.5"/>',
  calendar: '<rect x="3.5" y="5" width="17" height="15" rx="2.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  lock: '<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8 10.5v-3a4 4 0 0 1 8 0v3"/>',
  car: '<path d="M4 16.5v-4l2.2-5.2A2 2 0 0 1 8 6h8a2 2 0 0 1 1.8 1.3l2.2 5.2v4Z"/><path d="M4 12.5h16M6.5 16.5V19M17.5 16.5V19"/>',
  help: '<circle cx="12" cy="12" r="8.5"/><path d="M9.6 9.4a2.5 2.5 0 0 1 4.8.9c0 1.7-2.4 2.2-2.4 3.7"/><path d="M12 17h.01"/>',
  accessibility: '<circle cx="12" cy="4.6" r="1.7"/><path d="M5 8.5c2.3.7 4.6 1 7 1s4.7-.3 7-1M12 9.5V14m0 0-3 6m3-6 3 6"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8h.01"/>',
  alert: '<path d="M10.3 4.3 3 17.5a2 2 0 0 0 1.7 3h14.6a2 2 0 0 0 1.7-3L13.7 4.3a2 2 0 0 0-3.4 0Z"/><path d="M12 9.5v4M12 17h.01"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  chevronRight: '<path d="m9.5 6 6 6-6 6"/>',
  chevronLeft: '<path d="m14.5 6-6 6 6 6"/>',
  chevronDown: '<path d="m6.5 9.5 5.5 5.5 5.5-5.5"/>',
  arrowRight: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  arrowLeft: '<path d="M19 12H5M11 6l-6 6 6 6"/>',
  plus: '<path d="M12 5.5v13M5.5 12h13"/>',
  minus: '<path d="M5.5 12h13"/>',
  reset: '<path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3"/><path d="M4.5 4.5v4h4"/>',
  close: '<path d="m6.5 6.5 11 11M17.5 6.5l-11 11"/>',
  ship: '<path d="M3 18c1.5 1.2 3 1.2 4.5 0s3-1.2 4.5 0 3 1.2 4.5 0 3-1.2 4.5 0"/><path d="m5 15.5-1-4h16l-1.5 4M7 11.5V8h8l2 3.5M10.5 8V5.5"/>',
  external: '<path d="M14 5h5v5M19 5l-8 8"/><path d="M17 13.5V18a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 18V8.5A1.5 1.5 0 0 1 6 7h4.5"/>',
  refresh: '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3"/><path d="M19.5 4.5v4h-4"/>',
  gauge: '<path d="M4 16a8 8 0 1 1 16 0"/><path d="m12 16 3.5-4.5"/><circle cx="12" cy="16" r="1.2"/>',
  folder: '<path d="M3.5 7.5A1.5 1.5 0 0 1 5 6h4l2 2h8a1.5 1.5 0 0 1 1.5 1.5V17a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 17Z"/>',
  card: '<rect x="3" y="5.5" width="18" height="13" rx="2.5"/><path d="M3 10h18M7 15h4"/>',
  grid: '<rect x="4" y="4" width="6.5" height="6.5" rx="1.6"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.6"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.6"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.6"/>',
  quote: '<path d="M7 3.5h7l4 4V20H7Z"/><path d="M14 3.5V8h4M10 12h5M10 15.5h5"/>',
  book: '<path d="M5 4.5h10.5a2 2 0 0 1 2 2V20H7a2 2 0 0 1-2-2Z"/><path d="M5 18a2 2 0 0 1 2-2h10.5"/>',
  users: '<circle cx="9" cy="8.5" r="3.5"/><path d="M2.5 20v-1a6.5 6.5 0 0 1 13 0v1M16 5.2a3.5 3.5 0 0 1 0 6.6M18.5 14a6.5 6.5 0 0 1 3 5.5v.5"/>',
  flag: '<path d="M5 21V4h11l-2 4 2 4H5"/>',
};
// Arrows and chevrons point along the reading direction: right to left, they mirror.
const DIRECTIONAL = new Set(["arrowRight", "arrowLeft", "chevronRight", "chevronLeft"]);
function icon(name, cls = "") {
  if (DIRECTIONAL.has(name)) cls += " flip";
  return `<svg class="icon ${cls}" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${ICON_PATHS[name] || ""}</svg>`;
}
const STATUS_ICONS =
  '<svg viewBox="0 0 18 12" width="18" height="12" aria-hidden="true"><rect x="0" y="8" width="3" height="4" rx="1"/><rect x="5" y="5.5" width="3" height="6.5" rx="1"/><rect x="10" y="3" width="3" height="9" rx="1"/><rect x="15" y="0" width="3" height="12" rx="1"/></svg>' +
  '<svg viewBox="0 0 16 12" width="16" height="12" aria-hidden="true"><path d="M8 11.5 5.6 9a3.4 3.4 0 0 1 4.8 0Z"/><path d="M3.4 6.8a6.5 6.5 0 0 1 9.2 0l-1.4 1.4a4.5 4.5 0 0 0-6.4 0Z"/><path d="M1.2 4.6a9.6 9.6 0 0 1 13.6 0l-1.4 1.4a7.6 7.6 0 0 0-10.8 0Z"/></svg>' +
  '<svg viewBox="0 0 27 12" width="27" height="12" aria-hidden="true"><rect x=".5" y=".5" width="22" height="11" rx="3.2" fill="none" stroke="currentColor" opacity=".45"/><rect x="2" y="2" width="19" height="8" rx="2"/><path d="M24.2 4v4a2 2 0 0 0 0-4Z" opacity=".5"/></svg>';

// data-key gives every block a stable identity across re-renders (motion diffing).
const dk = (type, label) => ` data-key="${esc(type + ":" + label)}"`;

function btn(label, fn, cls = "secondary") {
  return `<button class="${esc(cls)}" onclick="${esc(fn)}">${esc(label)}</button>`;
}
function statusTone(v) {
  if (/^(Résultats reçus|Billet disponible|Disponible|Consultation disponible|Actif maintenant|Réservation conservée)/.test(v)) return "ok";
  if (/^(En cours…|En rapprochement|Recherche de la référence|À revalider|À confirmer)/.test(v)) return "wait";
  if (/^(Indisponible|Échec|Interruption|Confirmation manquante|Réservation: inconnu)/.test(v)) return "off";
  return "";
}
function btnField(label, value, fn, ic) {
  return `<button class="field clickable${ic ? " has-icon" : ""}"${dk("field", label)} onclick="${esc(fn)}">${ic ? icon(ic, "field-icon") : ""}<span class="field-label">${esc(label)}</span><strong data-value>${esc(value)}</strong>${icon("chevronDown", "field-chevron")}</button>`;
}
function btnRow(label, value, fn, arrow = true, cls = "") {
  const tone = statusTone(value);
  return `<button class="row${cls ? " " + cls : ""}"${dk("row", label)} onclick="${esc(fn)}"><span>${esc(label)}</span><strong data-value${tone ? ` data-tone="${tone}"` : ""}>${esc(value)}</strong>${arrow ? icon("chevronRight", "row-chevron") : ""}</button>`;
}
function field(label, value, ic) {
  const type = /Naissance|Expiration|date de naissance/.test(label)
    ? "date"
    : /Adresse e-mail|Confirmer.*e-mail|Adresse de notification/.test(label)
      ? "email"
      : /mot de passe/i.test(label)
        ? "password"
        : "text";
  const hint = /Document|Nom ·|Prénom|Naissance/.test(label) ? '<span class="fieldhint">Informations fictives uniquement.</span>' : "";
  return `<label class="field${ic ? " has-icon" : ""}"${dk("field", label)}>${ic ? icon(ic, "field-icon") : ""}<span class="field-label">${esc(label)}</span><input aria-label="${esc(label)}" type="${type}" autocomplete="off" value="${esc(type === "password" ? "" : fieldValue(label, value))}" oninput="${esc("setField(" + json(label) + ",this.value)")}">${hint}</label>`;
}
function heroBlock(title, tag = "Visuel décoratif") {
  const lines = String(title)
    .split("\n")
    .map((l) => `<span class="hero-line"><span>${esc(l)}</span></span>`)
    .join("");
  return `<div class="hero"${dk("hero", title)}><i class="hero-media" aria-hidden="true"></i><h2>${lines}</h2>${tag ? `<span class="hero-tag">${esc(tag)}</span>` : ""}</div>`;
}
function noticeBlock(title, text, tone = "info", extra = "") {
  return `<aside class="notice ${esc(tone)}"${dk("notice", title || text)}>${icon(tone === "warning" ? "alert" : "info", "notice-icon")}<div>${title ? `<strong>${esc(title)}</strong>` : ""}<p>${esc(text)}</p>${extra}</div></aside>`;
}
function stepButton(label, delta, atLimit) {
  return `<button class="step-btn" aria-label="${esc((delta < 0 ? "Retirer · " : "Ajouter · ") + label)}"${atLimit ? ' aria-disabled="true"' : ""} onclick="${esc("step(" + json(label) + "," + delta + ")")}">${icon(delta < 0 ? "minus" : "plus")}</button>`;
}
function budgetFill(v) {
  return ((v - 30000) / (200000 - 30000)) * 100;
}

function component(a, i) {
  const k = a[0],
    kk = key(i),
    target = rowRoute(a[1]);
  if (k === "hero") return heroBlock(a[1]);
  if (k === "label") {
    const label = `<h3 class="eyebrow"${dk("label", a[1])}>${esc(a[1])}</h3>`;
    return state.id === "C02" && a[1] === "RÉSULTATS" ? label + `<div class="port-list" data-key="ports">${portsPanel()}</div>` : label;
  }
  if (k === "text") return `<p class="muted"${dk("text", a[1])}>${esc(a[1])}</p>`;
  if (k === "field") {
    if (state.id === "C01") return btnField(a[1], fieldValue(a[1], a[2]), "routeRow(" + json(a[1]) + "," + json(target || "C03") + ")", a[3]);
    if (state.id === "C02")
      return `<label class="field has-icon"${dk("field", a[1])}>${icon("search", "field-icon")}<span class="field-label">${esc(a[1])}</span><input aria-label="Rechercher un port" oninput="filterPorts(this.value)" value="" placeholder="Port ou pays"></label>`;
    return field(a[1], a[2], a[3]);
  }
  if (k === "two") return `<div class="two"${dk("two", a[1])}>${field(a[1], a[2]) + field(a[3], a[4])}</div>`;
  if (k === "row")
    return btnRow(a[1], fieldValue(a[1], a[2]), target ? "routeRow(" + json(a[1]) + "," + json(target) + ")" : "rowAction(" + json(a[1]) + ")", !!target);
  if (k === "seg") {
    const labels = state.id === "C22" ? Array.from({ length: totalTravelers() }, (_, j) => "V" + (j + 1)) : a.slice(1);
    const active =
      state.id === "C22"
        ? state.traveler
        : ["C01", "C03"].includes(state.id)
          ? state.tripMode
          : state.id === "C05"
            ? +state.vehicle
            : state.id === "C08"
              ? +state.pets
              : state.id === "C13"
                ? +(state.filter.sort === "duration")
                : state.tab;
    return `<div class="seg" role="group"${dk("seg", a[1])}>${labels
      .map((s, j) => `<button class="${j === active ? "active" : ""}" aria-pressed="${j === active}" onclick="setTab(${j})">${esc(s)}</button>`)
      .join("")}</div>`;
  }
  if (k === "tools")
    return `<div class="two tools"${dk("tools", a[1])}>${a
      .slice(1)
      .map((s) => {
        const label = s.startsWith("Filtres") ? "Filtres (" + activeFilterCount() + ")" : s.includes("filtres actifs") ? activeFilterCount() + " filtres actifs" : s;
        return btn(label, rowRoute(s) ? "routeRow(" + json(s) + "," + json(rowRoute(s)) + ")" : "rowAction(" + json(s) + ")");
      })
      .join("")}</div>`;
  if (k === "choice") {
    if (state.id === "C02") return "";
    const disabled = state.id === "C12" && ["Cabine privée", "Véhicule accepté"].includes(a[1]);
    const selected = disabled ? filterCheck(a[1]) : check(kk, state.id === "C12" && a[1] === "Départ le soir" ? false : a[3]);
    const type = exclusive.has(state.id) ? "radio" : "checkbox";
    return `<label class="choice ${selected ? "selected" : ""}${disabled ? " locked" : ""}"${dk("choice", a[1])}><input type="${type}" name="${state.id}-choice" ${selected ? "checked" : ""} ${disabled ? "disabled" : ""} onchange="${esc("choose(" + json(kk) + ",this.checked,this)")}"><div><strong>${esc(a[1])}</strong><small>${esc(a[2])}${disabled ? " · configuration du voyage" : ""}</small></div></label>`;
  }
  if (k === "stepper") {
    const n = state.counts[a[1]] ?? a[3],
      min = ["Adultes", "Cabines privées"].includes(a[1]) ? 1 : 0;
    return `<div class="stepper"${dk("stepper", a[1])}><div><strong>${esc(a[1])}</strong><small>${esc(a[2])}</small></div><div class="stepper-ctrl">${stepButton(a[1], -1, n <= min)}<b class="count"><span data-value>${n}</span></b>${stepButton(a[1], 1, n >= 9)}</div></div>`;
  }
  if (k === "notice") return noticeBlock(a[1], a[2], a[3]);
  if (k === "summary") {
    const f = fareFixtures[state.selectedFare || 0];
    let title = a[1].includes("Alger →") ? state.origin + " → " + state.dest : a[1],
      details = a[2];
    if (state.id === "C11") details = dateText(state.departureISO) + " · " + groupText() + " · " + (state.vehicle ? "voiture" : "piéton");
    if (state.id === "C15") {
      title = dateText(state.departureISO) + " · " + f.departure;
      details = "Arrivée " + f.arrival + " (+1 j) · heure locale";
    }
    if (a[1] === "Total · DZD") details = money(quoteTotal()) + " · données de démo";
    return `<div class="summary"${dk("summary", a[1])}><strong data-value>${esc(title)}</strong><p data-value>${esc(details)}</p></div>`;
  }
  if (k === "fare") {
    if (state.id === "C11") return "";
    const to = state.id === "C32" ? "C33" : state.id === "C17" ? "C11" : "C15";
    return `<button class="fare"${dk("fare", a[1])} onclick="${esc("go(" + json(to) + ")")}"><span class="operator">${icon("ship")}Démonstration · aucun inventaire réel</span><strong>${esc(a[1])}</strong><small>${esc(a[2])}</small><footer><b data-value${/DZD/.test(a[3]) ? "" : ' class="is-label"'}>${/DZD/.test(a[3]) ? money(basePrice()) : esc(a[3])}</b><span>Voir ${icon("arrowRight")}</span></footer></button>`;
  }
  if (k === "compare") {
    const rows = clone(a[3]);
    if (state.id === "C16") rows[0] = ["Total", money(basePrice()), money(basePrice() + 2400)];
    if (state.id === "C13") {
      rows[0] = ["Total groupe", money(fixtureTotal(0)), money(fixtureTotal(1))];
      rows[2] = ["Hébergement", state.cabin, state.cabin];
      rows[3] = ["Véhicule", state.vehicle ? "Inclus" : "Sans véhicule", state.vehicle ? "Inclus" : "Sans véhicule"];
    }
    // C13 compares two offers: flag the lower group total.
    const best = state.id === "C13" ? (fixtureTotal(0) <= fixtureTotal(1) ? 1 : 2) : 0;
    const head = (j, label) => `<b${best === j ? ' class="best"' : ""}>${esc(label)}${best === j ? '<em class="tag">Meilleur prix</em>' : ""}</b>`;
    return `<div class="comparison"${dk("compare", a[1])}><div class="comparison-head"><span></span>${head(1, a[1])}${head(2, a[2])}</div>${rows
      .map((r, ri) => `<div>${r.map((v, j) => (j ? `<strong${ri === 0 && j === best ? ' class="best"' : ""} data-value>${esc(v)}</strong>` : `<span>${esc(v)}</span>`)).join("")}</div>`)
      .join("")}</div>`;
  }
  if (k === "calendar") {
    const dep = state.departureISO,
      ret = state.tripMode === 1 ? state.returnISO : "",
      editing = Number(state[state.dateContext].slice(-2));
    const days = Array.from({ length: 31 }, (_, j) => {
      const n = j + 1,
        iso = "2027-08-" + String(n).padStart(2, "0"),
        cls = ["day"];
      if (n === editing) cls.push("selected");
      if (iso === dep) cls.push("is-start");
      if (ret && iso === ret) cls.push("is-end");
      if (ret && iso > dep && iso < ret) cls.push("in-range");
      if (state.dateContext === "returnISO" && iso < dep) cls.push("is-before");
      if ((n + 5) % 7 >= 5) cls.push("weekend");
      const role = iso === dep ? " · départ" : ret && iso === ret ? " · retour" : "";
      return `<button class="${cls.join(" ")}" aria-label="${n} août 2027${role}" aria-pressed="${n === editing}" onclick="chooseDay(${n})">${n}</button>`;
    }).join("");
    const legend = ret
      ? `<div class="cal-legend"><span><i class="dot start"></i>Aller · ${esc(dateText(dep))}</span><span><i class="dot end"></i>Retour · ${esc(dateText(ret))}</span></div>`
      : "";
    return `<div class="calendar${ret ? " has-range" : ""}"${dk("calendar", "août")}><header><strong>Août 2027 · calendrier de démo</strong></header>${legend}<div class="calgrid">${(arabicOn() ? ["ن", "ث", "ر", "خ", "ج", "س", "ح"] : ["L", "M", "M", "J", "V", "S", "D"]).map((x) => `<span class="weekday">${x}</span>`).join("")}${"<span></span>".repeat(6)}${days}</div></div>`;
  }
  if (k === "progress")
    return `<div class="progress"${dk("progress", a[1])}><strong>${esc(a[1])}</strong><div class="bar" role="progressbar" aria-label="${esc(a[1])}" aria-valuenow="${a[2] * 100}" aria-valuemin="0" aria-valuemax="100"><span style="width:${a[2] * 100}%"></span></div><small>Simulation · utilisez le bouton ci-dessous.</small></div>`;
  if (k === "slider")
    return `<label class="slider"${dk("slider", "budget")}><strong id="budgetvalue">${money(state.filter.budget)}</strong><input aria-label="Budget total maximum" type="range" min="30000" max="200000" step="1000" value="${state.filter.budget}" style="--fill:${budgetFill(state.filter.budget)}%" oninput="changeBudget(this.value)"><span class="slider-scale" aria-hidden="true"><span>${money(30000)}</span><span>${money(200000)}</span></span></label>`;
  if (k === "otp")
    return `<label class="field otp"${dk("otp", "code")}><span class="field-label">Code de démonstration: 123456</span><input id="otp" inputmode="numeric" maxlength="6" aria-label="Code de vérification" placeholder="123456" autocomplete="off" oninput="otpInput(this)"></label>`;
  if (k === "success")
    return `<div class="success"${dk("success", a[1])}><b class="success-icon" aria-hidden="true"><svg viewBox="0 0 36 36"><circle cx="18" cy="18" r="15"/><path d="m11.5 18.5 4.5 4.5 9-9.5"/></svg></b><div><strong>${esc(a[1])}</strong><p>${esc(state.id === "C29" ? money(quoteTotal()) + " · démonstration" : a[2])}</p></div></div>`;
  if (k === "timeline")
    return `<ol class="timeline"${dk("timeline", a[1][0][0])}>${a[1]
      .map(
        (r, j) =>
          `<li class="${r[2] || (state.id === "C39" && j <= state.refundStage) ? "done" : ""}" style="--i:${j}"><strong>${esc(r[0])}</strong><small>${esc(state.id === "C30" && j === 0 ? money(quoteTotal()) : r[1])}</small></li>`,
      )
      .join("")}</ol>`;
  if (k === "ticket")
    return `<div class="ticket"${dk("ticket", a[1])}><span class="eyebrow">DOCUMENT DE DÉMONSTRATION</span><h2>${esc(a[1])}</h2><p>${esc(a[2])}</p><div class="tear" aria-hidden="true"></div><div class="ticket-code"><div>DÉMO</div><p><strong>Aucun QR actif</strong><small>Non valable pour embarquer</small></p></div></div>`;
  return "";
}

/* 6. Panels =============================================================== */

function portsPanel() {
  const items =
    state.portContext === "origin" ? DATA.ports.filter((p) => connections(p.name).length) : DATA.ports.filter((p) => connections(state.origin).includes(p.name));
  const current = state[state.portContext];
  return items
    .map(
      (p) =>
        `<button class="choice port${p.name === current ? " is-current" : ""}"${dk("port", p.name)} data-search="${esc((p.name + " " + p.country).toLowerCase())}" onclick="${esc("selectPort(" + json(p.name) + ")")}">${icon("location", "port-icon")}<div><strong>${esc(p.name)}</strong><small>${esc(p.country)} · liaison publiée, dates à confirmer</small></div>${p.name === current ? '<em class="tag">Actuel</em>' : ""}${icon("chevronRight", "row-chevron")}</button>`,
    )
    .join("");
}
function filterPorts(q) {
  let n = 0;
  for (const p of $$(".port", $("#body"))) {
    p.hidden = !normalize(p.dataset.search).includes(normalize(q));
    if (!p.hidden) n++;
  }
  $("#portEmpty")?.remove();
  if (!n) $(".port-list", $("#body")).insertAdjacentHTML("beforeend", '<p id="portEmpty" class="empty-state" role="status">Aucun port correspondant. Essayez le nom du port ou du pays.</p>');
}
function resultsPanel() {
  const list = fareFixtures.map((f, i) => ({ ...f, index: i })).filter((f) => fareVisible(f.index));
  list.sort(state.filter.sort === "duration" ? (a, b) => a.duration - b.duration : (a, b) => a.extra - b.extra);
  if (!list.length)
    return noticeBlock(
      "Aucune offre correspondante",
      "Le budget concerne tout le groupe. Modifiez les critères ou activez une alerte.",
      "warning",
      `<div class="notice-actions">${btn("Modifier les filtres", "go('C12')")}${btn("Créer une alerte", "go('C66')")}</div>`,
    );
  const badge = state.filter.sort === "duration" ? "Plus rapide" : "Meilleur prix";
  return list
    .map(
      (f, n) =>
        `<button class="fare"${dk("fare", f.company)} onclick="${esc("selectFare(" + f.index + ")")}"><span class="operator">${icon("ship")}${esc(f.company)} · offre fictive${n === 0 && list.length > 1 ? `<em class="tag">${badge}</em>` : ""}</span><strong>${esc(state.origin)} → ${esc(state.dest)}</strong><small>${esc(dateText(state.departureISO))} · ${f.departure} → ${f.arrival} (+1 j) · ${f.duration} h</small><small>Heures locales des ports · ${esc(groupText())}</small><footer><b data-value>${money(fixtureTotal(f.index))}</b><span>Total du groupe ${icon("arrowRight")}</span></footer></button>`,
    )
    .join("");
}
function selectFare(index) {
  state.selectedFare = index;
  state.fareExtra = fareFixtures[index].extra * (state.tripMode === 1 ? 2 : 1);
  startQuote();
  go("C15");
}
function sourceCards(ids) {
  return ids
    .map((id) => DATA.evidence.find((s) => s.id === id))
    .filter(Boolean)
    .map(
      (s) =>
        `<a class="source-card"${dk("source", s.id)} target="_blank" rel="noopener noreferrer" href="${esc(s.url)}"><strong>${esc(s.title)}</strong><small>${s.id} · vérifié le 02/10/2026${s.access !== "content" ? " · accès partiel / indexé" : ""}</small>${icon("external", "source-icon")}</a>`,
    )
    .join("");
}
function operatorsPanel() {
  return DATA.operators
    .map(
      (o) =>
        `<button class="operator-card"${dk("operator", o.id)} onclick="${esc("openOperator(" + json(o.id) + ")")}"><span class="eyebrow">${esc(o.country)}</span><strong>${esc(o.name)}</strong><p>${esc(o.coverage)}</p><small>Routes publiées · aucune place garantie</small><b>Consignes & sources ${icon("arrowRight")}</b></button>`,
    )
    .join("");
}
function openOperator(id) {
  state.guideOperator = id;
  // Switching tabs inside the guide keeps the screen; elsewhere it opens it.
  if (state.id === "C52") Motion.intent = "update";
  go("C52");
}
function policyPanel() {
  const o = DATA.operators.find((p) => p.id === state.guideOperator) || DATA.operators[1];
  const policies = {
    AF: [
      "Présentation publiée: au moins 3 h à pied, 5 h avec véhicule. Vérifier la consigne du billet.",
      "Franchise publiée: 60 kg adulte en cabine, 30 kg en économique. Ne pas généraliser à une autre compagnie.",
      "Certaines informations pratiques doivent être rapprochées des consignes actuelles des autorités.",
    ],
    CL: [
      "Heures de présentation indiquées sur le billet. Assistance à signaler, idéalement dès la réservation.",
      "Garage inaccessible pendant la traversée. Préparer un sac avec documents et médicaments.",
      "Électrique / hybride: consigne publiée de batterie sous 30 %, sans recharge. GPL: sécurité selon compagnie.",
    ],
    BA: [
      "La table est directionnelle. Au relevé: Alger → Barcelone 9 h; Barcelone → Alger 8 h.",
      "Alger → Valence 9 h; Valence → Alger 5 h. Oran → Valence 7 h; Valence → Oran 5 h.",
      "Ce sont des anticipations de présentation publiées, pas des durées de traversée ni une heure de clôture.",
    ],
    GN: [
      "Le billet et la carte d’embarquement sont des documents différents.",
      "La FAQ extra-Schengen consultée ne nomme pas l’Algérie dans sa liste de délais. Ne pas déduire automatiquement 4 h.",
      "Le terminal, la présentation et la clôture doivent être confirmés sur votre départ.",
    ],
    NE: [
      "Le site officiel a été identifié; l’extraction des conditions et du programme reste partielle.",
      "Les itinéraires, les horaires et les conditions sont à confirmer auprès de la compagnie avant vente.",
    ],
  };
  return `<div class="summary" data-key="summary:guide"><strong data-value>${esc(o.name)}</strong><p>Consignes publiques · à revalider pour votre départ</p></div><div class="operator-tabs" data-key="tabs:operators">${DATA.operators
    .map((p) => btn(p.name, "openOperator(" + json(p.id) + ")", p.id === o.id ? "active" : ""))
    .join("")}</div>${policies[o.id].map((t) => noticeBlock("", t)).join("")}<h3 class="eyebrow" data-key="label:sources">SOURCES DE LA COMPAGNIE</h3>${sourceCards(o.guide)}${noticeBlock(
    "Le billet décide du départ",
    "Ces sources ne remplacent pas le tarif, les dates, les dernières consignes ni les contrôles des autorités.",
    "warning",
  )}`;
}

/* 7. Screens ============================================================== */

// Every screen renders into the same device: status bar, header, a sliding
// screen area and an optional tab bar. Motion relies on this structure.
function renderPhone({ cls = "", rtl = false, header = "", screen, nav = "" }) {
  $("#viewport").innerHTML = `<div class="device"><article class="phone${cls ? " " + cls : ""}"${rtl ? ' dir="rtl" lang="ar"' : ' lang="fr"'}>
<div class="statusbar" aria-hidden="true"><span class="sb-time">9:41</span><span class="island"></span><span class="sb-icons">${STATUS_ICONS}</span></div>
${header}<div class="screen-stack"><section class="screen">${screen}</section></div>
${nav}<i class="home-indicator" aria-hidden="true"></i>
</article></div>`;
}
function appHeader(title, action = "", rtl = false) {
  return `<header class="app-header"><button class="back" aria-label="Retour" onclick="back()">${icon("chevronLeft")}</button><h1 id="screenHeading" tabindex="-1">${esc(title)}</h1>${action}</header>`;
}
function tabBar(label, tabs) {
  return `<nav class="bottomnav" style="--n:${tabs.length}" aria-label="${esc(label)}">${tabs
    .map(
      (t) =>
        `<button class="${t.active ? "active" : ""}"${t.active ? ' aria-current="page"' : ""} onclick="${esc(t.onclick)}">${icon(t.icon, "nav-icon")}<span>${esc(t.label)}</span></button>`,
    )
    .join("")}<i class="nav-pill" aria-hidden="true"></i></nav>`;
}
function rail(id) {
  const active = stageOf(id);
  if (active < 0) return "";
  return `<div class="step-rail" aria-label="Étapes de la réservation"><i class="rail-progress" style="--p:${(active + 1) / STAGES.length}" aria-hidden="true"></i>${STAGES.map(
    (s, i) =>
      `<span class="${i === active ? "current" : i < active ? "done" : ""}"${i === active ? ' aria-current="step"' : ""}><b>${i < active ? icon("check") : i + 1}</b>${s[0]}</span>`,
  ).join("")}</div>`;
}

/* Customer app ------------------------------------------------------------ */
function renderArabic() {
  const t = state.tripMode;
  return `${heroBlock("رحلتك البحرية، بكل وضوح", "")}<div class="seg" role="group" data-key="seg:ar">${[
    ["ذهاب فقط", 0],
    ["ذهاب وعودة", 1],
  ]
    .map(([l, j]) => `<button class="${t === j ? "active" : ""}" aria-pressed="${t === j}" onclick="setTab(${j})">${l}</button>`)
    .join(
      "",
    )}</div><button class="field clickable has-icon" data-key="field:ar-from" onclick="state.portContext='origin';go('C02')">${icon("location", "field-icon")}<span class="field-label">من</span><strong>${esc(state.origin)}</strong></button><button class="field clickable has-icon" data-key="field:ar-to" onclick="state.portContext='dest';go('C02')">${icon("location", "field-icon")}<span class="field-label">إلى</span><strong>${esc(state.dest)}</strong></button><button class="row" data-key="row:ar-date" onclick="go('C03')"><span>تاريخ المغادرة</span><strong>${esc(dateText(state.departureISO))}</strong>${icon("chevronRight", "row-chevron")}</button><button class="row" data-key="row:ar-party" onclick="go('C04')"><span>المسافرون</span><strong>بالغان وطفل واحد</strong>${icon("chevronRight", "row-chevron")}</button><button class="row" data-key="row:ar-vehicle" onclick="go('C05')"><span>المركبة والإقامة</span><strong>سيارة ومقصورة خاصة</strong>${icon("chevronRight", "row-chevron")}</button>${noticeBlock("سعر واضح", "السعر الإجمالي يشمل المسافرين والمركبة والمقصورة.")}`;
}

/* Arabic (customer app) ----------------------------------------------------- */
// Screens render in French; in Arabic one pass maps their text to the AR dictionary
// (src/i18n/ar.json). Patterns cover what carries live values: dates, prices, counts.
const arabicOn = () => state.lang === "AR" && !state.id.startsWith("A");
const AR_MONTHS = {
  janvier: "جانفي", février: "فيفري", mars: "مارس", avril: "أفريل", mai: "ماي", juin: "جوان",
  juillet: "جويلية", août: "أوت", septembre: "سبتمبر", octobre: "أكتوبر", novembre: "نوفمبر", décembre: "ديسمبر",
};
const MONTH = "(janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre)";
const arMonth = (m) => AR_MONTHS[m.toLowerCase()];
// Counted nouns: [1, 2–10 (and 0), 11+].
const AR_NOUNS = {
  adulte: ["بالغ", "بالغين", "بالغًا"], enfant: ["طفل", "أطفال", "طفلًا"], bébé: ["رضيع", "رضّع", "رضيعًا"],
  voyageur: ["مسافر", "مسافرين", "مسافرًا"], traversée: ["رحلة", "رحلات", "رحلة"], place: ["مكان", "أماكن", "مكانًا"],
  siège: ["مقعد", "مقاعد", "مقعدًا"], cabine: ["مقصورة", "مقصورات", "مقصورة"], véhicule: ["مركبة", "مركبات", "مركبة"],
  filtre: ["عامل تصفية", "عوامل تصفية", "عامل تصفية"], heure: ["ساعة", "ساعات", "ساعة"], seconde: ["ثانية", "ثوانٍ", "ثانية"],
};
const arCount = (n, noun) => {
  const [one, few, many] = AR_NOUNS[noun];
  return `${n} ${+n === 1 ? one : +n <= 10 ? few : many}`;
};
const nounOf = (w) => w.replace(/s$/, "");
const AR_ROLE = { Voyageur: "المسافر", Adulte: "البالغ", Enfant: "الطفل", Bébé: "الرضيع" };
const AR_PATTERNS = [
  [/^([+\-−]?\s?[\d   ]*\d)\s?DZD$/, (m, n) => `${n} دج`],
  [new RegExp(`^(\\d{1,2}) ${MONTH}(?: (\\d{4}))?(?: (\\d{1,2}:\\d{2}))?$`, "i"), (m, d, mo, y, t) => [d, arMonth(mo), y, t].filter(Boolean).join(" ")],
  [new RegExp(`^${MONTH} (\\d{4})$`, "i"), (m, mo, y) => `${arMonth(mo)} ${y}`],
  [/^(\d{1,2}:\d{2}) \(\+1 j\)$/, (m, t) => `${t} (+1 يوم)`],
  [/^Arrivée (\d{1,2}:\d{2})( \(\+1 j\))?$/, (m, t, p) => `الوصول ${t}${p ? " (+1 يوم)" : ""}`],
  [/^(\d+) h$/, (m, n) => `${n} سا`],
  [/^(\d+) heures?$/, (m, n) => arCount(n, "heure")],
  [/^(\d+,\d+) m(?: \/ (\d+,\d+) m)?$/, (m, a, b) => (b ? `${a} م / ${b} م` : `${a} م`)],
  [/^\d+ (?:adultes?|enfants?|bébés?)(?:, \d+ (?:adultes?|enfants?|bébés?))*$/, (m) => m.split(", ").map((p) => { const [n, w] = p.split(" "); return arCount(n, nounOf(w)); }).join("، ")],
  [/^(\d+) (voyageurs?|véhicules?|places)$/, (m, n, w) => arCount(n, nounOf(w))],
  [/^(\d+) filtres? actifs?$/, (m, n) => `${arCount(n, "filtre")} ${+n === 1 ? "مفعّل" : "مفعّلة"}`],
  [/^Afficher (\d+) traversées?$/, (m, n) => `عرض ${arCount(n, "traversée")}`],
  [/^(Voyageur|Adulte|Enfant|Bébé) (\d+)(?: \/ (\d+))?$/, (m, w, a, b) => `${AR_ROLE[w]} ${a}${b ? ` / ${b}` : ""}`],
  [/^conducteur : (Adulte|Enfant) (\d+)$/, (m, w, n) => `السائق: ${AR_ROLE[w]} ${n}`],
  [/^Accepter (.+)$/, (m, x) => `قبول ${tr(x)}`],
  [/^estimation (.+)$/, (m, x) => `تقدير ${tr(x)}`],
  [/^Montant approuvé : (.+)$/, (m, x) => `المبلغ المعتمد: ${tr(x)}`],
  [/^Dans (\d+) (?:s|secondes)$/, (m, n) => `خلال ${arCount(n, "seconde")}`],
  [/^Valide jusqu’à (\d{1,2}:\d{2})$/, (m, t) => `صالح حتى ${t}`],
  [/^Entre (\d{1,2}:\d{2}) et (\d{1,2}:\d{2})$/, (m, a, b) => `بين ${a} و${b}`],
  [new RegExp(`^Rechercher le (\\d{1,2}) ${MONTH}$`, "i"), (m, d, mo) => `البحث يوم ${d} ${arMonth(mo)}`],
  [/^(Référence|Dossier|Ouvrir) (\S+-\d+)$/, (m, w, id) => `${{ Référence: "المرجع", Dossier: "الملف", Ouvrir: "فتح" }[w]} ${id}`],
  [/^Privée (\d+) pl\.$/, (m, n) => `خاصة · ${arCount(n, "place")}`],
  [/^(\d+) sièges pour le groupe$/, (m, n) => `${arCount(n, "siège")} للمجموعة`],
  [/^(\d+) cabines? pour vos (\d+) voyageurs$/, (m, a, b) => `${arCount(a, "cabine")} لـ${arCount(b, "voyageur")}`],
  [/^Filtres \((\d+)\)$/, (m, n) => `التصفية (${n})`],
  [/^il y a (\d+) min$/, (m, n) => `منذ ${n} د`],
  [/^(?:vérifié|Vérifiées) le (\d{2}\/\d{2}\/\d{4})$/, (m, d) => `تم التحقق في ${d}`],
  [/^(.+) est consultable et modifiable dans cette simulation\.$/, (m, x) => `${tr(x)} قابل للعرض والتعديل في هذه المحاكاة.`],
  // Codes and ids stay as they are.
  [/^(?:[A-Z]+-)*[A-Z]+-?\d+$|^[ACSV]\d{1,2}$/, (m) => m],
];
function tr(text) {
  const key = text.replace(/\s+/g, " ").trim();
  if (!/[A-Za-zÀ-ÿ]/.test(key)) return text; // numbers, symbols or already Arabic
  let out = AR[key];
  if (out === undefined)
    for (const [re, f] of AR_PATTERNS) {
      const m = key.match(re);
      if (m) {
        out = f(...m);
        break;
      }
    }
  // UI joins: "A · B", "Alger → Marseille" (the arrow turns to follow the reading direction).
  if (out === undefined && / · | → /.test(key))
    out = key
      .split(/( · | → )/)
      .map((p) => (p === " → " ? " ← " : p === " · " ? p : tr(p)))
      .join("");
  if (out === undefined) {
    (window.arMissing ||= new Set()).add(key);
    return text;
  }
  return text.match(/^\s*/)[0] + out + text.match(/\s*$/)[0];
}
// Translate a rendered tree in place: text, labels and placeholders.
function translateTree(root) {
  if (!root) return;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  for (let n; (n = walker.nextNode()); ) if (/[A-Za-zÀ-ÿ]/.test(n.nodeValue)) n.nodeValue = tr(n.nodeValue);
  for (const el of root.querySelectorAll("[placeholder], [aria-label], [title]"))
    for (const a of ["placeholder", "aria-label", "title"]) if (el.hasAttribute(a)) el.setAttribute(a, tr(el.getAttribute(a)));
  // Prefilled values (example data, dimensions); e-mail addresses and codes stay as typed.
  for (const el of root.querySelectorAll("input[value]:not([type=email])")) el.value = tr(el.value);
}

const APP_NAV = [
  ["Recherche", "C01", "search", "البحث"],
  ["Favoris", "C17", "heart", "المفضلة"],
  ["Voyages", "C32", "ticket", "رحلاتي"],
  ["Compte", "C41", "user", "حسابي"],
];
function renderCustomer(d) {
  // Arabic covers the whole customer app; the home screen has its own Arabic layout.
  const arabic = state.lang === "AR",
    arabicHome = arabic && d.id === "C01";
  let title = arabicHome ? "رحلتك البحرية" : d.title;
  if (d.id === "C22") title = "Voyageur " + (state.traveler + 1) + " / " + totalTravelers();
  let body = arabicHome ? renderArabic() : d.id === "C51" ? operatorsPanel() : d.id === "C52" ? policyPanel() : d.elements.map(component).join("");
  if (d.id === "C11") body += resultsPanel();
  if (d.id === "C03")
    body =
      btnRow("Départ", dateText(state.departureISO), "state.dateContext='departureISO';render()", true, state.dateContext === "departureISO" ? "is-editing" : "") +
      (state.tripMode === 1
        ? btnRow("Retour", dateText(state.returnISO), "state.dateContext='returnISO';render()", true, state.dateContext === "returnISO" ? "is-editing" : "")
        : "") +
      body;
  if (d.id === "C01" && state.tripMode === 1)
    body += btnRow(arabicHome ? "تاريخ العودة" : "Retour", dateText(state.returnISO), "state.dateContext='returnISO';go('C03')");
  if (d.id === "C25" && state.priceDelta)
    body += btnRow("Ajustement accepté", money(state.priceDelta), "toast('Variation de tarif acceptée dans le scénario C16.')", false);
  if (d.id === "C25" && (!state.quoteExpiry || Date.now() > state.quoteExpiry)) body += btn("Revalider le devis", "rowAction('Revalider le devis')");
  const totals = ["C23", "C24", "C25", "C26", "C27", "C59"].includes(d.id)
    ? `<div class="action-total"><span>Total du groupe · démo</span><strong data-value>${money(quoteTotal())}</strong></div>`
    : "";
  renderPhone({
    cls: arabic ? "rtl" : "",
    rtl: arabic,
    header: appHeader(
      title,
      arabic
        ? `<button class="language" onclick="toggleLang()" aria-label="عرض التطبيق بالفرنسية" lang="fr">FR</button>`
        : `<button class="language" onclick="toggleLang()" aria-label="Afficher l’app en arabe" lang="ar">AR</button>`,
      arabic,
    ),
    screen: `<div class="screen-id">${d.id} · PROTOTYPE V3 · AUCUN ACHAT RÉEL</div>${arabicHome ? "" : rail(d.id)}<main id="body" class="app-body">${body}</main><div class="actionbar">${totals}<p id="gateHint" role="status" class="gate-hint"></p><button id="primary" class="primary" onclick="primary()">${arabicHome ? "البحث عن الرحلات" : esc(d.cta)}</button></div>`,
    nav: tabBar(
      "Navigation de l’application",
      APP_NAV.map(([n, to, ic, ar]) => ({ label: arabic ? ar : n, icon: ic, onclick: `navTo('${to}','tab')`, active: n === d.nav })),
    ),
  });
  updatePrimary();
  if (arabic) translateTree($("#viewport .phone"));
}

/* Staff app (Espace équipe) ----------------------------------------------- */
const STAFF_SECTIONS = [
  [
    "Opérations",
    [
      ["A02", "Vue opérations", "gauge"],
      ["A03", "Devis assisté", "quote"],
      ["A05", "Dossiers", "folder"],
      ["A07", "Paiements", "card"],
      ["A09", "Remboursements", "reset"],
    ],
  ],
  [
    "Plateforme",
    [
      ["A12", "Compagnies", "ship"],
      ["A15", "Catalogue", "book"],
      ["A16", "Équipe & audit", "users"],
      ["A18", "Confidentialité", "lock"],
      ["A20", "Incidents", "alert"],
    ],
  ],
  ["Lancement Algérie", staff.slice(20).map((s) => [s.id, s.title, "flag"])],
];
const STAFF_TABS = [
  ["A02", "Opérations", "gauge"],
  ["A05", "Dossiers", "folder"],
  ["A07", "Paiements", "card"],
  ["A20", "Incidents", "alert"],
];
// Screens opened from another section's shortcuts belong to that section.
const STAFF_PARENT = { A04: "A03", A06: "A05", A08: "A05", A10: "A05", A11: "A05", A19: "A05", A13: "A15", A14: "A15", A17: "A16" };
const staffSection = (id) => STAFF_PARENT[id] || id;

function staffTone(v) {
  if (/^(Actif|Active|Approuvé|Confirmée|Reçue|Devis préparé)/.test(v)) return "ok";
  if (/(inconnu|Délai dépassé|Bloqué|Suspendu)/i.test(v)) return "off";
  if (/(attente|À valider|À approuver|À appliquer|Vérifier|Revoir|Invitation|Limitée|Dégradé)/.test(v)) return "wait";
  return "info";
}
function renderStaff(d) {
  if (d.id === "A01") return renderStaffSignIn();
  const a = d.data,
    section = staffSection(d.id);
  const metrics = a[4].map(([lab, val], j) => `<div style="--j:${j}"><span>${esc(lab)}</span><strong data-count>${esc(val)}</strong></div>`).join("");
  // Table rows become cards: first column as title, status as a pill, the rest as pairs.
  const statusCol = a[5].findIndex((h) => /^(État|Statut|Résultat|Décision)$/.test(h));
  const records = a[6]
    .map((r, j) => {
      const pill = statusCol > 0 ? `<span class="pill" data-tone="${staffTone(r[statusCol])}">${esc(r[statusCol])}</span>` : "";
      const pairs = r
        .map((c, k) => (k > 0 && k !== statusCol ? `<div><dt>${esc(a[5][k])}</dt><dd>${esc(c)}</dd></div>` : ""))
        .join("");
      return `<button class="record" style="--j:${j}" onclick="staffAction('${d.id}')"><span class="record-head"><span><small>${esc(a[5][0])}</small><strong>${esc(r[0])}</strong></span>${pill}</span><dl>${pairs}</dl>${icon("chevronRight", "row-chevron")}</button>`;
    })
    .join("");
  const chart = ["A02", "A12", "A20"].includes(d.id)
    ? `<div class="chart" data-key="chart"><span>Dossiers / erreurs · exemple sur 6 heures</span><div>${[24, 32, 21, 45, 38, 29]
        .map((v, i) => `<figure style="--j:${i}"><i style="height:${v * 2}px" data-v="${v}"></i><figcaption>${9 + i}:00</figcaption></figure>`)
        .join("")}</div></div>`
    : "";
  const extras = d.id === "A05" ? ["A06", "A07", "A08", "A09", "A10", "A11", "A19"] : d.id === "A15" ? ["A13", "A14"] : d.id === "A16" ? ["A17"] : [];
  const shortcuts = extras.length
    ? `<h3 class="eyebrow" data-key="label:shortcuts">ACCÈS RAPIDES</h3>${extras.map((id) => btnRow(byId[id].title, id, `navTo('${id}','forward')`)).join("")}`
    : "";
  const review = a[7]
    .map((f) => {
      const at = f.indexOf(" : "),
        [label, value] = at > 0 ? [f.slice(0, at), f.slice(at + 3)] : ["À vérifier", f];
      const control = value.length > 30 ? `<textarea aria-label="Détail de revue" rows="2">${esc(value)}</textarea>` : `<input aria-label="Détail de revue" value="${esc(value)}">`;
      return `<label class="field"${dk("field", f)}><span class="field-label">${esc(label)}</span>${control}</label>`;
    })
    .join("");
  const tabs = STAFF_TABS.map(([id, label, ic]) => ({ label, icon: ic, onclick: `navTo('${id}','tab')`, active: section === id }));
  tabs.push({ label: "Plus", icon: "grid", onclick: "openStaffMenu()", active: !STAFF_TABS.some(([id]) => id === section) });
  renderPhone({
    cls: "staff",
    header: appHeader(d.title, `<button class="header-action" aria-label="Actualiser les données" onclick="staffRefresh(this)">${icon("refresh")}</button>`),
    screen: `<div class="screen-id">${d.id} · ESPACE ÉQUIPE · DONNÉES DÉMO</div><div class="ptr" aria-hidden="true"><i>${icon("refresh")}</i></div><main id="body" class="app-body"><div class="role-line" data-key="role">${icon("user")}<span><strong>${esc(d.role)}</strong><small>Agence démo · MFA · session vérifiée</small></span></div><label class="field has-icon" data-key="field:search">${icon("search", "field-icon")}<span class="field-label">Rechercher</span><input type="search" aria-label="Rechercher un dossier" placeholder="Dossier, référence…" enterkeyhint="search" oninput="filterRecords(this.value)"></label><div class="metrics" data-key="metrics">${metrics}</div>${chart}<h3 class="eyebrow" data-key="label:records">DOSSIERS & ÉLÉMENTS À REVOIR</h3><div class="record-list" data-key="records">${records}</div><p class="record-status" role="status" data-key="records:status"></p>${shortcuts}<h3 class="eyebrow" data-key="label:review">REVUE & ACTION</h3>${review}${noticeBlock("Contrôle avant action", d.guardrail, "warning")}</main><div class="actionbar">${btn(d.primary_action, `staffAction('${d.id}')`, "primary")}</div>`,
    nav: tabBar("Navigation de l’espace équipe", tabs),
  });
}
function renderStaffSignIn() {
  renderPhone({
    cls: "staff-login",
    screen: `<main id="body" class="app-body login-body"><div class="login-hero" data-key="hero"><span class="brand-mark reverse" role="img" aria-label="Delphin"></span><h1 id="screenHeading" tabindex="-1">Espace équipe</h1><p>Des dossiers clairs.<br>Des accès maîtrisés.</p><svg class="login-waves" viewBox="0 0 400 120" preserveAspectRatio="none" aria-hidden="true"><path d="M-40 70 C 30 48 110 92 200 70 S 360 48 440 70"/><path d="M-40 88 C 40 68 120 108 210 88 S 360 68 440 88"/><path d="M-40 104 C 50 88 130 120 220 104 S 360 90 440 104"/></svg></div><div class="login-card" data-key="card"><span class="eyebrow">ÉTAPE 2 / 2</span><h2>Connexion sécurisée</h2><label class="field has-icon">${icon("user", "field-icon")}<span class="field-label">Identité professionnelle</span><input value="S•••@exemple.com · vérifiée" readonly></label><label class="field has-icon">${icon("users", "field-icon")}<span class="field-label">Organisation / rôle</span><input value="Agence démo · Support" readonly></label><label class="field has-icon">${icon("lock", "field-icon")}<span class="field-label">Code authentificateur</span><input id="staffCode" inputmode="numeric" autocomplete="one-time-code" placeholder="Code de démonstration : 123456" onkeydown="if(event.key==='Enter')staffSignIn()"></label><p class="muted">Connexion réservée aux membres autorisés. MFA et accès par organisation.</p></div></main><div class="actionbar">${btn("Vérifier et ouvrir la session", "staffSignIn()", "primary")}<p class="login-note">Aucun compte partagé · accès de démonstration</p></div>`,
  });
}
