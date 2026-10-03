
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
  const counter = $$(".tools button", $("#body")).find((b) => /filtres actifs/.test(b.textContent));
  if (counter) counter.textContent = activeFilterCount() + " filtres actifs";
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
  if (state.id === "C12" && (label === "Compagnie" || label === "Durée maximale")) {
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
    return;
  }
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
  modal(
    label,
    `<p>${esc(label)} est consultable et modifiable dans cette simulation.</p><label>Détail de démonstration<input aria-label="Détail" value="Exemple"></label><p class="muted">Aucune action sur un service réel.</p>`,
    [{ label: "Fermer" }, { label: "Enregistrer", primary: true, fn: () => toast("Modification conservée dans le prototype.", "success") }],
  );
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
  status.textContent = !q ? "" : shown ? `${shown} sur ${cards.length}` : `Aucun élément ne correspond à « ${query.trim()} ».`;
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
function back() {
  const target = state.history.pop() || DATA.back[state.id] || "C01";
  if (!Motion.intent) Motion.intent = target === state.id ? "edge" : "back";
  go(target, false);
}
function render() {
  const d = byId[state.id] || screens[0];
  const snap = Motion.capture(d);
  if (d.id.startsWith("A")) renderStaff(d);
  else renderCustomer(d);
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
