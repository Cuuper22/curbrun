# CurbRun

CurbRun is a native Android app for finding legal free curb parking in San Francisco for a requested `N` hour window.

It ranks streets by closest-first legal fit, then availability risk. The app is designed as a fast navigation surface, not a civic-data browser: duration, vehicle profile, candidate streets, legality reasons, and external navigation are all one screen.

## What Works Now

- Kotlin native Android app with Jetpack Compose.
- Bold high-contrast "night-ops" UI over a real native OpenStreetMap surface: a live curb-clock hero, duration presets, radius control, a vehicle toggle, candidate cards with availability rings/bars, a route sweep strip, and one-tap navigation handoff.
- SF neighborhood quick-jump search anchors for destination-style parking searches around Mission, SoMa, Hayes, Marina, Sunset, and Richmond.
- Saved parking preferences restore the last duration, search radius, vehicle profile, and chosen search area on relaunch.
- Whole-window legality engine for `[now, now + N hours]`.
- Free-only filtering for paid meters, no-parking rules, loading/color curb windows, street cleaning, time limits, and curb length.
- Curb-clock timing: each candidate carries the next known restriction time and reason, so the UI can show "free until" instead of only proving the requested window.
- Live reranking loop keeps the curb clock current as time moves, preserving the selected curb when it remains legal.
- Countdown-style result badges show how long the curb remains clear, plus free-only/risk evidence for quick trust checks.
- Empty-result recovery card gives one-tap shorter-window and wider-radius retries when a free-only search is too constrained.
- Confidence tiers label each result as strong, sign-check-required, or high-competition based on availability and risk factors.
- Candidate cards carry curb-source provenance, including bundled SFMTA Digital Curb segments.
- Real surveyed curb capacity from the SFMTA on-street parking census: candidates show the measured number of spaces on the blockface, and that capacity nudges the availability estimate.
- Accessibility semantics label the map, controls, route stops, candidate cards, confidence dials, and navigation actions for TalkBack/UI-tree QA.
- Closest-first ranking with availability/risk penalties.
- Greedy multi-stop search route over the best legal candidates, with Google Maps directions handoff.
- Bundled SQLite curb database loaded from APK assets, with Kotlin seed data only as fallback.
- Reproducible data pipeline in `scripts/build_curb_db.py` for SFMTA Digital Curb plus street-cleaning, time-limit/RPP, other regulation, metered blockface, and color-curb overlays, plus a real parking-census capacity overlay (`scripts/attach_capacity.py`).

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS/Linux:

```bash
./gradlew :app:assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Full local verification:

```powershell
.\scripts\verify.ps1
```

CI is configured in `.github/workflows/android.yml` to validate the bundled curb database, run the data-pipeline unit tests, build the debug APK, run unit tests, run lint, and upload APK/lint/database artifacts.

The Kotlin unit test suite covers legal-window blocking, paid-meter exclusion, radius filtering, closest-first ranking, availability tie-breaking, confidence tiers, curb-clock labels, route planning, bundled-asset polyline/day parsing, modeled-density risk flagging, measured-capacity availability and census provenance, selection preservation, and SF search anchors. A separate Python suite (`scripts/test_build_curb_db.py`, `scripts/test_attach_capacity.py`) covers the data-pipeline clock/day/geometry parsers, the availability-scoring model, and the parking-census spatial join.

Every Kotlin unit test is pure-JVM (the `domain` package plus `CurbAssetParsing`, with no Android framework), so `scripts/run_unit_tests_offline.sh` can compile and run the whole suite using the Kotlin compiler and JUnit jars bundled with any local Gradle 8.x install — verifying the core logic without an Android SDK, network, or CI. The canonical gate remains `./gradlew testDebugUnitTest`.

## Data Pipeline

Build a generated SQLite curb database:

```powershell
py scripts/build_curb_db.py --out app/src/main/assets/curbrun.sqlite --limit 5000 --overlay-limit 8000
```

The bundled asset currently contains thousands of SFMTA curb policies and attached overlay rules. Availability, traffic-pressure, and curb-density scores are transparent modeled heuristics derived from the density of nearby regulations, not measured occupancy, so they signal relative competition rather than a guaranteed open space. Measured curb **capacity** is now real: `scripts/attach_capacity.py` joins the SFMTA on-street parking census (surveyed `prkg_sply` per blockface) onto each segment's `measured_spaces` column, which the app surfaces as provenance and folds into the availability estimate. Live **occupancy** — how many of those spaces are taken at any moment — has no public real-time feed for SF residential curbs and remains the next upgrade; its seam is narrow, since such a feed would write the same `curb_segment` columns with no schema or UI change.

## Product Bar

The target is not a demo: it is a polished, installable APK with a premium, fast, navigation-grade interface and a serious legal/availability engine.

See `docs/verification.md` for the release checklist and emulator smoke flow.
See `docs/data-safety.md` for location, storage, network, and accuracy notes.
