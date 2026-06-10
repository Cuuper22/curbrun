# CurbRun — Data & Algorithm Research (free/open-data only)

Deep-research synthesis for "the most reliable free/open way to source data and design the
algorithm" for CurbRun (legal free curb parking in SF for an N-hour window). Scope was fixed
to **free/open data only** (no INRIX/SpotHero/ParkMobile/Google paid tiers) with an
**end-to-end** algorithm focus (availability prediction + legality + ranking/routing).

All dataset IDs and the two highest-value new sources were re-verified against the live
DataSF Socrata API on 2026-06-06. Confidence is noted per item; citations are at the end.

---

## Bottom line (TL;DR)

1. **Regulations are well covered by free data; occupancy is not.** SF publishes authoritative,
   queryable curb-regulation and schedule data for free. But there is **no free real-time
   on-street occupancy feed** anywhere today — the SFpark sensors were removed after 2013.
2. **The single highest-value upgrade is real temporary restrictions.** CurbRun currently only
   models recurring schedules. SF publishes **daily, structured, geocoded temporary tow-zones**
   (`6r5h-j298`) and construction street-space permits (`sftu-nd43`) — real "no parking starting
   tomorrow" data the app is missing.
3. **For free residential curbs, honest uncertainty is the correct design.** No occupancy data
   exists for unmetered blocks at any price. The best achievable is a coarse time-of-day ×
   day-of-week × land-use *prior* presented as bands (low/med/high), never a precise count. This
   validates CurbRun's current "modeled heuristic, not measured" labeling.
4. **Maps/routing: fine for a demo, must self-host at scale.** OSM tiles + Nominatim public
   endpoints prohibit production traffic; OSRM/Valhalla (BSD/MIT) are embeddable for real ETAs.

---

## Part 1 — Free / open DATA stack

### 1.1 Curb regulations (the legality backbone)

| Source | What it gives | Access | Freshness / reliability |
|---|---|---|---|
| **SFMTA ArcGIS REST** `Parking/digitalcurb/FeatureServer/0` (`RPT_CURB_ZONES_POLICIES`) and `parkingregulations_timelimited/MapServer/0` | Live curb-zone policies + blockface regulations (Time limited, RPP, No overnight, Gov't permit, No parking, Oversized) with `HRLIMIT/DAYS/HOURS/RPPAREA1-3` | ArcGIS REST query (Esri JSON), 2k/10k page caps | **Agency source-of-truth.** ~100 regulation changes/month. **High.** |
| DataSF `hi6h-neyh` / map `qbyz-te2i` ("Parking regulations except non-metered color curb") | Socrata mirror of the above | SODA/SoQL (easier) | Lags the live ArcGIS layer; "weekly" publish. Same exclusions. **Med-high.** |
| OpenStreetMap `parking:both/left/right:*` (2022 schema; old `parking:lane`/`parking:condition` deprecated) | Crowd parking orientation/restriction | OSM/Overpass (ODbL) | **Sparse & uneven for SF** — supplement only, not a primary source. |
| OMF **Curb Data Specification (CDS) 1.1** & **CurbLR** | *Specifications*, not data — how to model rules/time-spans | GitHub | Adopt CDS as the **internal data model**; SFMTA's Digital Curb targets the CDS Curbs API. |

**Gap (important):** the main regulation feed **excludes non-metered color curb** (loading/green/
yellow/white) and curb cuts. Blue (accessible) zones are a separate dataset (`7r4t-4yft`,
`g69s-9jxr`). The DataSF dataset literally named "Color curb" (`v3se-eucw`) is a **311
service-request table, not curb geometry** — do not use it as a regulation source.

### 1.2 Street sweeping (recurring no-parking) — **verified**

- **`yhqp-riqs`** "Street Sweeping Schedule" (SF Public Works) — **verified 37,878 rows**, fields
  `cnn, corridor, blockside, weekday, fromhour, tohour, week1..week5 (boolean), holidays, line`.
  Week-of-month is five boolean flags, not one field. Cadence "as needed" → treat as
  slow-changing reference. CurbRun already models this as recurring windows. **High.**

### 1.3 Temporary restrictions — **the biggest missing piece, verified live**

- **`6r5h-j298`** "SFMTA – Enforced Temporary Tow Zones" — **verified 170,686 rows, daily refresh,
  443 currently active/upcoming** (`enddate >= today`). 34 structured columns incl.
  `startdate, enddate, starttime, endtime, address, cnn, latitude, longitude, source, permitnumber`.
  Aggregates Public Works + Film Commission + SFMTA + special events.
  **Caveat (verified):** many rows have **null `startdate/enddate/lat/long`** and `status` is blank
  ~90% — filter to rows with a valid future `enddate` and geocode by `cnn`/address when lat/long
  is null. **High value, medium data hygiene.**
- **`sftu-nd43`** "Parking Signs / Street Space Permits" (construction/moving) — daily, ~present-
  state snapshot (~335 rows); photos in `pigs-fac7`.
- **Do NOT use the SF311 Open311 API for temporary no-parking** — its `services.json` exposes no
  TNP service type (verified negative). Use the tow-zones dataset instead.

### 1.4 Capacity & meters

- **`9ivs-nf5y`** On-Street Parking Census — surveyed space counts per blockface (**2008–2014
  survey**, 17 ft/space; filter the `5555` artifact). **Already integrated into CurbRun** as
  `measured_spaces`. Real but vintage.
- **`8vzz-qzz9`** Parking Meters — ~38,600 meter locations, **location only**. Rates live in
  **`fwjv-32uk`** which is **frozen at 2014-03-07 — do not use for current pricing**; SF moved to
  demand-responsive pricing since.

---

## Part 2 — OCCUPANCY: the honest reality

- **No free (or paid) real-time on-street occupancy feed exists for SF today.** The original
  SFpark availability API is decommissioned (404); the in-ground sensors were removed after the
  2011–2013 pilot.
- **Free historical training data does exist:** SFMTA's "SFpark Parking Sensor Data Hourly
  Occupancy 2011–2013" CSV (~1.38 GB, free, no login) + roadway/garage companions; plus a
  Harvard Dataverse research mirror (~5-min resolution, CC BY-NC-ND).
- **Free ongoing proxy WHERE METERS EXIST:** `imvp-dq3v` "Parking Meter Detailed Revenue
  Transactions" — **verified** to carry `session_start_dt`/`session_end_dt` per transaction
  (~176 M rows). SFMTA's own SIRA model recovers occupancy at **~70% accuracy** from payments;
  academic queueing methods reach **~1–2.5 cars/block-face RMSE**, biased low by **~30% non-payment**.
- **For FREE residential curbs: nothing exists, free or paid.** No sensors, no transactions.
  Transfer/similarity methods (GIS land-use similarity → apply models from instrumented areas)
  only yield **coarse occupancy bands (~±15–25 percentage points)**, not block-level counts.

**Design implication:** present availability for free curbs as **bands with explicit uncertainty**,
exactly as CurbRun now labels it — and only claim precision for metered corridors where
transaction data backs it.

---

## Part 3 — End-to-end ALGORITHM design

### 3.1 Legality engine (deterministic — this is CurbRun's strength)

- Model rules on the **CDS time-span** shape: `days_of_week` as 3-letter lowercase, time-of-day
  as **`HH:MM` with an exclusive end**, optional `days_of_month/months/start_date/end_date`.
- **Overnight/wrap-around windows must be split at midnight** into two same-day checks (because
  the end is exclusive and bounded to one day). **CurbRun already does this** — validated against
  the spec.
- Prove the whole `[now, now+N]` interval legal by testing it against every applicable rule
  (CurbRun's approach is correct). Add:
  - a **temporary-restriction overlay** from `6r5h-j298`/`sftu-nd43` as time-bounded
    `NoParking`/`TowAway` rules (start/end datetime), and
  - a **holiday calendar** (CDS treats holidays as free-text `designated_period`, so the city's
    calendar must be supplied externally — street-sweeping `holidays` flag + a US/SF holiday list).

### 3.2 Availability prediction (be honest, tier by data)

- **Metered corridors:** build a **per-block time-of-day × day-of-week occupancy prior** from the
  SFpark historical CSV + `imvp-dq3v` transactions. Simple models win here: a **Decision
  Tree/Gradient-Boosting** classifier reached ~90% short-horizon accuracy in comparable studies
  (deep nets did *not* dominate); a queueing estimator gives ~1–2.5 cars/block RMSE.
- **Free residential curbs (CurbRun's hard case):** only a **coarse prior** from time-of-day,
  day-of-week, and land-use/POI similarity (transfer). Present as **low/med/high bands**, never a
  precise count. This is a principled upgrade from the current rule-density heuristic *for the
  metered subset*, while keeping honest bands elsewhere.
- Standard feature set: time-of-day, day-of-week, weather, nearby events, land use/POI, and
  (where available) neighboring-block occupancy. Note weather/event correlations **transfer poorly
  across cities** — calibrate on SF only.

### 3.3 Ranking & routing

- **Ranking:** closest-first with an availability-prior penalty (CurbRun's current design is
  sound). Tier confidence by data source (metered vs free-curb band).
- **Multi-stop sweep:** for <10 candidates, exact TSP is cheap — OSRM's **Trip** service brute-
  forces <10 waypoints (greedy farthest-insertion at ≥10). CurbRun's greedy nearest-neighbor is a
  fine approximation; the budget-constrained variant ("maximize good spots within a drive-time
  budget") is the **Orienteering Problem** (greedy by prize/incremental-distance ratio).
- **Maps/geocoding/routing licensing:** OSM tiles + Nominatim public endpoints **prohibit
  production traffic** (Nominatim ≤1 req/s; tiles require ≥7-day cache and self-hosting for heavy
  use). For real traffic, **self-host OSRM (BSD) or Valhalla (MIT)** + tiles + Photon (Apache) —
  all license-compatible (permissive code over ODbL data) with a closed app. ORS free tier
  (~10k directions/day) is fine for low volume but quotas drift.

---

## Part 4 — Recommended roadmap for CurbRun (prioritized)

1. **Ingest real temporary restrictions** (`6r5h-j298` + `sftu-nd43`) into the pipeline as
   time-bounded `TowAway`/`NoParking` overlays. *Highest value, low effort* — closes the app's
   biggest real-world correctness gap. Handle the null-field hygiene noted above.
2. **Close the color-curb gap:** add blue-zone (`7r4t-4yft`/`g69s-9jxr`) and, where available,
   loading/color-curb data; move the regulation source toward the live SFMTA `digitalcurb`
   FeatureServer (CDS-aligned).
3. **Replace the availability heuristic with a historical prior** where data exists: precompute a
   time-of-day × day-of-week occupancy prior from the SFpark CSV + `imvp-dq3v` for metered blocks;
   keep coarse honest bands for free curbs. Bake into the existing `base_availability`/curve
   columns — no app schema change.
4. **Keep & extend the legality engine:** add the holiday calendar; keep the midnight-split
   overnight handling (already correct).
5. **Routing/maps:** keep greedy ranking; if you add live ETAs/turn-by-turn at scale, self-host
   OSRM/Valhalla + tiles rather than hitting public OSM endpoints.

**Net:** CurbRun's architecture is already aligned with the evidence (CDS-style legality,
honest heuristic labeling, census capacity). The biggest reliable wins from *free* data are
**(a) temporary tow-zones** and **(b) a real historical occupancy prior for metered blocks** —
both ingestible into the current SQLite pipeline.

---

## Citations

**SF regulation / schedule data**
- SFMTA ArcGIS REST (Parking): https://services.sfmta.com/arcgis/rest/services/Parking
- Parking regulations (DataSF `hi6h-neyh` / map `qbyz-te2i`): https://data.sfgov.org/Transportation/Parking-regulations-except-non-metered-color-curb-/hi6h-neyh
- Street Sweeping Schedule (`yhqp-riqs`): https://data.sfgov.org/City-Infrastructure/Street-Sweeping-Schedule/yhqp-riqs
- On-Street Parking Census (`9ivs-nf5y`): https://data.sfgov.org/Transportation/On-Street-Parking-Census/9ivs-nf5y
- Parking Meters (`8vzz-qzz9`) / Meter Rate Schedules frozen 2014 (`fwjv-32uk`): https://data.sfgov.org/Transportation/Parking-Meters/8vzz-qzz9 · https://data.sfgov.org/Transportation/Meter-Rate-Schedules/fwjv-32uk
- Blue zones (`7r4t-4yft`, `g69s-9jxr`): https://data.sfgov.org/Transportation/Map-of-Accessible-Curb-Blue-Zones-/7r4t-4yft

**Temporary restrictions**
- SFMTA Enforced Temporary Tow Zones (`6r5h-j298`): https://data.sfgov.org/Transportation/SFMTA-Enforced-Temporary-Tow-Zones/6r5h-j298
- Parking Signs / Street Space Permits (`sftu-nd43`) + photos (`pigs-fac7`): https://data.sfgov.org/City-Infrastructure/Parking-Signs-Street-Space-Permits/sftu-nd43
- SF311 Open311 services (no TNP type): https://mobile311.sfgov.org/open311/v2/services.json

**Occupancy data & proxies**
- SFpark Evaluation + historical occupancy CSV: https://www.sfmta.com/getting-around/drive-park/demand-responsive-pricing/sfpark-evaluation
- Harvard Dataverse SFpark mirror: https://dataverse.harvard.edu/dataset.xhtml?persistentId=doi:10.7910/DVN/YLWCSU
- Meter transactions (`imvp-dq3v`): https://data.sfgov.org/Transportation/SFMTA-Parking-Meter-Detailed-Revenue-Transactions/imvp-dq3v
- US DOT ITS — SF meter data ~70% occupancy: https://www.itskrs.its.dot.gov/2016-b01101

**Prediction literature**
- Yang & Qian 2017, Transp. Res. Part C, doi:10.1016/j.trc.2017.02.022
- Jordon et al., arXiv:2106.02270 (smart-meter occupancy; ~30% non-payment)
- Awan et al. 2020, *Sensors* 20(1):322, doi:10.3390/s20010322 (Decision Tree ~92%)
- Ionita et al., IJAIT — transfer to unmonitored areas (coarse bands)
- Shao et al., FADACS, arXiv:2007.08551 (few-shot domain adaptation)
- Millard-Ball, Weinberger & Hampshire 2014, Transp. Res. Part A (SFpark critique)

**Specs, routing, maps**
- Curb Data Specification (rules/time-spans): https://github.com/openmobilityfoundation/curb-data-specification/blob/main/curbs/README.md
- CurbLR: https://github.com/curblr/curblr-spec
- OSM tile policy: https://operations.osmfoundation.org/policies/tiles/ · Nominatim: https://operations.osmfoundation.org/policies/nominatim/ · ODbL: https://www.openstreetmap.org/copyright
- OSRM Trip/TSP: https://github.com/Project-OSRM/osrm-backend/blob/master/docs/http.md · Valhalla (MIT): https://github.com/valhalla/valhalla · Photon (Apache): https://github.com/komoot/photon · ORS limits: https://openrouteservice.org/restrictions/
- Orienteering Problem: https://www.cs.cmu.edu/~avrim/Papers/orienteering-sicomp.pdf
- SpotAngels (crowdsourced sign photos): https://www.spotangels.com/blog/how-we-are-building-parking-maps-for-the-world-at-spotangels/

*Verification: dataset IDs `6r5h-j298`, `imvp-dq3v`, `yhqp-riqs`, `9ivs-nf5y` confirmed via live
Socrata API 2026-06-06. arXiv items (Jordon, FADACS) are preprints, flagged as such. Several
publisher pages (ScienceDirect/MDPI/Tandfonline) returned 403 to the crawler; those claims rest on
parsed PDFs/abstracts + corroborating sources.*
