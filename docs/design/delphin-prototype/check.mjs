// Smoke test for delphin-prototype.html: every screen renders, the booking flow
// reaches the ticket, reduced motion leaves no long animation, and nothing logs
// an error. Usage (from the repository root):
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

await browser.close();
check(errors.length === 0, `page errors: ${errors.join(" | ")}`);
console.log(failures.length ? `✗ ${failures.length} failure(s)\n- ${failures.join("\n- ")}` : `✓ ${screens.length} screens, booking flow and reduced motion OK`);
process.exit(failures.length ? 1 : 0);
