
/* 8. Actions ============================================================== */

function choose(k, val, el) {
  const id = k.split("-")[0],
    i = +k.split("-")[1];
  if (exclusive.has(id)) {
    for (const [j, a] of byId[id].elements.entries()) if (a[0] === "choice") state.checks[id + "-" + j] = j === i && val;
  } else state.checks[k] = val;
  if (id === "C07" && val) {
    state.cabin = byId[id].elements[i][1];
    invalidateQuote();
  }
  if (id === "C24") invalidateQuote();
  save();
  // C12 and C24 show values derived from these choices (filter count, option
  // subtotal, group total), so they re-render instead of only toggling the card.
  if (exclusive.has(id) || id === "C12" || id === "C24") render();
  else {
    el?.closest(".choice")?.classList.toggle("selected", val);
    updatePrimary();
  }
}
function step(label, delta) {
  const before = state.counts[label] ?? 0;
  const min = ["Adultes", "Cabines privées"].includes(label) ? 1 : 0;
  const next = Math.max(min, Math.min(9, before + delta));
  if (passengerLabels.includes(label) && totalTravelers() - before + next > 9) {
    toast("Pour plus de 9 voyageurs, demandez un devis groupe.", "warn");
    return;
  }
  // At a bound nothing changes, so the current quote stays valid.
  if (next === before) return Motion.nudge(label);
  state.counts[label] = next;
  invalidateQuote();
  save();
  render();
}
function changeBudget(v) {
  state.filter.budget = +v;
  $("#budgetvalue").textContent = money(v);
  $(".slider input", $("#body"))?.style.setProperty("--fill", budgetFill(+v) + "%");
  const counter = $(".tool-status", $("#body"));
  if (counter) counter.textContent = arabicOn() ? tr(filterCountText()) : filterCountText();
  save();
  updatePrimary();
}
function setTab(v) {
  state.tab = v;
  if (["C01", "C03"].includes(state.id)) {
    state.tripMode = v;
    invalidateQuote();
  }
  if (state.id === "C05") {
    state.vehicle = v === 1;
    invalidateQuote();
  }
  if (state.id === "C08") state.pets = v === 1;
  if (state.id === "C13") state.filter.sort = v === 1 ? "duration" : "price";
  if (state.id === "C22") state.traveler = Math.min(totalTravelers() - 1, v);
  save();
  render();
}
function selectPort(v) {
  if (!DATA.ports.some((p) => p.name === v)) return;
  if (state.portContext === "dest" && !connections(state.origin).includes(v)) {
    toast("Cette liaison ne figure pas dans le catalogue vérifié.", "warn");
    return;
  }
  state[state.portContext] = v;
  if (state.portContext === "origin" && !connections(v).includes(state.dest)) state.dest = connections(v)[0] || "";
  invalidateQuote();
  save();
  go("C01");
}
function chooseDay(v) {
  const iso = "2027-08-" + String(v).padStart(2, "0");
  if (state.dateContext === "returnISO" && iso < state.departureISO) {
    toast("Le retour doit être après le départ.", "warn");
    return;
  }
  state[state.dateContext] = iso;
  if (state.returnISO < state.departureISO) state.returnISO = state.departureISO;
  invalidateQuote();
  save();
  render();
}
function otpInput(el) {
  el.value = el.value.replace(/\D/g, "").slice(0, 6);
  const f = el.closest(".field"),
    full = el.value.length === 6,
    ok = el.value === "123456";
  f.classList.toggle("valid", ok);
  f.classList.toggle("invalid", full && !ok);
  if (full) ok ? Motion.sheen($("#primary")) : Motion.shake(f);
}
function rowAction(label) {
  if (label === "Réinitialiser") {
    state.filter = clone(seed.filter);
    for (const k of Object.keys(state.checks)) if (k.startsWith("C12-")) delete state.checks[k];
    save();
    render();
    return;
  }
  const k = state.id + "|" + label;
  if (ROW_ACTIONS[k]) return ROW_ACTIONS[k]();
  if (ROW_OPTIONS[k]) return pickOption(label, ROW_OPTIONS[k]);
  if (label === "Sources publiques & mises à jour") {
    go("C52");
    return;
  }
  if (label === "Revalider le devis") {
    startQuote();
    go("C15");
    return;
  }
  if (/Billet PDF|Billet enregistré|Billet compagnie|Copie locale/.test(label) || /Télécharger|Reçu de paiement/.test(label)) {
    documentDemo();
    return;
  }
  if (label === "Immatriculation") return;
  if (/Exporter|Demander une copie|Supprimer|Rectifier/.test(label)) {
    modal(
      "Demande de confidentialité",
      `<p>${esc(label)} : vérification de l’accès et revue des données concernées.</p><p>Prototype : aucune donnée réelle ne sera exportée ou supprimée.</p>`,
      [{ label: "Revenir" }, { label: "Simuler la demande", primary: true, fn: () => toast("Demande PRV-DEMO-27 enregistrée dans la simulation.", "success") }],
    );
    return;
  }
  if (label === "Vérification accessible") {
    modal("Vérification accessible", "<p>Utilisez l’option assistée pour demander une vérification accessible. Ce prototype ne lance aucun CAPTCHA.</p>", [
      { label: "Fermer" },
      { label: "Accéder à la recherche", primary: true, fn: () => go("C11") },
    ]);
    return;
  }
  if (label === "Ajouter un voyageur") {
    modal("Ajouter un voyageur", '<label>Nom de démonstration<input placeholder="Exemple"></label><p>Cette modification concerne les futures réservations.</p>', [
      { label: "Annuler" },
      { label: "Enregistrer", primary: true, fn: () => toast("Voyageur de démonstration ajouté.", "success") },
    ]);
    return;
  }
  if (/Retrouv|Voyage en invité/.test(label)) {
    go("C21");
    return;
  }
  const value = fieldValue(label, byId[state.id].elements.find((a) => a[1] === label)?.[2] ?? "");
  if (COPY_ROWS.has(label)) return copyReference(value);
  infoSheet(label, value, ROW_SOURCES[k]);
}
// A row that informs: what it says, the caveat and its official sources.
function infoSheet(label, value, sources = []) {
  modal(
    label,
    `<p class="sheet-lead">${esc(value)}</p><p class="muted">Information indicative de la démonstration. Confirmez-la auprès de la compagnie ou de l’autorité concernée avant le départ.</p>${sourceCards(sources)}`,
    [{ label: "Compris", primary: true }],
  );
}
function copyReference(value) {
  const ref = value.split(" · ")[0],
    shown = () => toast("Référence " + ref);
  try {
    navigator.clipboard.writeText(ref).then(() => toast("Référence " + ref + " copiée.", "success"), shown);
  } catch (e) {
    shown();
  }
}
// A short list of values: the row then shows the one picked.
function pickOption(label, options) {
  const current = fieldValue(label, "");
  modal(
    label,
    "<p>Choisissez l’option qui correspond à votre voyage.</p>",
    options.map((v) => ({
      label: v,
      current: v === current,
      fn: () => {
        state.fields[fieldKey(label)] = v;
        // A different vehicle needs its quote checked again (see the C23 notice).
        if (state.id === "C23") vehicleChanged();
        render();
      },
    })),
  );
}
function vehicleChanged() {
  invalidateQuote();
  toast("Véhicule mis à jour · le devis sera vérifié à nouveau.");
}
function filterPicker(label) {
  const company = label === "Compagnie",
    prop = company ? "company" : "duration";
  modal(
    label,
    "<p>Critère appliqué aux offres fictives du groupe.</p>",
    (company ? ["Toutes", "Compagnie A", "Compagnie B"] : [16, 20, 22, 30]).map((v) => ({
      label: company ? v : v + " h",
      current: state.filter[prop] === v,
      fn: () => {
        state.filter[prop] = v;
        save();
        render();
      },
    })),
  );
}
// Filters set by the trip itself (cabin, vehicle) say where to change them.
function lockedFilter() {
  toast("Ce critère suit la configuration de votre voyage : modifiez-la à l’étape Voyage.");
}
function removeFavorite() {
  const favs = favorites();
  if (!favs.length) return;
  modal("Retirer un favori", "<p>Le favori quitte cette liste. Aucune réservation n’est concernée.</p>", [
    ...favs.map((a) => ({
      label: a[1],
      fn: () => {
        (state.favRemoved ||= []).push(a[1]);
        render();
        toast("Favori retiré.", "success");
      },
    })),
    { label: "Annuler" },
  ]);
}
function revokeSession() {
  const label = "Autre appareil · exemple";
  if (fieldValue(label, "") === "Session fermée") return toast("Cette session est déjà fermée.");
  modal("Fermer cette session ?", "<p>L’appareil devra se reconnecter avec votre adresse vérifiée.</p>", [
    { label: "Annuler" },
    {
      label: "Fermer la session",
      primary: true,
      fn: () => {
        state.fields[fieldKey(label)] = "Session fermée";
        render();
        toast("Session fermée sur l’autre appareil.", "success");
      },
    },
  ]);
}
function editDimensions() {
  const label = "Longueur / hauteur",
    [length, height] = fieldValue(label, "4,60 m / 1,65 m").match(/\d+,\d+/g) || ["4,60", "1,65"];
  modal(
    label,
    `<label>Longueur totale (m)<input id="dimLength" inputmode="decimal" autocomplete="off" value="${length}"></label><label>Hauteur totale (m)<input id="dimHeight" inputmode="decimal" autocomplete="off" value="${height}"></label><p class="muted">Charge sur le toit et accessoires compris.</p>`,
    [
      { label: "Annuler" },
      {
        label: "Enregistrer",
        primary: true,
        fn: () => {
          const num = (sel) => parseFloat($(sel).value.replace(",", ".")),
            l = num("#dimLength"),
            h = num("#dimHeight");
          if (!(l >= 2 && l <= 20 && h >= 1 && h <= 5)) {
            toast("Longueur entre 2 et 20 m, hauteur entre 1 et 5 m.", "warn");
            Motion.shake($("#modal"));
            return false;
          }
          const m = (v) => v.toFixed(2).replace(".", ",") + " m";
          state.fields[fieldKey(label)] = m(l) + " / " + m(h);
          vehicleChanged();
          render();
        },
      },
    ],
  );
}
function editContact() {
  modal(
    "Coordonnées personnelles",
    '<label>Adresse e-mail<input type="email" value="vous@exemple.com" autocomplete="off"></label><label>Téléphone<input type="tel" value="+213 555 00 00 00" autocomplete="off"></label><p class="muted">Données fictives uniquement. Une nouvelle adresse doit être vérifiée.</p>',
    [{ label: "Annuler" }, { label: "Enregistrer", primary: true, fn: () => toast("Coordonnées conservées dans la démonstration.", "success") }],
  );
}
function editTraveler() {
  modal(
    "Adulte 2 · démo",
    '<label>Prénom<input value="VOYAGEUR" autocomplete="off"></label><label>Nom · comme sur le document<input value="EXEMPLE" autocomplete="off"></label><p class="muted">Informations fictives uniquement. Un billet déjà émis n’est pas modifié.</p>',
    [{ label: "Annuler" }, { label: "Enregistrer", primary: true, fn: () => toast("Voyageur conservé dans la démonstration.", "success") }],
  );
}
function addAttachment() {
  const label = "Pièce jointe · facultative",
    k = fieldKey(label),
    attach = (name) => {
      name ? (state.fields[k] = name) : delete state.fields[k];
      render();
      toast(name ? "Pièce jointe ajoutée à la demande." : "Pièce jointe retirée.", "success");
    };
  modal("Ajouter une pièce jointe", "<p>Fichier fictif uniquement. Masquez les numéros sensibles avant l’envoi.</p>", [
    { label: "Photo · démo", current: state.fields[k] === "Photo · démo", fn: () => attach("Photo · démo") },
    { label: "Document PDF · démo", current: state.fields[k] === "Document PDF · démo", fn: () => attach("Document PDF · démo") },
    ...(state.fields[k] ? [{ label: "Retirer la pièce jointe", fn: () => attach(null) }] : []),
  ]);
}
function stopAlert() {
  if (!state.alertEnabled) return toast("Aucune alerte active pour le moment.");
  modal("Arrêter l’alerte ?", "<p>Vous ne recevrez plus de message pour cette recherche.</p>", [
    { label: "Garder l’alerte" },
    {
      label: "Arrêter l’alerte",
      primary: true,
      fn: () => {
        state.alertEnabled = false;
        toast("Alerte arrêtée.", "success");
      },
    },
  ]);
}
function documentDemo() {
  modal(
    "Aperçu de document",
    `<div class="ticket"><span class="eyebrow">APERÇU · DÉMONSTRATION</span><h2>${esc(state.origin)} → ${esc(state.dest)}</h2><p>${esc(dateText(state.departureISO))}</p><div class="tear" aria-hidden="true"></div><strong>DÉMO · NON VALABLE POUR EMBARQUER</strong><p>Aucun billet réel, aucun QR actif.</p></div>`,
    [
      { label: "Fermer" },
      {
        label: "Marquer cet aperçu",
        primary: true,
        fn: () => {
          state.saved = true;
          if (state.id === "C34") render();
          toast("Aperçu marqué dans cette session de démonstration.", "success");
        },
      },
    ],
  );
}
function primary() {
  if (!canContinue()) {
    const reason = gateReason();
    Motion.guide(reason);
    toast(reason, "warn");
    return;
  }
  if (state.id === "C05" && !state.vehicle) return go("C07");
  if (state.id === "C05" && check("C05-6")) return go("C06");
  if (state.id === "C11") toast("Résultats actualisés · offres de démonstration.", "success");
  if (state.id === "C42" && state.tab === 0) return toast("Messages marqués comme lus.", "success");
  if (state.id === "C15") {
    startQuote();
    return go("C16");
  }
  if (state.id === "C16") {
    state.priceDelta = 2400;
    return go("C18");
  }
  if (state.id === "C19" && $("#otp")?.value !== "123456") {
    Motion.shake($("#otp")?.closest(".field"));
    toast("Utilisez le code de démonstration 123456.", "warn");
    return;
  }
  if (state.id === "C22" && state.traveler < totalTravelers() - 1) {
    state.traveler++;
    render();
    toast("Passez au voyageur suivant.");
    return;
  }
  if (state.id === "C22" && !state.vehicle) return go("C24");
  if (state.id === "C24") {
    if (!state.quoteExpiry) startQuote();
    return go(state.tripMode === 1 ? "C59" : "C25");
  }
  if (state.id === "C27") state.paymentOutcome = "unknown";
  if (state.id === "C28") state.paymentOutcome = "paid_demo";
  if (state.id === "C61") {
    if (state.paymentOutcome !== "failed_confirmed_demo") {
      go("C28");
      toast("Résultat inconnu: rapprocher avant une nouvelle tentative.", "warn");
      return;
    }
    state.attempt++;
    startQuote();
    return go("C26");
  }
  if (state.id === "C65") {
    state.fields["C44|Objet"] = "Réclamation: traversée perturbée";
    return go("C44");
  }
  if (state.id === "C66") {
    state.alertEnabled = true;
    toast("Alerte activée dans la simulation. Aucun message réel.", "success");
    return go("C01");
  }
  if (state.id === "C34") return documentDemo();
  if (state.id === "C39" && state.refundStage < 3) {
    state.refundStage++;
    render();
    toast("Étape simulée. Aucun mouvement de fonds.");
    return;
  }
  if (["C41", "C42", "C54", "C55"].includes(state.id)) {
    toast("Choix conservé dans cette session de démonstration.", "success");
    if (["C54", "C55"].includes(state.id)) go(DATA.flows[state.id]);
    return;
  }
  if (state.id === "C45") return rowAction("Demander une copie");
  if (state.id === "C44") {
    toast("Demande SUP-DEMO-27 simulée.", "success");
    return go("C33");
  }
  go(DATA.flows[state.id] || "C01");
}
function routeRow(label, target) {
  if (state.id === "C61" && target === "C26" && state.paymentOutcome !== "failed_confirmed_demo") {
    go("C28");
    toast("Rapprochez le résultat inconnu avant une nouvelle tentative.", "warn");
    return;
  }
  if (state.id === "C01" && (label === "Depuis" || label === "Vers")) state.portContext = label === "Depuis" ? "origin" : "dest";
  if (label === "Départ") state.dateContext = "departureISO";
  if (label === "Retour") state.dateContext = "returnISO";
  if (label === "Simuler un refus confirmé") return scenario("C61", "forward");
  if (label === "Conserver mon voyage") toast("Voyage conservé dans la simulation.", "success");
  if (target === state.id) return rowAction(label);
  go(target);
}
function scenario(id, intent = "jump") {
  if (!byId[id]) return;
  if (id === "C16") startQuote();
  if (id === "C61") state.paymentOutcome = "failed_confirmed_demo";
  if (id === "C28" || id === "C60" || id === "C62") state.paymentOutcome = "unknown";
  Motion.intent = intent;
  go(id);
}
function toggleLang() {
  state.lang = state.lang === "FR" ? "AR" : "FR";
  save();
  Motion.intent = "lang";
  render();
}
function resetDemo() {
  state = clone(seed);
  save();
  Motion.intent = "jump";
  go("C01", false);
  toast("Prototype réinitialisé.", "success");
  Motion.spin($("#resetButton .icon"));
}
function staffAction(id) {
  const d = byId[id],
    dual = ["A09", "A20"].includes(id);
  modal(
    d.primary_action,
    `<p>${esc(d.guardrail)}</p><label>Motif de l’action<input id="purpose" placeholder="Motif obligatoire"></label>${
      dual
        ? '<label>Demandeur · démo<input id="requestor" placeholder="Agent A"></label><label>Approbateur · démo<input id="approver" placeholder="Agent B"></label><label class="choice"><input id="approval" type="checkbox"><span>Approbation distincte vérifiée · simulation</span></label>'
        : ""
    }<p class="muted">Aucune action réelle. La production contrôle les rôles et les identités côté serveur.</p>`,
    [
      { label: "Annuler" },
      {
        label: "Simuler la validation",
        primary: true,
        fn: () => {
          const norm = (s) => String(s || "").trim().toLowerCase();
          const purpose = norm($("#purpose")?.value),
            requestor = norm($("#requestor")?.value),
            approver = norm($("#approver")?.value);
          if (!purpose || (dual && (!requestor || !approver || requestor === approver || !$("#approval")?.checked))) {
            toast("Motif requis et, si nécessaire, deux identités distinctes.", "warn");
            Motion.shake($("#modal"));
            return false;
          }
          toast("Revue simulée, journal de production à implémenter.", "success");
          const link = DATA.links.find((l) => l.from === id && l.label === "Controlled staff action");
          go(link?.to || "A02");
        },
      },
    ],
  );
}
function staffSignIn() {
  const input = $("#staffCode"),
    button = $(".staff-login .actionbar .primary");
  if (input.value !== "123456") {
    Motion.shake(input);
    toast("Utilisez le code de démonstration 123456.", "warn");
    input.focus();
    return;
  }
  // A short verified state before the workspace opens, as a real sign-in would.
  button.classList.add("is-busy");
  button.textContent = "Session vérifiée";
  setTimeout(() => state.id === "A01" && navTo("A02", "jump"), Motion.reduced() ? 0 : 420);
}
function staffRefresh(button) {
  toast("Données de démonstration actualisées.", "success");
  Motion.spin($(".icon", button));
  Motion.restagger($$("#body .record"));
}
// Staff search narrows the record cards as you type; accents and case are ignored.
function filterRecords(query) {
  const list = $("#body .record-list"),
    status = $("#body .record-status");
  if (!list) return;
  const q = normalize(query.trim()),
    cards = $$(".record", list);
  const text = (card) => normalize($$("strong, dd, .pill", card).map((e) => e.textContent).join(" "));
  Motion.reflow(list, () => cards.forEach((card) => (card.hidden = !!q && !text(card).includes(q))));
  const shown = cards.filter((card) => !card.hidden).length;
  status.textContent = !q ? "" : shown ? `${shown} sur ${cards.length}` : frenchText(`Aucun élément ne correspond à « ${query.trim()} ».`);
}
// "Plus" opens every staff section in a bottom sheet inside the device.
function openStaffMenu() {
  const phone = $(".phone.staff");
  if (!phone || $(".sheet-layer", phone)) return;
  const section = staffSection(state.id);
  phone.insertAdjacentHTML(
    "beforeend",
    `<div class="sheet-layer"><div class="sheet-scrim" onclick="closeStaffMenu()"></div><section class="sheet" role="dialog" aria-modal="true" aria-label="Toutes les sections de l’espace équipe"><i class="sheet-handle" aria-hidden="true"></i><h2>Espace équipe</h2>${STAFF_SECTIONS.map(
      ([group, items]) =>
        `<h3 class="eyebrow">${esc(group.toUpperCase())}</h3><div class="sheet-grid">${items
          .map(
            ([id, label, ic]) =>
              `<button class="sheet-item${id === section ? " active" : ""}"${id === section ? ' aria-current="page"' : ""} onclick="${esc(`closeStaffMenu(true);navTo('${id}','tab')`)}">${icon(ic)}<span>${esc(label)}</span></button>`,
          )
          .join("")}</div>`,
    ).join("")}</section></div>`,
  );
  const layer = $(".sheet-layer", phone);
  // Modal: everything behind the sheet is out of reach until it closes.
  for (const el of $$(".app-header, .screen-stack, .bottomnav", phone)) el.inert = true;
  Motion.dragSheet($(".sheet", layer), () => closeStaffMenu(), (o) => ($(".sheet-scrim", layer).style.opacity = o));
  Motion.sheet(layer, true);
  ($(".sheet-item.active", layer) || $(".sheet-item", layer)).focus({ preventScroll: true });
}
function closeStaffMenu(immediate) {
  const layer = $(".phone .sheet-layer");
  if (!layer) return;
  for (const el of $$(".phone .app-header, .phone .screen-stack, .phone .bottomnav")) el.inert = false;
  if (immediate) return layer.remove();
  Motion.sheet(layer, false, () => {
    layer.remove();
    $(".bottomnav button:last-of-type")?.focus({ preventScroll: true });
  });
}

/* 9. Navigation & rendering =============================================== */

// Every navigation states its intent; Motion turns it into the matching transition.
function navTo(id, intent) {
  if (!byId[id]) return;
  Motion.intent = intent;
  go(id);
}
function go(id, push = true) {
  if (!byId[id]) {
    Motion.intent = null;
    return;
  }
  if (!Motion.intent) Motion.intent = id === state.id ? "refresh" : !push || DATA.back[state.id] === id ? "back" : "forward";
  if (push && state.id !== id) state.history.push(state.id);
  state.id = id;
  state.tab = 0;
  location.hash = id;
  save();
  render(); // a fresh #body always starts scrolled to the top
  closeMenu();
}
// Where Back leads: the previous screen, else the screen's parent. A staff screen
// opened directly stays in the staff app.
function backTarget() {
  return state.history.at(-1) || DATA.back[state.id] || (state.id.startsWith("A") ? STAFF_PARENT[state.id] || "A02" : "C01");
}
function back() {
  const target = backTarget();
  state.history.pop();
  if (!Motion.intent) Motion.intent = target === state.id ? "edge" : "back";
  go(target, false);
}
function render() {
  const d = byId[state.id] || screens[0];
  const snap = Motion.capture(d);
  if (d.id.startsWith("A")) renderStaff(d);
  else renderCustomer(d);
  // A screen with nothing to tab to still scrolls from the keyboard.
  const body = $("#body");
  if (body && !$("button, a[href], input, select, textarea", body)) body.tabIndex = 0;
  syncShell(d, snap.kind);
  Motion.play(snap);
}
function updatePrimary() {
  const b = $("#primary"),
    reason = gateReason();
  if (b) {
    const first = !b.dataset.ready,
      wasBlocked = b.getAttribute("aria-disabled") === "true";
    b.dataset.ready = "1";
    // aria-disabled keeps the button focusable: pressing it explains what is missing.
    b.setAttribute("aria-disabled", String(!!reason));
    if (state.id === "C12") {
      const fr = "Afficher " + filteredCount() + " traversée" + (filteredCount() === 1 ? "" : "s"),
        label = arabicOn() ? tr(fr) : fr;
      if (b.textContent !== label) {
        b.textContent = label;
        if (!first) Motion.bump(b);
      }
    }
    if (!first && wasBlocked && !reason) Motion.sheen(b);
  }
  const hint = $("#gateHint"),
    text = arabicOn() ? tr(reason) : reason;
  if (hint && hint.textContent !== text) {
    hint.textContent = text;
    if (reason) Motion.reveal(hint);
  }
  syncFieldStates(reason);
}
// Touched fields that block the CTA are outlined; valid e-mail fields get a check.
function syncFieldStates(reason) {
  const body = $("#body");
  if (!body) return;
  const target = gateTarget(reason);
  for (const f of $$(".field", body)) {
    const input = $("input", f);
    if (!input || f.classList.contains("otp")) continue;
    const flagged = f === target && input.dataset.touched === "1";
    f.classList.toggle("invalid", flagged);
    if (input.type === "email") f.classList.toggle("valid", !flagged && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.value));
  }
  const mail = $$(".field", body).find((f) => $("input[type=email]", f)),
    confirm = $$(".field", body).find((f) => /^Confirmer/.test($(".field-label", f)?.textContent || ""));
  if (mail && confirm)
    confirm.classList.toggle("valid", mail.classList.contains("valid") && $("input", confirm).value === $("input", mail).value && !confirm.classList.contains("invalid"));
}
