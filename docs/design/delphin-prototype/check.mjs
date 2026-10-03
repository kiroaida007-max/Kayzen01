// Smoke test for delphin-prototype.html: every screen renders, the booking flow
// reaches the ticket, reduced motion leaves no long animation, dialogs are bottom
// sheets on a phone, the customer app is fully Arabic in Arabic, the dark theme covers
// every screen, and nothing logs an error. Usage (from the repository root):
//   npm install --no-save playwright && npx playwright install chromium
//   node docs/design/delphin-prototype/check.mjs
import { chromium } from "playwright";

const url = new URL("./delphin-prototype.html", import.meta.url).href;
const failures = [];
const check = (ok, message) => ok || failures.push(message);

const browser = await chromium.launch();
const errors = [];
async function open(options = {}) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 920 }, ...options });
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("console", (m) => m.type() === "error" && errors.push(m.text()));
  await page.goto(url);
  await page.waitForTimeout(500);
  return page;
}

// 1. Every screen renders with its own title.
let page = await open();
const screens = await page.evaluate(() => order.map((id) => ({ id, title: byId[id].title })));
for (const { id, title } of screens) {
  const shown = await page.evaluate((id) => {
    state = JSON.parse(JSON.stringify(seed));
    navTo(id, "jump");
    // Every screen titles its phone header; A01 is the staff sign-in.
    const heading = document.querySelector(".app-header #screenHeading");
    return { id: state.id, heading: heading?.textContent, login: !!document.querySelector(".staff-login") };
  }, id);
  check(shown.id === id, `${id}: navigation ended on ${shown.id}`);
  if (id === "A01") check(shown.login, "A01: sign-in screen not shown");
  else if (id !== "C22") check(shown.heading === title, `${id}: heading "${shown.heading}", expected "${title}"`);
}

// 2. The booking flow reaches the ticket through the primary button alone.
const reached = await page.evaluate(() => {
  state = JSON.parse(JSON.stringify(seed));
  navTo("C01", "jump");
  for (let i = 0; i < 60 && state.id !== "C31"; i++) {
    const body = document.querySelector("#body");
    if (state.id === "C11") {
      selectFare(0);
      continue;
    }
    if (state.id === "C19") document.querySelector("#otp").value = "123456";
    if (state.id === "C21")
      for (const input of body.querySelectorAll("input[type=email]")) {
        input.value = "demo@exemple.com";
        input.dispatchEvent(new Event("input", { bubbles: true }));
      }
    if (gateReason()) for (const input of body.querySelectorAll(".choice input:not(:checked):not(:disabled)")) input.click();
    primary();
  }
  return state.id;
});
check(reached === "C31", `booking flow stopped on ${reached}, expected C31`);
await page.close();

// 3. Reduced motion: navigation happens, nothing longer than a short fade keeps running.
page = await open({ reducedMotion: "reduce" });
await page.evaluate(() => primary());
await page.waitForTimeout(300);
const long = await page.evaluate(() => document.getAnimations().filter((a) => a.playState === "running").length);
check(long === 0, `reduced motion: ${long} animation(s) still running after 300 ms`);
await page.close();

// 4. On a phone, dialogs open as bottom sheets.
page = await open({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
const sheet = await page.evaluate(async () => {
  navTo("C31", "jump");
  documentDemo();
  await new Promise((r) => setTimeout(r, 600));
  const r = document.querySelector("#modal").getBoundingClientRect();
  return { left: r.left, width: r.width, bottom: r.bottom };
});
check(sheet.left === 0 && sheet.width === 390 && Math.round(sheet.bottom) === 844, `phone dialog is not a bottom sheet: ${JSON.stringify(sheet)}`);
await page.close();

// 5. Arabic: every customer screen reads right to left with no untranslated text.
page = await open({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
const arabic = await page.evaluate(() => {
  const wrong = [];
  for (const id of order.filter((x) => x.startsWith("C"))) {
    state = JSON.parse(JSON.stringify(seed));
    state.lang = "AR";
    navTo(id, "jump");
    if (document.querySelector(".phone").dir !== "rtl") wrong.push(id);
  }
  return { wrong, missing: [...(window.arMissing || [])] };
});
check(!arabic.wrong.length, `Arabic: not right to left on ${arabic.wrong.join(", ")}`);
check(!arabic.missing.length, `Arabic: no translation for ${arabic.missing.slice(0, 5).join(" | ")}`);
await page.close();

// 6. Dark theme: the device setting and the switch give the same colours, no screen
// keeps a white surface, and the choice survives a reload.
page = await open({ colorScheme: "dark" });
const tokens = await page.evaluate(() => {
  const rule = [...document.styleSheets].flatMap((s) => [...s.cssRules]).find((r) => r.selectorText === ':root[data-theme="dark"]');
  return [...rule.style].filter((p) => p.startsWith("--"));
});
const read = (names) => names.map((n) => getComputedStyle(document.documentElement).getPropertyValue(n).trim());
const viaDevice = await page.evaluate(read, tokens);
const white = await page.evaluate(() => {
  const found = new Set();
  for (const id of order) {
    state = JSON.parse(JSON.stringify(seed));
    navTo(id, "jump");
    for (const el of document.querySelectorAll("#viewport .phone *"))
      if (getComputedStyle(el).backgroundColor === "rgb(255, 255, 255)" && el.getClientRects().length) found.add(`${id} ${el.tagName.toLowerCase()}.${[...el.classList].join(".")}`);
  }
  return [...found];
});
check(!white.length, `dark theme: white surfaces on ${white.slice(0, 5).join(", ")}`);
await page.close();
page = await open({ colorScheme: "light" });
await page.click('.topbar .theme-switch [data-choice="dark"]');
await page.waitForTimeout(800);
const viaSwitch = await page.evaluate(read, tokens);
const differ = tokens.filter((n, i) => viaDevice[i] !== viaSwitch[i]);
check(tokens.length > 30 && !differ.length, `dark theme: the device and the switch disagree on ${differ.join(", ") || "(no tokens found)"}`);
await page.reload();
await page.waitForTimeout(500);
const kept = await page.evaluate(() => [document.documentElement.dataset.theme, document.querySelector(".topbar .theme-switch").dataset.choice]);
check(kept[0] === "dark" && kept[1] === "dark", `dark theme: not kept after a reload (${kept})`);
await page.close();

await browser.close();
check(errors.length === 0, `page errors: ${errors.join(" | ")}`);
console.log(failures.length ? `✗ ${failures.length} failure(s)\n- ${failures.join("\n- ")}` : `✓ ${screens.length} screens, booking flow, reduced motion, phone dialogs, Arabic and dark theme OK`);
process.exit(failures.length ? 1 : 0);
