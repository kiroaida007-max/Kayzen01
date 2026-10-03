
/* 11. Shell =============================================================== */

// Phone widths and short landscape screens show the app full-screen (same query as the CSS).
const compactQuery = matchMedia("(max-width: 760px), (max-height: 500px)");

/* Screen library: search, journey filters, sliding selection ------------- */
const LIBRARY_FILTERS = [
  ["all", "Tout", () => true],
  ...STAGES.map(([name, ids]) => [name, name, (s) => ids.includes(s.id)]),
  ["after", "Suivi & compte", (s) => s.id.startsWith("C") && stageOf(s.id) < 0],
  ["staff", "Équipe", (s) => s.id.startsWith("A")],
];
let libraryFilter = "all",
  libraryBoxes = new Map();

function buildLibrary() {
  $("#libraryChips").innerHTML = LIBRARY_FILTERS.map(
    ([k, label, test]) =>
      `<button class="chip${k === libraryFilter ? " active" : ""}" data-filter="${k}" aria-pressed="${k === libraryFilter}" onclick="setLibraryFilter('${k}')">${esc(label)}<span>${[...screens, ...staff].filter(test).length}</span></button>`,
  ).join("");
  const item = (s) =>
    `<button class="lib-item" data-id="${s.id}" data-search="${esc(normalize(s.id + " " + s.title))}" onclick="${esc("navTo(" + json(s.id) + ",'jump')")}"><b>${s.id}</b><span>${esc(s.title)}</span></button>`;
  $("#screenList").innerHTML = `<i class="lib-indicator" aria-hidden="true"></i><h3 data-group="C">Application client · ${screens.length}</h3>${screens
    .map(item)
    .join("")}<h3 data-group="A">Espace équipe · ${staff.length}</h3>${staff.map(item).join("")}<p class="lib-empty" hidden>Aucun écran ne correspond.</p>`;
}
function setLibraryFilter(k) {
  libraryFilter = k;
  for (const c of $$("#libraryChips .chip")) {
    c.classList.toggle("active", c.dataset.filter === k);
    c.setAttribute("aria-pressed", String(c.dataset.filter === k));
  }
  filterLibrary();
}
function filterLibrary() {
  const q = normalize($("#librarySearch").value.trim()),
    test = LIBRARY_FILTERS.find((f) => f[0] === libraryFilter)[2],
    shown = { C: 0, A: 0 };
  for (const b of $$("#screenList .lib-item")) {
    const ok = test(byId[b.dataset.id]) && (!q || b.dataset.search.includes(q));
    b.hidden = !ok;
    if (ok) shown[b.dataset.id[0]]++;
  }
  for (const h of $$("#screenList h3")) h.hidden = !shown[h.dataset.group];
  $("#screenList .lib-empty").hidden = shown.C + shown.A > 0;
  measureLibrary();
  placeLibraryIndicator(false);
}
// Item positions only change when the list is filtered or resized, so they are
// measured then and reused: selecting a screen never forces a layout pass.
function measureLibrary() {
  libraryBoxes = new Map($$("#screenList .lib-item").map((b) => [b.dataset.id, { top: b.offsetTop, height: b.offsetHeight }]));
}
function librarySearchKey(e) {
  if (e.key === "Enter") {
    const first = $("#screenList .lib-item:not([hidden])");
    if (first) navTo(first.dataset.id, "jump");
  } else if (e.key === "Escape" && e.target.value) {
    e.target.value = "";
    filterLibrary();
    e.stopPropagation();
  } else if (e.key === "ArrowDown") {
    e.preventDefault();
    $("#screenList .lib-item:not([hidden])")?.focus();
  }
}
function syncLibrary(id) {
  const list = $("#screenList"),
    prev = $(".lib-item.selected", list),
    cur = $(`.lib-item[data-id="${id}"]`, list);
  if (prev !== cur) {
    prev?.classList.remove("selected");
    prev?.removeAttribute("aria-current");
    cur?.classList.add("selected");
    cur?.setAttribute("aria-current", "page");
  }
  placeLibraryIndicator(true);
}
function placeLibraryIndicator(reveal) {
  const ind = $("#screenList .lib-indicator"),
    box = libraryBoxes.get(state.id),
    shown = box && !$(`#screenList .lib-item[data-id="${state.id}"]`)?.hidden;
  if (!ind) return;
  ind.style.opacity = shown ? 1 : 0;
  if (!shown) return;
  ind.style.transform = `translateY(${box.top}px)`;
  ind.style.height = box.height + "px";
  if (reveal) requestAnimationFrame(() => revealInLibrary(box));
}
// Scroll the library so the selected screen is visible (read in the next frame).
function revealInLibrary(box = libraryBoxes.get(state.id)) {
  const lib = $(".library"),
    head = $(".library-head").offsetHeight;
  if (!box || !lib.clientHeight) return;
  const top = $("#screenList").offsetTop + box.top;
  if (top < lib.scrollTop + head + 8 || top + box.height > lib.scrollTop + lib.clientHeight - 8)
    lib.scrollTo({ top: top - head - (lib.clientHeight - head) / 2 + box.height / 2, behavior: Motion.ready && !Motion.reduced() ? "smooth" : "auto" });
}
// Previous / next screen in library order (within the active filter).
function stepScreen(delta) {
  let ids = $$("#screenList .lib-item:not([hidden])").map((b) => b.dataset.id);
  if (!ids.includes(state.id)) ids = order;
  const i = ids.indexOf(state.id);
  navTo(ids[(i + delta + ids.length) % ids.length], delta > 0 ? "forward" : "back");
}

/* Stage header, mode switch & inspector ---------------------------------- */
function routeLinks(d) {
  const staffView = d.id.startsWith("A");
  const next = staffView ? DATA.links.find((l) => l.from === d.id && l.label === "Controlled staff action")?.to : DATA.flows[d.id];
  const chip = (id, intent, label) =>
    id && id !== d.id && byId[id]
      ? `<button class="route-chip ${intent === "back" ? "to-back" : "to-next"}" onclick="${esc("navTo(" + json(id) + ",'" + intent + "')")}"><small>${label}</small><span><b>${id}</b>${esc(byId[id].title)}</span>${icon(intent === "back" ? "arrowLeft" : "arrowRight")}</button>`
      : "";
  const links = chip(next, "forward", staffView ? "Après l’action contrôlée" : "Bouton principal · par défaut") + chip(DATA.back[d.id], "back", "Retour");
  return links || '<p class="route-none">Pas de suite définie pour cet écran.</p>';
}
function syncShell(d, kind) {
  const staffView = d.id.startsWith("A"),
    notes = $$(".inspector [data-note]").map((n) => n.textContent);
  $("#screenTitle").textContent = d.title;
  $("#counter").textContent = d.id + " / " + order.length + " écrans";
  syncLibrary(d.id);
  $("#modeSwitch").dataset.mode = staffView ? "staff" : "client";
  for (const b of $$("#modeSwitch button")) b.setAttribute("aria-pressed", String(b.dataset.mode === (staffView ? "staff" : "client")));
  $("#flowName").textContent = staffView ? "Espace équipe" : "Application client";
  $("#purposeText").textContent = d.description || d.guardrail;
  const contract = DATA.spec.required_screen_contracts.find((s) => s[0] === d.id);
  $("#requiredText").textContent = contract[2];
  $("#stateText").textContent = contract[4];
  $("#serviceText").textContent = contract[3];
  $("#routeLinks").innerHTML = routeLinks(d);
  const select = $("#scenarioSelect");
  select.value = [...select.options].some((o) => o.value === d.id) ? d.id : "";
  if (kind !== "update" && kind !== "boot") Motion.inspector(notes);
}

/* Toast ------------------------------------------------------------------ */
function toast(msg, tone = "info") {
  const t = $("#toast"),
    again = t.classList.contains("show");
  t.textContent = msg;
  t.dataset.tone = tone;
  placeToast();
  t.classList.add("show");
  t.classList.toggle("cycle"); // restarts the countdown bar
  if (again) Motion.bump(t);
  clearTimeout(window.toastTimer);
  window.toastTimer = setTimeout(hideToast, 3400);
}
function hideToast() {
  $("#toast").classList.remove("show");
}
// The toast sits inside the device, just above its action bar.
function placeToast() {
  const t = $("#toast"),
    phone = $("#viewport .phone");
  if (!phone) return;
  const r = phone.getBoundingClientRect();
  t.style.left = r.left + r.width / 2 + "px";
  t.style.bottom = Math.max(16, innerHeight - Math.min(r.bottom, innerHeight) + 138 * Motion.fit) + "px";
  t.style.maxWidth = r.width - 32 + "px";
}

/* Dialog ----------------------------------------------------------------- */
function modal(title, body, actions = []) {
  const m = $("#modal");
  if (m.classList.contains("closing")) {
    m.classList.remove("closing");
    m.close();
  }
  $("#modalTitle").textContent = title;
  $("#modalBody").innerHTML = body;
  $("#modalActions").innerHTML = actions
    .map((a, i) => btn(a.label, `modalAction(${i})`, (a.primary ? "primary" : "secondary") + (a.current ? " is-current" : "")))
    .join("");
  $("#modalActions").dataset.count = actions.length;
  window.modalFns = actions.map((a) => a.fn);
  m.showModal();
}
function modalAction(i) {
  const result = window.modalFns?.[i]?.();
  if (result !== false) closeModal();
}
function closeModal() {
  const m = $("#modal");
  if (!m.open || m.classList.contains("closing")) return;
  if (Motion.reduced()) return m.close();
  m.classList.add("closing");
  setTimeout(() => {
    if (!m.classList.contains("closing")) return;
    m.classList.remove("closing");
    m.close();
  }, 190);
}

/* Focus mode, mobile drawer & stage fit ------------------------------------ */
function toggleFocusMode(force) {
  const on = document.body.classList.toggle("focus-mode", force);
  for (const b of $$("#focusButton, #presentButton")) b.setAttribute("aria-pressed", String(on));
  // On a phone the top bar goes too: say how to bring it back.
  if (compactQuery.matches) on ? toast("Mode présentation · touchez l’écran avec deux doigts pour revenir aux outils.") : hideToast();
  setTimeout(() => {
    measureLibrary();
    placeLibraryIndicator(false);
    if ($("#toast").classList.contains("show")) placeToast();
  }, 600);
}
function toggleMenu(force) {
  const open = document.body.classList.toggle("menu-open", force);
  $("#menuButton").setAttribute("aria-expanded", String(open));
  if (open) requestAnimationFrame(() => revealInLibrary());
}
function closeMenu() {
  if (document.body.classList.contains("menu-open")) toggleMenu(false);
}
// Scale the 390×844 device so it fits the stage without scrolling (never above 1).
function fitPhone() {
  const stage = $(".stage");
  let fit = 1;
  if (!compactQuery.matches) {
    const avail = stage.clientHeight - $(".stage-title").offsetHeight - 80;
    fit = Math.min(1, Math.max(0.68, avail / 844));
  }
  Motion.fit = fit;
  document.documentElement.style.setProperty("--fit", fit.toFixed(4));
}

/* 12. Boot ================================================================ */

$("#brandLogo").src = IMAGES.logo;
buildLibrary();
measureLibrary();
document.fonts?.ready.then(() => {
  measureLibrary();
  placeLibraryIndicator(false);
});
fitPhone();

$("#modal").addEventListener("cancel", (e) => {
  e.preventDefault();
  closeModal();
});
$("#modal").addEventListener("close", () => {
  const m = $("#modal");
  m.classList.remove("closing");
  m.style.removeProperty("transform");
  m.style.removeProperty("--scrim");
});
$("#modal").addEventListener("click", (e) => {
  const m = e.currentTarget,
    r = m.getBoundingClientRect();
  if (performance.now() - Motion.dragEnd < 400) return;
  if (e.target === m && (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom)) closeModal();
});
// On phones the dialog is a bottom sheet: drag it down to close.
Motion.dragSheet($("#modal"), closeModal, (o) => (o ? $("#modal").style.setProperty("--scrim", o) : $("#modal").style.removeProperty("--scrim")));
$("#toast").addEventListener("click", hideToast);
// A vertical wheel over the filter chips scrolls them sideways.
$("#libraryChips").addEventListener(
  "wheel",
  (e) => {
    const c = e.currentTarget;
    if (Math.abs(e.deltaY) <= Math.abs(e.deltaX) || c.scrollWidth <= c.clientWidth) return;
    e.preventDefault();
    c.scrollLeft += e.deltaY;
  },
  { passive: false },
);
$("#screenList").addEventListener("keydown", (e) => {
  if (e.key !== "ArrowDown" && e.key !== "ArrowUp") return;
  const items = $$("#screenList .lib-item:not([hidden])"),
    i = items.indexOf(document.activeElement);
  if (i < 0) return;
  e.preventDefault();
  (items[i + (e.key === "ArrowDown" ? 1 : -1)] || (e.key === "ArrowUp" ? $("#librarySearch") : null))?.focus();
});

// Where the last press happened decides where focus goes after navigation.
document.addEventListener("click", (e) => (Motion.pressed = { el: e.target, t: performance.now() }), true);
document.addEventListener("input", (e) => e.target.closest?.(".field") && (e.target.dataset.touched = "1"), true);
// Water-drop ripple on the main tap targets.
document.addEventListener(
  "pointerdown",
  (e) => {
    const host = e.target.closest?.(".primary, .fare, .operator-card");
    if (!host || e.button > 0 || Motion.reduced() || host.getAttribute("aria-disabled") === "true") return;
    const r = host.getBoundingClientRect(),
      f = host.closest(".device") ? Motion.fit : 1,
      size = (Math.max(r.width, r.height) * 2.2) / f,
      dot = document.createElement("span");
    dot.className = "ripple";
    dot.setAttribute("aria-hidden", "true");
    dot.style.cssText = `left:${(e.clientX - r.left) / f}px;top:${(e.clientY - r.top) / f}px;width:${size}px;height:${size}px`;
    host.append(dot);
    setTimeout(() => dot.remove(), 750);
  },
  { passive: true },
);
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    closeMenu();
    closeStaffMenu();
  }
  if (e.defaultPrevented || e.metaKey || e.ctrlKey || e.altKey || $("#modal").open) return;
  if (e.target.closest?.("input, textarea, select, [contenteditable]")) return;
  if (e.key === "/") {
    e.preventDefault();
    if (compactQuery.matches) toggleMenu(true);
    $("#librarySearch").focus();
    $("#librarySearch").select();
  } else if (e.key === "f" || e.key === "F") {
    toggleFocusMode();
  } else if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
    e.preventDefault();
    stepScreen(e.key === "ArrowRight" ? 1 : -1);
  }
});
let resizeQueued = false;
addEventListener("resize", () => {
  if (resizeQueued) return;
  resizeQueued = true;
  requestAnimationFrame(() => {
    resizeQueued = false;
    fitPhone();
    measureLibrary();
    placeLibraryIndicator(false);
    Motion.placeNavPill();
    if ($("#toast").classList.contains("show")) placeToast();
  });
});
// A two-finger tap toggles presentation mode: on a phone, the way back to the tools.
let twoFingers = null;
document.addEventListener(
  "touchstart",
  (e) => (twoFingers = e.touches.length === 2 ? { t: e.timeStamp, at: new Map([...e.touches].map((p) => [p.identifier, [p.clientX, p.clientY]])) } : null),
  { passive: true },
);
document.addEventListener(
  "touchmove",
  (e) => {
    const moved = (p) => {
      const o = twoFingers?.at.get(p.identifier);
      return o && Math.hypot(p.clientX - o[0], p.clientY - o[1]) > 12;
    };
    if (twoFingers && [...e.changedTouches].some(moved)) twoFingers = null; // a pinch or a scroll
  },
  { passive: true },
);
document.addEventListener(
  "touchend",
  (e) => {
    if (!twoFingers || e.touches.length) return;
    const quick = e.timeStamp - twoFingers.t < 450;
    twoFingers = null;
    if (quick) toggleFocusMode();
  },
  { passive: true },
);
document.addEventListener("touchcancel", () => (twoFingers = null), { passive: true });

// Browser back/forward: a step back through the app history slides back.
window.addEventListener("hashchange", () => {
  const id = location.hash.slice(1);
  if (!byId[id] || id === state.id) return;
  if (state.history.at(-1) === id) {
    Motion.intent = "back";
    back();
  } else {
    Motion.intent = "jump";
    go(id, false);
  }
});

if (byId[location.hash.slice(1)]) state.id = location.hash.slice(1);
render();
// Opened from a phone's home screen, or with ?app or #app in the address: start in presentation mode.
if (new URLSearchParams(location.search).has("app") || location.hash === "#app" || matchMedia("(display-mode: standalone)").matches || navigator.standalone)
  toggleFocusMode(true);
