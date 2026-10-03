
/* 10. Motion ============================================================== */
/* Spatial model
   - forward: the new screen pushes in from the inline end over the old one;
   - back: the old screen slides away to the inline end, revealing the previous;
   - tab / jump / lang: cross-fade, then the content settles in reading order;
   - update (same screen): nothing is torn down visually — scroll and focus
     stay put, moved blocks glide (FLIP), changed values roll or count.
   Every transition can be interrupted: a new render settles the running one
   first. prefers-reduced-motion keeps the state changes but swaps movement
   for short fades. */

const linearOK = typeof CSS !== "undefined" && CSS.supports?.("transition-timing-function", "linear(0, 1)");
const EASE = {
  out: "cubic-bezier(.22,1,.36,1)",
  in: "cubic-bezier(.4,0,1,1)",
  inOut: "cubic-bezier(.65,0,.35,1)",
  push: "cubic-bezier(.32,.72,0,1)",
  // Damped springs sampled into linear(): soft ≈ 4 % overshoot, snappy ≈ 11 %.
  soft: linearOK
    ? "linear(0, 0.0381, 0.1315, 0.2542, 0.3873, 0.5177, 0.6371, 0.7408, 0.8269, 0.8954, 0.9474, 0.9852, 1.0108, 1.0268, 1.0353, 1.0383, 1.0376, 1.0344, 1.0299, 1.0248, 1.0197, 1.015, 1.0109, 1.0074, 1.0046, 1.0025, 1.0009, 0.9998, 0.9991, 0.9987, 0.9985, 0.9985, 1)"
    : "cubic-bezier(.3,1.25,.5,1)",
  snappy: linearOK
    ? "linear(0, 0.0515, 0.1775, 0.3415, 0.5151, 0.6784, 0.819, 0.9309, 1.0126, 1.0663, 1.0959, 1.1065, 1.1033, 1.0913, 1.0743, 1.0558, 1.038, 1.0224, 1.0097, 1.0003, 0.9939, 0.9903, 0.9887, 0.9888, 0.9899, 0.9916, 0.9936, 0.9955, 0.9973, 0.9987, 0.9998, 1.0005, 1)"
    : "cubic-bezier(.34,1.56,.64,1)",
};
const MARKS = ["selected", "is-start", "is-end", "in-range", "done", "is-editing"];

function parseMoney(t) {
  const m = /^([\d\s]+) DZD$/.exec(String(t).trim());
  return m ? +m[1].replace(/\D/g, "") : null;
}
function focusPath() {
  const vp = $("#viewport"),
    el = document.activeElement;
  if (!el || el === document.body || !vp.contains(el)) return null;
  const path = [];
  for (let n = el; n !== vp; n = n.parentElement) path.unshift([...n.parentElement.children].indexOf(n));
  return { path, tag: el.tagName, label: el.getAttribute("aria-label") || el.textContent.trim(), sel: [el.selectionStart, el.selectionEnd] };
}
function restoreFocus(f) {
  if (!f) return;
  const vp = $("#viewport");
  let n = vp;
  for (const i of f.path) if (!(n = n?.children[i])) break;
  if (!n || n.tagName !== f.tag || (n.getAttribute("aria-label") || n.textContent.trim()) !== f.label)
    n = $$(f.tag, vp).find((x) => (x.getAttribute("aria-label") || x.textContent.trim()) === f.label) || n;
  if (!n || n.tagName !== f.tag) return;
  n.focus({ preventScroll: true });
  if (f.sel[0] != null && n.setSelectionRange)
    try {
      n.setSelectionRange(f.sel[0], f.sel[1]);
    } catch (e) {}
}

const Motion = {
  intent: null, // forward | back | tab | jump | lang | refresh | update | edge
  shown: null, // id currently on stage
  ready: false,
  fit: 1, // scale of the device on the desktop stage
  pressed: null,
  dragEnd: 0, // when the last sheet drag ended, so it never counts as a tap
  running: new Set(),
  media: matchMedia("(prefers-reduced-motion: reduce)"),

  reduced() {
    return this.media.matches || document.documentElement.dataset.motion === "reduce";
  },
  anim(el, frames, opts = {}, done) {
    if (!el?.animate) return done?.(), null;
    let a;
    try {
      a = el.animate(frames, { fill: "backwards", ...opts });
    } catch (e) {
      return done?.(), null;
    }
    const entry = { a, ran: false };
    entry.end = () => {
      if (entry.ran) return;
      entry.ran = true;
      this.running.delete(entry);
      done?.();
    };
    a.onfinish = a.oncancel = entry.end;
    this.running.add(entry);
    return a;
  },
  // Jump every running transition to its end state (and run its cleanup).
  settle() {
    for (const e of [...this.running]) {
      try {
        e.a.finish();
      } catch (err) {}
      e.end();
    }
  },
  pressedInside(sel) {
    const p = this.pressed;
    return !!p && performance.now() - p.t < 900 && !!p.el?.closest?.(sel);
  },

  /* Snapshot the outgoing view ------------------------------------------- */
  capture(d) {
    this.settle();
    const same = this.shown === d.id,
      intent = this.intent;
    const kind =
      this.shown === null ? "boot" : same ? (["update", "lang", "edge"].includes(intent) ? intent : intent ? "refresh" : "update") : intent || "jump";
    const vp = $("#viewport"),
      body = $("#body");
    const snap = { kind, to: d.id, reduced: this.reduced(), scroll: body?.scrollTop || 0, focus: focusPath() };
    snap.fromApp = this.pressedInside(".phone, #modal");
    snap.title = $(".app-header #screenHeading", vp);
    snap.nav = $(".bottomnav", vp)?.getAttribute("aria-label");
    snap.navIndex = $$(".bottomnav > button", vp).findIndex((b) => b.classList.contains("active"));
    snap.stage = this.shown ? stageOf(this.shown) : -1;
    snap.total = $(".action-total strong", vp)?.textContent;
    if (kind === "update") {
      snap.items = this.measure();
      snap.segs = $$(".seg, .operator-tabs", body || vp).map((g) => {
        const btns = $$(":scope > button", g),
          i = btns.findIndex((b) => b.classList.contains("active"));
        return i < 0 ? null : { i, box: this.box(btns[i], g) };
      });
      snap.blocked = $("#primary")?.getAttribute("aria-disabled") === "true";
    } else if (kind !== "boot") {
      snap.screen = $(".screen:not(.ghost)", vp);
      snap.heroes = $$(".hero-media", snap.screen || vp).map((m) => getComputedStyle(m).transform);
    }
    return snap;
  },
  box(el, container) {
    const r = el.getBoundingClientRect(),
      c = container.getBoundingClientRect(),
      f = this.fit;
    return { x: (r.left - c.left) / f + container.scrollLeft, y: (r.top - c.top) / f, w: r.width / f, h: r.height / f };
  },
  measure() {
    const map = new Map(),
      seen = {},
      body = $("#body");
    if (!body) return map;
    for (const el of body.children) {
      if (el.classList.contains("exit-ghost")) continue;
      let k = el.dataset.key || el.tagName + "." + el.className.split(" ")[0] + ":" + el.textContent.trim().slice(0, 24);
      seen[k] = (seen[k] || 0) + 1;
      if (seen[k] > 1) k += "#" + seen[k];
      map.set(k, { el, rect: el.getBoundingClientRect(), sig: this.sig(el) });
    }
    return map;
  },
  sig(el) {
    return el.textContent + "|" + $$("input", el).map((i) => (i.type === "checkbox" || i.type === "radio" ? i.checked : i.value)).join(",");
  },

  /* Play the transition for the new view --------------------------------- */
  play(snap) {
    if (snap.kind === "boot") this.boot();
    else if (snap.kind === "update") this.update(snap);
    else this.navigate(snap);
    this.decorate(snap);
    this.shown = snap.to;
    this.intent = null;
  },
  boot() {
    $("#body")?.classList.add("enter");
    setTimeout(() => {
      document.body.classList.remove("booting");
      this.ready = true;
    }, 1500);
  },
  navigate(snap) {
    const vp = $("#viewport"),
      phone = $(".phone", vp),
      body = $("#body");
    body?.classList.add("enter");
    if (phone && snap.screen && !["refresh", "edge"].includes(snap.kind)) {
      const dir = ["forward", "back"].includes(snap.kind) && !snap.reduced ? snap.kind : null;
      if (dir) this.slide(snap, dir);
      else this.crossfade(snap);
      this.swapTitle(snap, dir);
      this.railProgress(snap);
    } else if (snap.kind === "edge") {
      if (!snap.reduced)
        this.anim($(".screen", phone), [{ transform: "none" }, { transform: "translateX(12px)", offset: 0.35 }, { transform: "none" }], {
          duration: 360,
          easing: EASE.out,
        });
    } else if (snap.kind === "refresh") {
      if (!snap.reduced) body?.classList.add("stagger");
    }
    if (snap.fromApp) $("#screenHeading", vp)?.focus({ preventScroll: true });
  },
  // Re-use the outgoing screen as a frozen, inert layer for the transition.
  ghost(snap, layer) {
    const g = snap.screen;
    if (!g) return null;
    g.classList.add("ghost", layer);
    g.inert = true;
    g.setAttribute("aria-hidden", "true");
    for (const n of [g, ...$$("[id]", g)]) n.removeAttribute("id");
    $(".app-body", g)?.classList.remove("enter", "stagger");
    $$(".hero-media", g).forEach((m, i) => {
      m.style.animation = "none";
      m.style.transform = snap.heroes?.[i] || "";
    });
    return g;
  },
  slide(snap, dir) {
    const stack = $(".screen-stack"),
      next = $(".screen:not(.ghost)", stack),
      ghost = this.ghost(snap, dir === "forward" ? "under" : "over");
    if (!ghost || !next) return this.crossfade(snap);
    const scrim = document.createElement("i");
    scrim.className = "screen-scrim";
    if (dir === "forward") stack.prepend(ghost, scrim);
    else stack.append(scrim, ghost);
    const oldBody = $(".app-body", ghost);
    if (oldBody) oldBody.scrollTop = snap.scroll;
    const s = $(".phone")?.dir === "rtl" ? -1 : 1,
      opts = { duration: dir === "forward" ? 520 : 460, easing: EASE.push };
    const cleanup = () => {
      ghost.remove();
      scrim.remove();
      next.classList.remove("is-entering");
    };
    if (dir === "forward") {
      next.classList.add("is-entering");
      this.anim(next, [{ transform: `translateX(${100 * s}%)` }, { transform: "none" }], opts);
      this.anim(ghost, [{ transform: "none" }, { transform: `translateX(${-28 * s}%)` }], { ...opts, fill: "forwards" });
      this.anim(scrim, [{ opacity: 0 }, { opacity: 1 }], { ...opts, fill: "forwards" }, cleanup);
    } else {
      ghost.classList.add("is-leaving");
      this.anim(ghost, [{ transform: "none" }, { transform: `translateX(${100 * s}%)` }], { ...opts, fill: "forwards" });
      this.anim(next, [{ transform: `translateX(${-28 * s}%)` }, { transform: "none" }], opts);
      this.anim(scrim, [{ opacity: 1 }, { opacity: 0 }], { ...opts, fill: "forwards" }, cleanup);
    }
  },
  crossfade(snap) {
    const stack = $(".screen-stack"),
      next = $(".screen:not(.ghost)", stack),
      ghost = this.ghost(snap, "over");
    if (!next) return;
    // Before the ghost's scroll position forces layout, so style is computed once.
    if (!snap.reduced) $("#body")?.classList.add("stagger");
    if (ghost) {
      stack.append(ghost);
      const oldBody = $(".app-body", ghost);
      if (oldBody) oldBody.scrollTop = snap.scroll;
      this.anim(ghost, [{ opacity: 1 }, { opacity: 0 }], { duration: snap.reduced ? 160 : 220, easing: EASE.in, fill: "forwards" }, () => ghost.remove());
    }
    if (snap.reduced) return;
    this.anim(next, [{ opacity: 0, transform: "translateY(10px)" }, { opacity: 1, transform: "none" }], { duration: 420, delay: 60, easing: EASE.out });
  },
  swapTitle(snap, dir) {
    const h1 = $("#screenHeading"),
      old = snap.title;
    if (!h1 || !old || old === h1 || old.textContent === h1.textContent || !h1.closest(".app-header")) return;
    const s = (dir === "back" ? -1 : dir === "forward" ? 1 : 0) * ($(".phone")?.dir === "rtl" ? -1 : 1);
    old.removeAttribute("id");
    old.removeAttribute("tabindex");
    old.setAttribute("aria-hidden", "true");
    old.className = "title-ghost";
    old.style.cssText = "";
    h1.parentElement.append(old);
    this.anim(old, [{ opacity: 1, transform: "none" }, { opacity: 0, transform: `translateX(${-18 * s}px)` }], { duration: 150, easing: EASE.in, fill: "forwards" }, () =>
      old.remove(),
    );
    this.anim(h1, [{ opacity: 0, transform: `translateX(${24 * s}px)` }, { opacity: 1, transform: "none" }], {
      duration: snap.reduced ? 160 : 420,
      delay: snap.reduced ? 0 : 120,
      easing: EASE.out,
    });
  },
  railProgress(snap) {
    const fill = $(".rail-progress"),
      to = stageOf(snap.to),
      from = snap.stage;
    if (!fill || from === to || snap.reduced) return;
    const scale = (i) => `scaleX(${(i + 1) / STAGES.length})`;
    this.anim(fill, [{ transform: scale(from) }, { transform: scale(to) }], { duration: 820, delay: 160, easing: EASE.out });
    if (to > from)
      this.anim($(".step-rail .current b"), [{ transform: "scale(.3)", opacity: 0 }, { transform: "none", opacity: 1 }], { duration: 560, delay: 320, easing: EASE.snappy });
  },

  /* Same-screen updates ---------------------------------------------------- */
  update(snap) {
    const body = $("#body");
    if (body) body.scrollTop = snap.scroll;
    restoreFocus(snap.focus);
    if (snap.title && snap.title.textContent !== $("#screenHeading")?.textContent) this.swapTitle(snap, null);
    if (snap.reduced || !body) return;
    const after = this.measure(),
      f = this.fit,
      dir = this.segments(snap);
    let entering = 0;
    for (const [k, cur] of after) {
      const prev = snap.items.get(k);
      if (!prev) {
        this.anim(cur.el, [{ opacity: 0, transform: "translateY(10px) scale(.98)" }, { opacity: 1, transform: "none" }], {
          duration: 380,
          delay: 60 + entering++ * 40,
          easing: EASE.out,
        });
        continue;
      }
      const dx = (prev.rect.left - cur.rect.left) / f,
        dy = (prev.rect.top - cur.rect.top) / f;
      if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5)
        this.anim(cur.el, [{ transform: `translate(${dx}px, ${dy}px)` }, { transform: "none" }], { duration: 460, easing: EASE.out });
      if (prev.sig !== cur.sig) this.changed(prev.el, cur.el, dir);
      this.marks(prev.el, cur.el);
    }
    for (const [k, prev] of snap.items) if (!after.has(k)) this.exit(prev, body);
    const total = $(".action-total strong");
    if (total && snap.total && snap.total !== total.textContent) this.tween(total, parseMoney(snap.total), parseMoney(total.textContent));
    if (snap.blocked && $("#primary")?.getAttribute("aria-disabled") === "false") this.sheen($("#primary"));
  },
  // Slide a thumb from the previously active option to the new one.
  segments(snap) {
    let dir = 0;
    $$(".seg, .operator-tabs", $("#body")).forEach((g, gi) => {
      const before = snap.segs?.[gi],
        btns = $$(":scope > button", g),
        i = btns.findIndex((b) => b.classList.contains("active"));
      if (!before || i < 0 || before.i === i) return;
      dir ||= Math.sign(i - before.i);
      const a = before.box,
        b = this.box(btns[i], g),
        thumb = document.createElement("i");
      thumb.className = "seg-thumb";
      thumb.style.cssText = `width:${b.w}px;height:${b.h}px;transform:translate(${b.x}px,${b.y}px)`;
      g.classList.add("is-sliding");
      g.prepend(thumb);
      this.anim(
        thumb,
        [
          { transform: `translate(${a.x}px,${a.y}px)`, width: a.w + "px" },
          { transform: `translate(${b.x}px,${b.y}px)`, width: b.w + "px" },
        ],
        { duration: 460, easing: EASE.soft },
        () => {
          thumb.remove();
          g.classList.remove("is-sliding");
        },
      );
    });
    return dir;
  },
  changed(prevEl, curEl, dir) {
    const olds = $$("[data-value]", prevEl);
    $$("[data-value]", curEl).forEach((n, i) => {
      const o = olds[i]?.textContent;
      if (o == null || o === n.textContent) return;
      const a = parseMoney(o),
        b = parseMoney(n.textContent);
      if (a != null && b != null) return this.tween(n, a, b);
      if (/^\d+$/.test(o) && /^\d+$/.test(n.textContent)) return this.roll(n, o, +n.textContent > +o ? 1 : -1);
      this.anim(n, [{ opacity: 0, transform: "translateY(5px)" }, { opacity: 1, transform: "none" }], { duration: 320, easing: EASE.out });
    });
    const oldInputs = $$("input", prevEl);
    $$("input", curEl).forEach((n, i) => {
      const o = oldInputs[i];
      if (o && o.type !== "checkbox" && o.type !== "radio" && o.value !== n.value)
        this.anim(n, [{ opacity: 0, transform: `translateX(${14 * (dir || 1)}px)` }, { opacity: 1, transform: "none" }], { duration: 340, easing: EASE.out });
    });
  },
  markSet(el) {
    const s = new Set();
    for (const n of [el, ...$$("." + MARKS.join(",."), el)]) for (const c of MARKS) if (n.classList.contains(c)) s.add(c + "|" + (n === el ? "" : n.textContent.trim()));
    return s;
  },
  // Elements that gained a state class get a short "just-…" animation.
  marks(prevEl, curEl) {
    const before = this.markSet(prevEl);
    let r = 0;
    for (const n of [curEl, ...$$("." + MARKS.join(",."), curEl)])
      for (const c of MARKS) {
        if (!n.classList.contains(c) || before.has(c + "|" + (n === curEl ? "" : n.textContent.trim()))) continue;
        n.style.setProperty("--r", r++);
        n.classList.add("just-" + c);
        setTimeout(() => n.classList.remove("just-" + c), 900);
      }
  },
  exit(prev, body) {
    const el = prev.el,
      br = body.getBoundingClientRect(),
      f = this.fit;
    if (!el || el.isConnected) return;
    el.classList.add("exit-ghost");
    el.inert = true;
    el.setAttribute("aria-hidden", "true");
    for (const n of [el, ...$$("[id]", el)]) n.removeAttribute("id");
    el.style.cssText += `;left:${(prev.rect.left - br.left) / f}px;top:${(prev.rect.top - br.top) / f + body.scrollTop}px;width:${prev.rect.width / f}px`;
    body.append(el);
    this.anim(el, [{ opacity: 1, transform: "none" }, { opacity: 0, transform: "scale(.97)" }], { duration: 240, easing: EASE.in, fill: "forwards" }, () => el.remove());
  },
  // Count a money value to its new amount (exact final text guaranteed).
  tween(node, from, to) {
    if (from == null || to == null || from === to) return;
    const final = node.textContent,
      t0 = performance.now(),
      D = 560;
    const tick = (t) => {
      if (!node.isConnected) return;
      const p = Math.min(1, (t - t0) / D);
      node.textContent = p < 1 ? money(from + (to - from) * (1 - Math.pow(1 - p, 3))) : final;
      if (p < 1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
    node.classList.remove("flash");
    void node.offsetWidth;
    node.classList.add("flash");
  },
  roll(node, oldText, dir) {
    const box = node.parentElement,
      ghost = document.createElement("span");
    ghost.className = "roll-ghost";
    ghost.textContent = oldText;
    ghost.setAttribute("aria-hidden", "true");
    box.append(ghost);
    this.anim(ghost, [{ transform: "none", opacity: 1 }, { transform: `translateY(${-90 * dir}%)`, opacity: 0 }], { duration: 260, easing: EASE.in, fill: "forwards" }, () =>
      ghost.remove(),
    );
    this.anim(node, [{ transform: `translateY(${90 * dir}%)`, opacity: 0 }, { transform: "none", opacity: 1 }], { duration: 380, easing: EASE.snappy });
  },

  /* Runs after every render --------------------------------------------- */
  decorate(snap) {
    const vp = $("#viewport");
    // Looping decoration keeps its phase across re-renders instead of restarting.
    const phase = -((performance.now() / 1000) % 52) + "s";
    $$(".hero-media", vp).forEach((m) => (m.style.animationDelay = phase));
    this.placeNavPill(snap);
    this.bindScroll(snap.kind === "update" ? snap.scroll : 0);
    if (snap.kind !== "update") this.countUp();
  },
  placeNavPill(snap = {}) {
    const nav = $(".bottomnav"),
      pill = nav && $(".nav-pill", nav);
    if (!pill) return;
    const btns = $$(":scope > button", nav),
      n = btns.length,
      i = btns.findIndex((b) => b.classList.contains("active"));
    pill.hidden = i < 0;
    if (i < 0) return;
    // Centre of column k inside the nav's 4px side padding (mirrored in RTL); no layout reads.
    const rtl = !!nav.closest('[dir="rtl"]'),
      at = (k) => `calc(4px + ${(rtl ? n - 1 - k : k) + 0.5} * (100% - 8px) / ${n} - var(--pill-w) / 2)`;
    pill.style.left = at(i);
    const from = snap.navIndex;
    if (snap.kind === "update" || from == null || from < 0 || from === i || snap.reduced || snap.nav !== nav.getAttribute("aria-label")) return;
    this.anim(
      pill,
      [
        { left: at(from), transform: "scaleX(1)" },
        { left: at((from + i) / 2), transform: "scaleX(1.5)", offset: 0.45 },
        { left: at(i), transform: "scaleX(1)" },
      ],
      { duration: 520, easing: EASE.inOut },
    );
    this.anim($(".nav-icon", btns[i]), [{ transform: "none" }, { transform: "translateY(-2px) scale(1.16)", offset: 0.4 }, { transform: "none" }], {
      duration: 480,
      delay: 140,
      easing: EASE.out,
    });
  },
  // Header hairline once content scrolls under it; gentle hero parallax.
  bindScroll(initial) {
    const body = $("#body"),
      phone = $(".phone");
    if (!body || !phone) return;
    const media = $(".hero-media", body);
    let queued = false;
    const update = (y = body.scrollTop) => {
      queued = false;
      phone.classList.toggle("is-scrolled", y > 4);
      if (media && !this.reduced()) media.style.translate = `0 ${Math.min(y, 220) * 0.32}px`;
    };
    body.addEventListener(
      "scroll",
      () => {
        if (!queued) {
          queued = true;
          requestAnimationFrame(update);
        }
      },
      { passive: true },
    );
    update(initial);
  },
  countUp() {
    if (this.reduced()) return;
    $$(".metrics [data-count]").forEach((el, j) => {
      // Only values that start with a number count up (never identifiers like INC-DEMO-1).
      const final = el.textContent,
        m = /^(\d[\d\s]*\d|\d)(.*)$/.exec(final);
      if (!m) return;
      const [, num, post] = m,
        pre = "",
        grouped = /\D/.test(num),
        target = +num.replace(/\D/g, ""),
        t0 = performance.now() + 140 + j * 90,
        D = 900;
      const fmt = (v) => pre + (grouped ? Math.round(v).toLocaleString("fr-FR") : String(Math.round(v))) + post;
      el.textContent = fmt(0);
      const tick = (t) => {
        if (!el.isConnected) return;
        const p = Math.min(1, Math.max(0, (t - t0) / D));
        el.textContent = p < 1 ? fmt(target * (1 - Math.pow(1 - p, 4))) : final;
        if (p < 1) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
  },

  /* Micro-interactions --------------------------------------------------- */
  // Lead the user to whatever blocks the primary action.
  guide(reason) {
    this.shake($("#primary"));
    const t = gateTarget(reason),
      body = $("#body");
    if (!t || !body) return;
    const tr = t.getBoundingClientRect(),
      br = body.getBoundingClientRect(),
      f = this.fit;
    if (tr.top < br.top + 12 || tr.bottom > br.bottom - 12)
      body.scrollTo({ top: body.scrollTop + (tr.top - br.top) / f - (br.height - tr.height) / f / 2, behavior: this.reduced() ? "auto" : "smooth" });
    t.classList.remove("attention");
    void t.offsetWidth;
    t.classList.add("attention");
    clearTimeout(t.attentionTimer);
    t.attentionTimer = setTimeout(() => t.classList.remove("attention"), 1800);
    (t.matches("input, button") ? t : $("input:not([disabled]), button", t))?.focus({ preventScroll: true });
  },
  shake(el) {
    navigator.vibrate?.(12);
    if (!el || this.reduced()) return;
    this.anim(
      el,
      [
        { transform: "none" },
        { transform: "translateX(-7px)" },
        { transform: "translateX(6px)" },
        { transform: "translateX(-4px)" },
        { transform: "translateX(2px)" },
        { transform: "none" },
      ],
      { duration: 420, easing: "ease-out" },
    );
  },
  nudge(label) {
    const count = $$(".stepper", $("#body")).find((s) => s.dataset.key === "stepper:" + label);
    this.shake($(".count", count));
  },
  sheen(el) {
    if (!el || this.reduced()) return;
    el.classList.remove("sheen");
    void el.offsetWidth;
    el.classList.add("sheen");
    clearTimeout(el.sheenTimer);
    el.sheenTimer = setTimeout(() => el.classList.remove("sheen"), 1100);
  },
  bump(el) {
    if (!this.reduced()) this.anim(el, [{ scale: "1" }, { scale: "1.025" }, { scale: "1" }], { duration: 260, easing: EASE.out });
  },
  reveal(el) {
    if (!this.reduced()) this.anim(el, [{ opacity: 0, transform: "translateY(4px)" }, { opacity: 1, transform: "none" }], { duration: 260, easing: EASE.out });
  },
  spin(el) {
    if (el && !this.reduced()) this.anim(el, [{ transform: "rotate(0)" }, { transform: "rotate(360deg)" }], { duration: 700, easing: EASE.out });
  },
  restagger(els) {
    if (this.reduced()) return;
    els.forEach((el, i) =>
      this.anim(el, [{ opacity: 0.25, transform: "translateY(6px)" }, { opacity: 1, transform: "none" }], { duration: 420, delay: i * 60, easing: EASE.out }),
    );
  },
  // Bottom sheet: slides up over a fading scrim; reversed to close.
  sheet(layer, open, done) {
    const panel = $(".sheet", layer),
      scrim = $(".sheet-scrim", layer);
    if (this.reduced()) return done?.();
    if (open) {
      const opts = { duration: 460, easing: EASE.push };
      this.anim(scrim, [{ opacity: 0 }, { opacity: 1 }], opts);
      this.anim(panel, [{ transform: "translateY(100%)" }, { transform: "none" }], opts, done);
    } else {
      // Closing starts where a drag left the sheet.
      const opts = { duration: 260, easing: EASE.in, fill: "forwards" };
      this.anim(scrim, [{ opacity: scrim.style.opacity || 1 }, { opacity: 0 }], opts);
      this.anim(panel, [{ transform: panel.style.transform || "none" }, { transform: "translateY(100%)" }], opts, done);
    }
  },
  // Bottom sheets follow a drag from their handle or title. Let go past a third of the
  // height, or flick down, and the sheet closes; otherwise it springs back.
  dragSheet(panel, close, fade) {
    panel.addEventListener("pointerdown", (e) => {
      const handle = $(".sheet-handle", panel);
      if (e.button > 0 || !handle?.getClientRects().length || !e.target.closest(".sheet-handle, .sheet-handle + h2")) return;
      e.preventDefault();
      panel.setPointerCapture(e.pointerId);
      const y0 = e.clientY,
        scale = panel.getBoundingClientRect().height / panel.offsetHeight || 1,
        h = panel.offsetHeight;
      let dy = 0,
        speed = 0,
        last = { y: y0, t: e.timeStamp };
      const move = (ev) => {
        dy = Math.max(0, (ev.clientY - y0) / scale);
        speed = (ev.clientY - last.y) / scale / Math.max(1, ev.timeStamp - last.t);
        last = { y: ev.clientY, t: ev.timeStamp };
        panel.style.transform = `translateY(${dy}px)`;
        fade(String(1 - Math.min(1, dy / h) * 0.85));
      };
      const up = () => {
        panel.removeEventListener("pointermove", move);
        panel.removeEventListener("pointerup", up);
        panel.removeEventListener("pointercancel", up);
        if (dy > 3) this.dragEnd = performance.now();
        if (dy > h / 3 || (speed > 0.5 && dy > 12)) return close();
        panel.style.transform = "";
        fade("");
        if (dy > 0 && !this.reduced()) this.anim(panel, [{ transform: `translateY(${dy}px)` }, { transform: "none" }], { duration: 380, easing: EASE.soft });
      };
      panel.addEventListener("pointermove", move);
      panel.addEventListener("pointerup", up);
      panel.addEventListener("pointercancel", up);
    });
  },
  // Fade the inspector notes that changed with the screen.
  inspector(before) {
    if (this.reduced()) return;
    $$(".inspector [data-note]").forEach((el, i) => {
      if (before[i] !== el.textContent) this.anim(el, [{ opacity: 0, transform: "translateY(4px)" }, { opacity: 1, transform: "none" }], { duration: 380, delay: i * 30, easing: EASE.out });
    });
  },
};
