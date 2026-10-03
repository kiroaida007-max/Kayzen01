# Delphin — clickable prototype (v3)

One self-contained HTML file: open [`delphin-prototype.html`](delphin-prototype.html) in any current
browser (double-click works — no server, no network). It holds the 66 customer screens and 24 staff
screens of the Delphin booking design, with demo data only: nothing is bought, sent or stored outside
the browser.

v3 keeps every flow and business rule of v2 (the file as received is the previous commit of this
path) and reworks the experience around them.

## What changed

**Fixes**

- The top-bar logo was broken: the file referenced `delphin-primary.png` without embedding it. The
  navy-wordmark variant is now derived from the reverse logo and embedded.
- In-place updates (steppers, radios, segmented controls, calendar, filters) no longer jump the screen
  back to the top or drop keyboard focus.
- The option subtotal and group total (C24) and the filter counter (C12) update as soon as a choice changes.
- "1 enfants" reads "1 enfant"; the Arabic home's trip toggle shows the current mode.
- The device fits the stage instead of being cut off on laptop-height windows.
- The browser's Back button steps back through the app history.
- Pressing − at a stepper's minimum no longer invalidates the current quote.

**UX**

- A blocked primary button stays focusable: pressing it scrolls to the missing item, highlights and
  focuses it, and says what is needed.
- Fields validate as you type (e-mail check marks, outline on the field that blocks the step); the
  one-time code is formatted and checked.
- Ports are listed under their heading with the current one tagged; results flag the best price or the
  fastest crossing; the comparison flags the lowest total; the calendar shows the whole trip range and
  greys out return dates before departure.
- Statuses carry meaning: received / pending / unavailable dots in rows, coloured pills in staff tables.
- Screen library: search (`/`), journey filters, a selection that follows navigation, previous / next
  (`←` `→`), and a presentation mode (`F`) that hides the side panels.
- The inspector links each screen to its next and previous screens (“Parcours”); the scenario picker
  stays in sync with the screen shown.
- Mobile: slide-in screen list with backdrop, compact top bar; staff tables scroll sideways.

**Code**

- One stylesheet and one script, each in numbered sections (tokens → components → motion; data → state
  → pricing → components → screens → actions → navigation → motion → shell → boot), replacing the three
  stacked override layers of v2.
- Images are parsed once: a render now writes ≈8 KB of markup instead of ≈310 KB.

## Motion

| Situation | Transition |
|---|---|
| Forward — primary button, row, card | The new screen pushes in from the inline end over the dimmed previous one (520 ms, `cubic-bezier(.32,.72,0,1)`); the title cross-slides. |
| Back — ‹, browser Back, return to a parent | The screen slides away to the inline end, revealing the previous one (460 ms). |
| Tab bar, library, scenario, language | Cross-fade; the content settles in reading order (30 ms stagger). |
| Same screen | Moved blocks glide (FLIP, 460 ms), new blocks rise in, removed ones fade; counters roll; amounts count to their new value. |
| Selection | Segmented-control thumb, tab-bar pill, staff-nav and library indicators slide on a soft spring. |
| Feedback | Blocked button shakes, the missing item pulses gold; the button glints once it becomes available; tap ripple on main targets. |
| Entering a screen | Hero headline rises line by line, progress fills, the success check draws, the ticket unfolds, timeline steps arrive in order, staff metrics count up. |
| Reduced motion | Same states and focus handling, no movement — short fades only. |

Transitions can be interrupted at any point: a new action settles the running one first. Easing tokens
live in the stylesheet's first section: `--ease-out`, `--ease-in`, `--ease-in-out`, and two damped
springs sampled into CSS `linear()` — `--ease-soft` (≈4 % overshoot) and `--ease-spring` (≈11 %) — with
`cubic-bezier` fallbacks.

## Design tokens

Section 1 of the stylesheet: brand colours (navy `#082C46`, gold `#D5AE66`, ivory `#F7F4EC`, sea
`#1B607A`, foam `#E6EEF0`, ink `#142D3C`, muted `#5C707C`, line `#D8E1E5`, error `#A2383D`, success
`#256949`), type (Inter for UI, Cormorant Garamond for display, Noto Sans Arabic), three elevations,
the focus ring and the motion tokens above.

## Keyboard

`←` `→` previous / next screen · `/` search screens · `↑` `↓` move through the list · `F` presentation
mode · `Esc` close the dialog or the screen list.

## Verification

- Same behaviour as v2: nine scripted journeys (about 140 steps — booking through to the ticket and
  back, round trip, travellers, ports, filters, identity checks, payment outcomes, operator guide,
  dialogs, staff sign-in and dual approval) leave v2 and v3 in identical app state.
- All 90 screens render without errors, on desktop and at phone width, in French and in Arabic (RTL).
- With reduced motion requested, nothing longer than a short fade runs.
- Cost per interaction in Chromium, layout included: about 7 ms for an in-place update and 11 ms for a
  navigation (v2: 6 ms and 9.5 ms).

After editing the file, run the smoke test (every screen, the booking flow, reduced motion):

```bash
npm install --no-save playwright && npx playwright install chromium
node docs/design/delphin-prototype/check.mjs
```
