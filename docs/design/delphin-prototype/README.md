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
  and hint, 1,075 strings in Modern Standard Arabic with Algerian usage (month names such as جوان and
  أوت, prices in دج). Live values (dates, prices, traveller counts, timers) go through patterns with
  Arabic number agreement. The staff app stays in French.
- The layout mirrors right to left: rows, fields, the step rail, the timeline, progress bars, the budget
  slider, and every arrow and chevron, which turn to point along the reading direction.
- Translations are for the prototype: have a native Arabic copywriter review them before release.

**Screen-by-screen audit**

Every control on the 90 screens was pressed and every screen reviewed at phone size, in French, Arabic
and the dark theme. What it found, now fixed:

- Rows do what they say. About 110 rows opened the same dialog ("… est consultable et modifiable",
  an "Exemple" box and "Enregistrer"), even for a total, a fee or a reference. Now amounts, statuses
  and timers are plain rows; references copy to the clipboard; short lists (roof load, trailer, engine,
  minor's residence, alert frequency…) open a picker and the row shows the choice; actions ask first
  (close another session, remove a favourite, add an attachment, stop an alert, edit contact details,
  a traveller or the vehicle dimensions); information rows open a sheet with the caveat and, where the
  research has one, the official source.
- The booking follows your choices: route, date, travellers and vehicle carry through the search,
  results, nearby dates, trip detail, price change, options, ticket, queue and no-result screens. A
  nearby-date row now picks that day ("Rechercher le 19 août"), the price-change button shows the new
  total, the options button counts the options taken, and the Arabic home shows the actual travellers
  and vehicle instead of fixed text.
- Tabs change what they show: "Historique" in Mes voyages, "Créer un compte" at sign-in, "Messages" and
  "Préférences" in notifications. "Sans véhicule" and "Sans animal" put the vehicle or animal details
  aside and the button reads "Continuer sans …"; towing a trailer now leads through the trailer screen.
- The dates screen no longer repeats the departure or shows a return field on a one-way trip.
- Controls that did nothing or misled: "Coordonnées personnelles" pointed at its own screen; the
  active-filter count was a button whose dialog showed another count ("0 filtres actifs" now reads
  "0 filtre actif" and is a status); the guest screen had two buttons to the same place; locked filters
  gave no reason; "Actualiser les résultats" gave no sign it had run.
- Back goes where it should: a staff screen opened directly no longer goes "back" into the customer
  app, and a home screen with nothing to return to has no back button (it only bounced).
- A ticket copy saved on the documents screen shows as saved; prompts such as "Décrivez ce qui bloque…"
  are placeholders, not text to delete; the e-mail confirmation field brings up the e-mail keyboard.
- French typography: a no-break space before `:` `;` `!` `?` and inside « », so punctuation never
  starts a line.
- Arabic: validation messages and toasts the earlier pass missed (e-mail mismatch, traveller details,
  document validity, quote revalidation, traveller limit, unknown route, return before departure) are
  translated; source cards turn with the reading direction.
- Accessibility: section titles follow the screen title (h2 under h1), a screen with nothing to tab to
  scrolls from the keyboard, and the screen-list counts meet contrast.
- The inspector's design notes are in French; they were in English for 70 screens.

**Dark theme**

- Light, dark or automatic, which follows the phone or computer setting. The switch sits in the top bar
  and, on a phone, at the top of the screen list (☰); `T` flips between light and dark. The choice is
  remembered.
- Both apps are covered, every screen with its dialogs, sheets and messages: a night-sea palette where
  the gold stays the colour of the main action, and the white logo in the top bar. All text on the 90
  screens meets WCAG AA contrast in both themes (4.5:1, 3:1 for large text), in French and in Arabic.
- The new theme grows as a circle from the switch that was pressed (in browsers with view transitions;
  elsewhere it changes at once).

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
| Theme | The new theme grows as a circle from the switch (620 ms); every colour changes at once inside it. |
| Reduced motion | Same states and focus handling, no movement — short fades only. |

Transitions can be interrupted at any point: a new action settles the running one first. Easing tokens
live in the stylesheet's first section: `--ease-out`, `--ease-in`, `--ease-in-out`, and two damped
springs sampled into CSS `linear()` — `--ease-soft` (≈4 % overshoot) and `--ease-spring` (≈11 %) — with
`cubic-bezier` fallbacks.

## Design tokens

Section 1 of the stylesheet: brand colours (navy `#082C46`, gold `#D5AE66`, ivory `#F7F4EC`, sea
`#1B607A`, foam `#E6EEF0`, ink `#142D3C`, muted `#5C707C`, line `#D8E1E5`, error `#A2383D`, success
`#256949`), type (Inter for UI, Cormorant Garamond for display, Noto Sans Arabic), three elevations,
the focus ring and the motion tokens above. Components use role tokens — `--surface`, `--raised`,
`--heading`, `--selected` and a few more — which section 1b re-maps for the dark theme, so the light
theme is unchanged.

## Keyboard

`←` `→` previous / next screen · `/` search screens · `↑` `↓` move through the list · `F` presentation
mode · `T` light / dark theme · `Esc` close the dialog, the staff “Plus” sheet or the screen list.

## Verification

- Same behaviour as v2: nine scripted journeys (about 140 steps — booking through to the ticket and
  back, round trip, travellers, ports, filters, identity checks, payment outcomes, operator guide,
  dialogs, staff sign-in and dual approval) leave v2 and v3 in identical app state.
- Every control on the 90 screens pressed once (about 1,040): no error, and none does nothing apart from
  the tab already open or the option already chosen.
- axe-core finds no accessibility violation on the 90 screens (French light and dark, Arabic dark; phone
  and desktop).
- All 90 screens render without errors, on desktop and on phones. In Arabic, the 66 customer screens,
  with every control pressed once (dialogs, messages, hints included), show no untranslated text and,
  at the eight phone sizes below, no overflow; French screens are pixel-identical before and after the
  right-to-left work.
- At eight phone sizes (320×640 to 430×932 portrait, plus two landscape sizes) no screen has an element
  past the edge of the phone, clipped text or a page that scrolls sideways.
- With reduced motion requested, nothing longer than a short fade runs.
- Dark theme: no screen keeps a white surface, the device setting and the switch give identical colours,
  and the choice survives a reload. Adding it left the light theme pixel-identical on all 90 screens.
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
| `src/i18n/notes-fr.json` | The design notes the spec gives in English, in French, by screen |
| `src/assets/`, `src/fonts/` | Images and font subsets (licences in `src/fonts/README.md`) |

```bash
python3 docs/design/delphin-prototype/build.py           # write delphin-prototype.html (no dependencies)
python3 docs/design/delphin-prototype/build.py --check   # fails if the built file is out of date
```

Then run the smoke test (every screen, the booking flow, reduced motion, phone dialogs, Arabic, dark theme):

```bash
npm install --no-save playwright && npx playwright install chromium
node docs/design/delphin-prototype/check.mjs
```
