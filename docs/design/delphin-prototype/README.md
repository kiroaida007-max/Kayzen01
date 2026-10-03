# Delphin — clickable prototype (v3)

One self-contained HTML file: open [`delphin-prototype.html`](delphin-prototype.html) in any current
browser (double-click works — no server, no network). All 90 screens of the Delphin booking design are
phone-app screens — 66 in the customer app, 24 in the staff app (Espace équipe) — with demo data only:
nothing is bought, sent or stored outside the browser.

v3 keeps every flow and business rule of v2 (the file as received is the previous commit of this
path) and reworks the experience around them.

## What changed

**Arabic**

- The whole customer app reads in Arabic (the AR button in the header): every screen, dialog, message
  and hint, 1,093 strings in Modern Standard Arabic with Algerian usage (month names such as جوان and
  أوت, prices in دج). Live values (dates, prices, traveller counts, timers) go through patterns with
  Arabic number agreement. The staff app stays in French.
- The layout mirrors right to left: rows, fields, the step rail, the timeline, progress bars, the budget
  slider, and every arrow and chevron, which turn to point along the reading direction.
- Translations are for the prototype: have a native Arabic copywriter review them before release.

**Mobile**

- Every screen is an app screen. The 24 staff screens, a wide desktop dashboard in v2, are now the
  *Espace équipe* phone app: navy header, tab bar (Opérations, Dossiers, Paiements, Incidents, Plus), a
  “Plus” bottom sheet for all 14 sections, tables shown as tappable record cards, the main action pinned
  above the tab bar, and a mobile sign-in.
- On a phone the app fills the screen and the screen list becomes a slide-in drawer. Layouts hold from
  320 px wide (iPhone SE, first generation) to 430 px; narrow phones get tighter spacing and type.
- Readable on a phone: no text inside the app is smaller than 11 px (only the prototype's screen-code line
  is 10 px), body text is a step larger than in v2, and every tap target is at least 44 px. On the
  narrowest phones the step rail keeps the label of the current step only.
- Dialogs open as bottom sheets on phones, their buttons kept in reach above long content. Sheets close
  with a drag down from their handle or title, a tap on the dimmed screen, or `Esc`.
- Landscape phones get slimmer chrome and side-by-side tab labels, which nearly doubles the height left
  for content.
- Presentation mode on a phone: “Mode présentation” in the screen list (☰) hides the prototype's top bar,
  so only the app shows; a two-finger tap brings the tools back. Add `?app` or `#app` to the address to open
  in it directly. Added to a phone's home screen (from a web address), the prototype opens full screen, in this
  mode, under its own icon.
- On desktop every screen is shown in the device, scaled to fit the window.

**Fixes**

- The top-bar logo was broken: the file referenced `delphin-primary.png` without embedding it. The
  navy-wordmark variant is now derived from the reverse logo and embedded.
- In-place updates (steppers, radios, segmented controls, calendar, filters) no longer jump the screen
  back to the top or drop keyboard focus.
- The option subtotal and group total (C24) and the filter counter (C12) update as soon as a choice changes.
- "1 enfants" reads "1 enfant"; the Arabic home's trip toggle shows the current mode.
- Components position by start and end (logical properties), so the same rules serve both reading
  directions instead of per-language overrides.
- The device fits the stage instead of being cut off on laptop-height windows.
- The browser's Back button steps back through the app history.
- Pressing − at a stepper's minimum no longer invalidates the current quote.
- Messages (toasts) use the phone's width instead of wrapping at half of it.

**UX**

- A blocked primary button stays focusable: pressing it scrolls to the missing item, highlights and
  focuses it, and says what is needed.
- Fields validate as you type (e-mail check marks, outline on the field that blocks the step); the
  one-time code is formatted and checked.
- Ports are listed under their heading with the current one tagged; results flag the best price or the
  fastest crossing; the comparison flags the lowest total; the calendar shows the whole trip range and
  greys out return dates before departure.
- Statuses carry meaning: received / pending / unavailable dots in rows, coloured pills on staff records.
- Staff search narrows the record cards as you type, ignoring accents and case; on a phone, pulling a
  staff list down from the top refreshes it.
- Screen library: search (`/`), journey filters, a selection that follows navigation, previous / next
  (`←` `→`), and a presentation mode (`F`) that hides the side panels.
- The inspector links each screen to its next and previous screens (“Parcours”); the scenario picker
  stays in sync with the screen shown.

**Code**

- One stylesheet and one script, each in numbered sections (tokens → components → motion; data → state
  → pricing → components → screens → actions → navigation → motion → shell → boot), replacing the three
  stacked override layers of v2.
- Images are parsed once: a render now writes ≈8 KB of markup instead of ≈310 KB.
- The file is 1.4 MB instead of 4.8 MB: fonts are subset to the characters in use and compressed (WOFF2),
  and the Figma frame export, which the prototype never reads, stays out of the build.

## Motion

| Situation | Transition |
|---|---|
| Forward — primary button, row, card | The new screen pushes in from the inline end over the dimmed previous one (520 ms, `cubic-bezier(.32,.72,0,1)`); the title cross-slides. |
| Back — ‹, browser Back, return to a parent | The screen slides away to the inline end, revealing the previous one (460 ms). |
| Tab bar, library, scenario, language | Cross-fade; the content settles in reading order (30 ms stagger). |
| Same screen | Moved blocks glide (FLIP, 460 ms), new blocks rise in, removed ones fade; counters roll; amounts count to their new value. |
| Selection | Segmented-control thumb, tab-bar pill (customer and staff apps) and library indicator slide on a soft spring. |
| Pull to refresh | Staff lists follow the finger with resistance over a refresh indicator; past 64 px they hold while refreshing, then spring back. Only a downward drag from the top pulls; any other drag scrolls. |
| Sheets | The staff “Plus” menu, and dialogs on phones, slide up over a dimmed screen (460 ms). A drag moves the sheet with the finger and lightens the dimming; let go past a third of its height, or flick, and it closes, otherwise it springs back. |
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
mode · `Esc` close the dialog, the staff “Plus” sheet or the screen list.

## Verification

- Same behaviour as v2: nine scripted journeys (about 140 steps — booking through to the ticket and
  back, round trip, travellers, ports, filters, identity checks, payment outcomes, operator guide,
  dialogs, staff sign-in and dual approval) leave v2 and v3 in identical app state.
- All 90 screens render without errors, on desktop and on phones. In Arabic, the 66 customer screens,
  with every control pressed once (dialogs, messages, hints included), show no untranslated text and,
  at the eight phone sizes below, no overflow; French screens are pixel-identical before and after the
  right-to-left work.
- At eight phone sizes (320×640 to 430×932 portrait, plus two landscape sizes) no screen has an element
  past the edge of the phone, clipped text or a page that scrolls sideways.
- With reduced motion requested, nothing longer than a short fade runs.
- Cost per interaction in desktop Chromium, layout included: about 6 ms for an in-place update and 8 ms
  for a navigation, 10 ms in the staff app (v2: 5 ms and 6 ms).

## Source and build

`delphin-prototype.html` is generated: edit the files in `src/`, then rebuild.

| Path | Holds |
|---|---|
| `src/index.html` | The page: top bar, screen list, stage, inspector, dialog |
| `src/styles.css` | The stylesheet, in numbered sections (tokens first, responsive and motion last) |
| `src/app/1-core.js` … `5-shell.js` | The script, in load order: data and state, screens, actions, motion, shell |
| `src/data.json` | Screens, flows, operators, ports and research sources (demo data) |
| `src/i18n/ar.json` | Arabic for every French string the customer app shows, keyed by the French text |
| `src/assets/`, `src/fonts/` | Images and font subsets (licences in `src/fonts/README.md`) |

```bash
python3 docs/design/delphin-prototype/build.py           # write delphin-prototype.html (no dependencies)
python3 docs/design/delphin-prototype/build.py --check   # fails if the built file is out of date
```

Then run the smoke test (every screen, the booking flow, reduced motion, phone dialogs, Arabic):

```bash
npm install --no-save playwright && npx playwright install chromium
node docs/design/delphin-prototype/check.mjs
```
