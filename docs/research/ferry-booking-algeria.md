# Ferry booking to and from Algeria: domain research (October 2026)

This document is the domain reference used to build WAVE's search engine, fare
engine and booking-rules engine. Everything in `backend/src/main/resources/catalog/`
and in `docs/booking-rules.md` is derived from it.

> **Data freshness.** Research was done on 2026-10-02 from public sources (operator
> press releases, operator help pages, and specialised press). Schedules and fares
> change often. In production, WAVE treats these values as **reference data** and
> overrides them with **live data** coming from the ingestion pipeline (`scraper/`)
> or from operator/agency APIs. Every price the app shows carries a
> `priceSource` (`LIVE` or `REFERENCE`) and a `lastUpdated` timestamp, so we never
> present a reference fare as a live one.

---

## 1. Market overview

Seven companies are authorised by the Directorate General of Merchant Marine for
passenger crossings in 2026: Algérie Ferries, Nouris Elbahr, Madar Maritime
Company, Corsica Linea, Baleària, Armas Trasmediterránea and GNV
([algerie360](https://www.algerie360.com/voyager-en-algerie-en-ete-2026-attention-ces-vehicules-seront-interdits-sur-les-ferries/),
[ulysse](https://ulysse.com/news/ferry-algerie-restrictions-vehicules-ete-2026)).
Madar Maritime Company (brand *Andalouza*, ship *Romantika*) suspended its
Algeria–Alicante services in February 2026
([algerie-eco](https://algerie-eco.com/2026/02/08/madar-maritime-company-arrete-les-dessertes-entre-lalgerie-et-alicante/)),
so WAVE lists it as *inactive*.

Summer 2026 sales (15 June to 15 September) were opened by all active
operators ([maghrebemergent](https://www.maghrebemergent.com/embarquez-pour-lete-2026-algerie-ferries-lance-ses-reservations-avec-enthousiasme/),
[directferries](https://www.directferries.fr/offres/algerie-ouverture-des-ventes-pour-l-ete-2026)).
Demand is dominated by the Algerian diaspora and is strongly seasonal and
**directional**: Europe → Algeria peaks from late June to July, Algeria → Europe
peaks from late August to mid September.

### 1.1 Operators and lines (2026)

| Operator | Code | Lines touching Algeria | Ships | Notes |
|---|---|---|---|---|
| **Algérie Ferries** (ENTMV, state) | `AF` | Marseille ↔ Alger (up to 2/week each way in summer), Marseille ↔ Oran (2–4/month), Marseille ↔ Skikda (3/month), Marseille ↔ Béjaïa (4/month, 2 in Sept), Marseille ↔ Annaba (1/month Jul–Sep); Alicante ↔ Alger (up to 2/week), Alicante ↔ Oran (1/week); summer extras from Sète, Barcelona, Valencia to Alger, Oran, Béjaïa, Ghazaouet, Mostaganem, Skikda | Badji Mokhtar III, Tariq Ibn Ziyad, El Djazair II, Tassili II | Single price for all Algerians since 2026 (no more diaspora surcharge). Sells in DZD in Algeria and EUR in Europe. |
| **Corsica Linea** | `CL` | Marseille ↔ Alger (3/week summer), Marseille ↔ Béjaïa (2/week), Marseille ↔ Skikda (1/week, Wed 10:00, ~24–26 h) | Méditerranée (Marseille–Alger 16 Jun–13 Sep 2026), Danielle Casanova (summer reinforcement) | Fares: Standard, Flex, Super Flex, Famille, Senior. |
| **GNV** (Grandi Navi Veloci, MSC group) | `GNV` | Sète ↔ Alger (weekly), Sète ↔ Béjaïa (weekly), Civitavecchia ↔ Annaba (2/week, Tue & Sat, ~23 h 30, since 8 Aug 2026) | Fantastic, Excellent, Allegra, Altair, Cristal | Halal meals and prayer room on Algeria lines. |
| **Baleària** | `BAL` | Barcelona ↔ Alger (weekly; Mon 13:00 ex-Barcelona, Sat 14:00 ex-Alger), Valencia ↔ Alger (Fri 19:00, ~14 h; return Tue/Fri 18:30, ~15 h 30), Valencia ↔ Oran (Wed 18:30; return Thu 15:00), Valencia ↔ Mostaganem (since 2016, 14–17 h) | Regina Baltica, Visborg, Martín i Soler, Bahama Mama | Took over Armas Trasmediterránea (CNMC approval, March 2026). |
| **Nouris Elbahr Ferries** (private, Algerian) | `NE` | Marseille ↔ Alger (weekly, Fri 19:00 ex-Marseille, ~22–23 h), Béjaïa ↔ Marseille, Alger ↔ Alicante, Oran ↔ Alicante | Cracovia (chartered from Polferries; 650 pax, 16.7 kn) | First private Algerian operator (2024). Bookings open until 31 Jan 2027. 20 % online discount from 1 Sept 2026. |
| **Armas Trasmediterránea** | `ATM` | Almería ↔ Ghazaouet (Fri 22:00 → Sat 08:30; return Sat 19:00 → Sun 08:00), Almería ↔ Oran | Almariya, Volcán de Timanfaya (1,000 pax, 300 vehicles) | Now part of the Baleària group. From 63 € when booked early. |
| Madar Maritime / Andalouza | `MMC` | (suspended Feb 2026) Alicante/Almería ↔ Oran/Mostaganem/Alger/Béjaïa | Romantika | Inactive. |

Sources: [Algérie Ferries summer programme](https://maghrebemergent.news/fr/reservations-ports-ce-que-prepare-algerie-ferries-pour-lete-2026/),
[Corsica Linea 2026](https://ulysse.com/news/corsica-linea-mediterranee-alger-ete-2026),
[Corsica Linea season page](https://www.corsicalinea.com/algerie/offres/saison-ete-algerie/),
[GNV Sète](https://voyagefrancealgerie.com/gnv-quels-prix-pour-les-traversees-sete-algerie-cet-ete-2026/),
[GNV Civitavecchia–Annaba](https://www.algerie360.com/voyage-italie-algerie-gnv-lance-sa-nouvelle-ligne-directe-vers-annaba/),
[Baleària Algeria routes](https://www.balearia.com/es/rutas-horarios/regiones-argelia),
[Baleària Valencia–Oran](https://www.balearia.com/es/sala-prensa/notas-prensa/tres-salidas-semanales-argelia-valencia-oran),
[Baleària Barcelona–Alger](https://www.balearia.com/es/sala-prensa/notas-prensa/balearia-abre-una-nueva-ruta-entre-barcelona-y-argel),
[Nouris Elbahr](https://www.lejournaldesentreprises.com/breve/nouris-elbahr-ferries-une-nouvelle-compagnie-algerienne-privee-dessert-marseille-2107386),
[Nouris Elbahr 2026 fares](https://www.visa-algerie.com/ete-2026-une-concurrente-dalgerie-ferries-annonce-marseille-alger-a-partir-de-280e/),
[Armas Almería–Ghazaouet](https://armastrasmediterranea.com/en/routes-timetables/ferry-almeria-ghazaouet),
[Check-in & weekly departures Marseille–Alger](https://ferryguide.fr/routes/ferry-marseille-alger/).

### 1.2 Ports

| Port | UN/LOCODE | Country | Time zone | Notes |
|---|---|---|---|---|
| Alger (Algiers) | `DZALG` | DZ | Africa/Algiers (UTC+1, no DST) | Main passenger port. |
| Oran | `DZORN` | DZ | Africa/Algiers | Summer restrictions on some vehicles. |
| Béjaïa | `DZBJA` | DZ | Africa/Algiers | |
| Skikda | `DZSKI` | DZ | Africa/Algiers | |
| Annaba | `DZAAE` | DZ | Africa/Algiers | Italy link since Aug 2026. |
| Mostaganem | `DZMOS` | DZ | Africa/Algiers | |
| Ghazaouet | `DZGHZ` | DZ | Africa/Algiers | Closest port to Tlemcen. |
| Marseille (Cap Janet) | `FRMRS` | FR | Europe/Paris | |
| Sète | `FRSET` | FR | Europe/Paris | |
| Barcelona | `ESBCN` | ES | Europe/Madrid | |
| Valencia | `ESVLC` | ES | Europe/Madrid | |
| Alicante | `ESALC` | ES | Europe/Madrid | |
| Almería | `ESLEI` | ES | Europe/Madrid | |
| Civitavecchia | `ITCVV` | IT | Europe/Rome | |

**Time zones matter.** Algeria stays on UTC+1 all year; France, Spain and Italy
move to UTC+2 in summer. A Marseille departure at 15:00 (CEST) with a 20 h
crossing arrives at 10:00 Algiers time, not 11:00. The backend stores every
departure/arrival as a zoned timestamp in the port's own zone.

### 1.3 Fleet (AIS identifiers used by the live map)

| Ship | Operator | IMO | MMSI | Capacity | Speed |
|---|---|---|---|---|---|
| Badji Mokhtar III | AF | 9827889 | 605016420 | 1,800 pax / 600 vehicles, 200 m | 24 kn |
| Tariq Ibn Ziyad | AF | 9109768 | 605246160 | 1,276 pax / 500 vehicles, 153 m | 21 kn |
| El Djazair II | AF | 9265421 | 605026190 | 1,320 pax, 146 m | 23.5 kn |
| Tassili II | AF | 9265419 | 605046150 | 1,320 pax / 300 vehicles, 146 m | 23.5 kn |
| Danielle Casanova | CL | 9230476 | 226242000 | ~2,200 pax | 23 kn |
| Regina Baltica | BAL | 7827225 | 210976000 | 1,675 pax / 350 cars, 145 m | 18 kn |
| Martín i Soler | BAL | 9390367 | 224637000 | 165 m, dual-fuel LNG | 21 kn |
| Fantastic | GNV | 9100267 | 247094000 | 188 m | 22 kn |
| Cracovia | NE | 9237242 | 311000671 | 650 pax, 180 m | 16.7 kn |

Sources: [Tassili II](https://www.vesseltracker.com/en/Ships/Tassili-Ii-9265419.html),
[El Djazair II](https://www.vesselfinder.com/vessels/EL-DJAZAIR-II-IMO-9265421-MMSI-605026190),
[Badji Mokhtar III](https://www.myshiptracking.com/vessels/badji-mokhtar-iii-mmsi-605016420-imo-9827889),
[Tariq Ibn Ziyad](https://www.myshiptracking.com/vessels/tariq-ibn-ziyad-mmsi-605246160-imo-9109768),
[Danielle Casanova](https://www.vesseltracker.com/en/Ships/Danielle-Casanova-9230476.html),
[Regina Baltica](https://www.vesseltracker.com/en/Ships/Regina-Baltica-7827225.html),
[Martín i Soler](https://www.vesselfinder.com/vessels/details/9390367),
[Fantastic](https://www.vesseltracker.com/en/Ships/Fantastic-9100267.html),
[Cracovia](https://www.vesselfinder.com/vessels/details/9237242),
[Algérie Ferries fleet](https://algerieferries.com/nos-ferries).
Ships whose MMSI could not be verified (Méditerranée, Visborg, Bahama Mama,
Excellent, Allegra, Altair, Cristal, Almariya) are in the catalog with
`mmsi = null`; the live map then shows a schedule-based estimated position.

---

## 2. What a ferry booking contains

A ferry booking (PNR) is richer than an airline ticket because it covers people,
vehicles, animals and on-board accommodation:

1. **Itinerary**: one or two legs (one-way / round trip), each a specific
   *sailing* (operator, ship, departure port & time, arrival port & time).
2. **Passengers**: title, first & last name *as in the travel document*, date of
   birth (drives the fare category), nationality, sex (needed for shared
   cabins and the ship's manifest), travel-document type, number, expiry date.
   Ships must transmit a passenger manifest to port authorities, so these are
   mandatory before boarding.
3. **Accommodation per leg**: seat (fauteuil/pullman), or cabin (interior or
   sea-view, 2 or 4 berths, suite, pet-friendly, accessible). Cabins are sold
   whole (private) for families; infants do not use a berth.
4. **Vehicle per leg**: type (car, high car/SUV, motorcycle, camper, car with
   trailer/caravan, van), registration plate, make/model, length and height
   (*including roof box or bike rack*). The driver is a passenger too.
5. **Animals**: species, number, and where it travels (kennel, pet cabin, or on
   deck on a leash), plus the health documents.
6. **Special needs**: reduced mobility (PRM) assistance, wheelchair, accessible
   cabin. EU Regulation 1177/2010 requires PRM needs to be notified at least
   48 h before departure.
7. **Fare & conditions**: tariff (promo/standard/flex), the fare breakdown
   (passage, accommodation, vehicle, pets, taxes, fees, discounts), and the
   cancellation/modification policy attached to the tariff.
8. **Contact & payment**: e-mail, phone (for operational notices), payment
   record (method, amount, currency, exchange rate used).
9. **Ticket**: booking reference and a boarding document (e-ticket with QR
   code) shown at check-in.

---

## 3. Rules and conditions

### 3.1 Passenger categories (age bands differ per operator)

Because the bands differ, **WAVE asks for each child's age** and lets each
operator's rules classify them. The search form collects adults (18+), seniors
(60+) and the age of every minor.

| Operator | Infant (free) | Child | Youth / Senior | Source |
|---|---|---|---|---|
| Algérie Ferries | < 3 years, free | 3–12 years, −60 % (2026 tariff) | Youth 12–25 fares exist; PRM −60 % | [visa-algerie](https://www.visa-algerie.com/algerie-ferries-ouvre-les-ventes-ete-2026-voici-les-prix-des-billets/), [alloferry](https://www.alloferry.com/billet-bateau-alger-marseille-tarif-reduit,8088.htm) |
| Corsica Linea | < 3 years | 3–12 (Famille tariff) | Senior 60+, −25 % | [Tarif Famille](https://www.corsicalinea.com/reserver/offres-et-promotions/algerie/tarif-famille), [algerieferry.info](https://www.algerieferry.info/corsicalinea/tarifs/) |
| Baleària | < 1 year, free (no seat) | 1–13 years, −50 % | — | [Baleària family](https://www.balearia.com/es/viajar-con-balearia/viajes-para-ti/viaja-en-familia) |
| GNV | < 4 years free on most lines (< 2 on Maghreb lines) | 4–11 | — | [GNV conditions](https://info.gnv.it/fr/condizioni-generali.html) |

### 3.2 Minors

* GNV: under 14 cannot travel alone; 14–17 may travel alone with written
  authorisation from the holder of parental authority
  ([GNV CGT](https://info.gnv.it/fr/condizioni-generali.html)).
* France: a minor leaving France without either parent needs an *AST*
  (autorisation de sortie du territoire) plus a copy of the signing parent's ID
  ([service-public.fr](https://www.service-public.gouv.fr/particuliers/vosdroits/F36350/1_1_2)).
* Algeria: a minor leaving Algeria without the father (or the parent holding
  parental authority) may be asked for a *paternal travel authorisation*
  certified at a police station, town hall or consulate
  ([consulat](https://consulat-nantes-algerie.fr/legalisation-documents-divers/autorisation-voyage-paternelle/),
  [bledz](https://bledz.fr/guides/autorisation-sortie-territoire-mineur-algerie/)).
* **WAVE rule:** every booking needs at least one passenger aged 18+; infants
  cannot outnumber adults; minors without an adult are refused; the app adds
  the AST / paternal-authorisation reminder when the group contains minors.

### 3.3 Travel documents

* Passport valid for the whole trip (at least 6 months after return is the
  safe rule; ID cards are not accepted on Algeria lines).
* Schengen visa or EU residence permit for Algerian nationals travelling to
  France, Spain or Italy; Algerian visa for foreign nationals.
  ([ulysse customs guide](https://ulysse.com/news/douane-algerie-2026-guide-diaspora-devises-vehicules-bagages),
  [bladia](https://www.bladia.com/fr/blog/documents-ferry-maroc-algerie-tunisie))

### 3.4 Vehicles

* Documents: registration certificate (carte grise) in the driver's name or a
  power of attorney, driving licence, international insurance (green card)
  valid in Algeria, and for vehicles registered abroad the **TPD** (titre de
  passage en douane, temporary admission). Algerians residing abroad get a TPD
  valid 6 months per 12-month period; it can be requested online on ALCES
  (alces.douane.gov.dz) or completed on board between Marseille and Algeria
  ([eplaque](https://www.eplaque.fr/infos/titre-passage-douane-en-ligne-algerie),
  [douane.gov.dz](https://douane.gov.dz/IMG/pdf/circulaire_157-2.pdf)).
* Size classes drive the price: standard car (≤ 5 m, ≤ 1.85–1.90 m high incl.
  roof box), high car/SUV/minivan, motorcycle, camper (Baleària accepts up to
  10 m long, 4 m high), car + trailer/caravan. GNV's family offer requires
  ≤ 4.99 m and ≤ 1.90 m incl. roof rack
  ([observalgerie](https://observalgerie.com/2026/02/05/voyage/ferry-france-algerie-des-traversees-gnv-avec-vehicule-a-partir-de-280-e/),
  [Baleària pets & vehicles](https://www.balearia.com/es/viajar-con-balearia/viaja-con-mascotas)).
* **Summer 2026 restrictions (15 June – 15 September), all seven operators:**
  1. Vans / utility vehicles may not board passenger ships at any Algerian port.
  2. At Alger and Oran only, new or < 3-year-old vehicles bought abroad by
     Algerian citizens (import) are refused.
  ([algerie-eco](https://algerie-eco.com/2026/05/30/transport-maritime-reconduction-des-restrictions-sur-certains-vehicules-pour-lete-2026/),
  [observalgerie](https://observalgerie.com/2026/06/04/voyage/ferries-vers-lalgerie-ces-vehicules-et-remorques-interdits-a-lembarquement/)).
  These measures are renewed each summer, so WAVE stores them as
  **date-ranged regulatory rules** (`catalog/regulations.json`), not code.

### 3.5 Animals

* Microchip (or tattoo done before 3 July 2011), rabies vaccination done
  between 21 days and 12 months before departure, health certificate from a
  vet, validated by an official vet of the departure country within 48 h
  before boarding.
* **Return to the EU from Algeria needs a rabies antibody titration** done in
  an EU-approved lab at least 3 months before entry (the 3-month wait does not
  apply if the titration was done before leaving the EU)
  ([Algérie Ferries animals](https://algerieferries.com/algerie-ferries/guide-de-passager/animaux),
  [anivetvoyage](https://anivetvoyage.com/pays/algerie/),
  [Corsica Linea pets](https://www.corsicalinea.com/blog-algerie/voyager-avec-votre-animal-sur-les-lignes-france-algerie/)).
* On board: kennels, pet-friendly cabins (GNV, Baleària), or leash + muzzle on
  outside decks. Pets are not allowed in seat lounges or ordinary cabins.

### 3.6 Check-in

Marseille–Alger reference times
([alloferry / ferryguide](https://ferryguide.fr/routes/ferry-marseille-alger/),
[voyagefrancealgerie](https://voyagefrancealgerie.com/algerie-ferries-les-horaires-denregistrement-dune-traversee-marseille-alger/)):

| | Opens | Closes | Recommended arrival |
|---|---|---|---|
| Foot passenger | 4 h before | 1 h before | 2 h before |
| With vehicle | 7 h before | 1 h 30 before | 3 h before |

Late passengers may be refused boarding.

### 3.7 Baggage

Algérie Ferries: 60 kg per adult in cabin class, 30 kg in economy (seat), on
Algeria–France and Algeria–Spain lines
([Algérie Ferries CGV](https://algerieferries.com/guide-de-passager/conditions-generales)).

### 3.8 Tariffs, cancellation and modification

| Operator | Tariff | Cancellation rule |
|---|---|---|
| Algérie Ferries | Standard (code F0) | 20 % until 30 days before departure, 30 % from 29 to 10 days, 50 % from 9 days to 48 h, 100 % under 48 h. Promo and Open tariffs are non-refundable. Tickets bought on board cost 10 % more. |
| Corsica Linea | Flex / Super Flex | Flex: free refund until ~30 days, then progressive penalties. Super Flex: free refund until 7 days before; reduced modification fees. Family tariff: max stay 180 days. |
| Baleària | Reduced fare | 100 % refund within 24 h of purchase (if > 2 h before departure); then 10 % fee until 48 h before departure; 20 % fee between 48 h and 24 h. |
| GNV / Nouris Elbahr / Armas | Promo vs flexible | Promo fares are generally non-refundable; flexible fares refundable with a fee. |

Sources: [Algérie Ferries CGV](https://algerieferries.com/guide-de-passager/conditions-generales),
[Corsica Flex](https://www.corsicalinea.com/reserver/offres-et-promotions/corse/tarifs-flex-et-super-flex),
[Baleària fares](https://www.balearia.com/es/condiciones-flexibles).

### 3.9 Passenger rights (EU Regulation 1177/2010)

Applies to departures from EU ports: information within 30 min of scheduled
departure on delay/cancellation; refreshments when departure is delayed
> 90 min; choice between refund and re-routing; compensation of 25 % or 50 % of
the ticket price depending on the arrival delay, except for weather or
extraordinary circumstances
([EUR-Lex summary](https://eur-lex.europa.eu/FR/legal-content/summary/rights-of-passengers-travelling-by-sea-and-inland-waterways.html)).

---

## 4. Prices (public "from" fares, 2026)

| Line | Operator | Public fare | Source |
|---|---|---|---|
| Alger → Marseille | Algérie Ferries | 21,620 DZD foot, 53,120 DZD with vehicle (promo: 23,020 / 57,020 DZD) | [observalgerie](https://observalgerie.com/2026/05/19/voyage/promo-algerie-ferries-billets-algerie-france-des-23-000-dinars) |
| Marseille ↔ Béjaïa | Algérie Ferries | 404 € return, seat, 1 pax | [visa-algerie](https://www.visa-algerie.com/algerie-ferries-ouvre-les-ventes-ete-2026-voici-les-prix-des-billets/) |
| Marseille → Alger | Corsica Linea | seat 146 €; with car 400 €; interior cabin (2 pax) 650 €; outside cabin (2 pax) 750 € | [ulysse](https://ulysse.com/news/corsica-linea-mediterranee-alger-ete-2026) |
| Sète → Béjaïa / Alger | GNV | 91 € / 143 € (return legs 119 € / 146 €); with vehicle from 280 € | [visa-algerie](https://voyagefrancealgerie.com/gnv-quels-prix-pour-les-traversees-sete-algerie-cet-ete-2026/) |
| Valencia → Mostaganem | Baleària | 130 € foot, 295 € with car | [Baleària](https://www.balearia.com/fr/routes-horaires/bateau-valence-mostaganem) |
| Barcelona → Alger | Baleària | from 185 € | [Baleària](https://www.balearia.com/en/routes-timetables/ferry-barcelona-algiers) |
| Marseille → Alger | Nouris Elbahr | from 280.90 € (avg 398 €) | [voyagerdz](https://voyagerdz.com/nouris-elbahr-des-traversees-vers-lalgerie-des-168-e-cet-ete-2026/) |
| Alicante → Alger / Oran | Nouris Elbahr | from 168.23 € | idem |
| Alger → Alicante / Marseille | Nouris Elbahr | from 25,235 DZD / 42,135 DZD | idem |
| Almería → Ghazaouet | Armas Trasmediterránea | from 63 € | [omio](https://www.omio.fr/ferries/almeria/ghazaouet-qcxlw) |

These are calibration points for the reference fare tables in
`backend/src/main/resources/catalog/fares.json`.

---

## 5. Money and payment in Algeria

* **Official rates** (Banque d'Algérie, 1–2 Oct 2026): 1 EUR = 151.06 DZD,
  1 USD = 132.73 DZD
  ([voyagefrancealgerie](https://voyagefrancealgerie.com/euro-dinar-leuro-recule-encore-au-debut-du-mois-doctobre/),
  [dinarsquare](https://dinarsquare.com/taux-dinar-devise)).
  The parallel market rate is much higher (~275 DZD/EUR) but has no legal
  standing for ticket sales; WAVE only uses official rates.
* Operators price in their own currency: Algérie Ferries and Nouris Elbahr sell
  in DZD from Algeria and EUR from Europe; Corsica Linea, GNV, Baleària and Armas
  price in EUR. WAVE stores each fare in its native currency and converts for
  display with the rate snapshot attached to the quote.
* Local payment: CIB (interbank) and EDAHABIA (Algérie Poste) cards through the
  **SATIM** e-payment gateway (`register.do` then `confirmOrder.do`/status,
  amounts in DZD, minimum 50 DZD), or through aggregators such as Chargily Pay.
  International cards for EUR/USD. Tickets can also be paid at the agency.
  ([satim SDK](https://github.com/khalilbnd/satim-node),
  [Chargily](https://dev.chargily.com/pay-v2/introduction)).

---

## 6. Live vessel tracking

* **aisstream.io**: free global AIS feed over WebSocket
  (`wss://stream.aisstream.io/v0/stream`), subscription message with `APIKey`,
  `BoundingBoxes`, optional `FiltersShipMMSI` and `FilterMessageTypes`
  (`PositionReport`, `ShipStaticData`)
  ([docs](https://aisstream.io/documentation.html)). Used by the backend
  `AisStreamClient`.
* **AIS-catcher + pyais**: for a self-hosted receiver (RTL-SDR at a port),
  AIS-catcher decodes the radio signal and pyais decodes NMEA sentences; the
  scraper package contains a `pyais`-based forwarder.
* **flutter_map** (fleaflet) with OpenStreetMap + OpenSeaMap seamark tiles in
  the app.
* When no AIS fix is available the backend estimates the position from the
  schedule (great-circle interpolation along the route's sea waypoints) and
  flags it `estimated: true`.
