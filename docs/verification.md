# Verification

Use this checklist before shipping a debug APK or opening a pull request.

## Local Gate

```powershell
.\scripts\verify.ps1
```

This checks that the bundled curb database exists, builds the debug APK, runs unit tests, runs Android lint, and verifies that the APK artifact was produced.

The gate also validates the bundled SQLite asset with `scripts/validate_curb_db.py`: schema, segment/rule counts, rule-kind coverage, SF geographic bounds, orphan rules, time-window ranges, JSON fields, and build metadata.

Unit tests cover legality windows, paid-meter exclusion, radius filtering, closest-first ranking, availability tie-breaking, confidence tiers, curb-clock formatting, route planning, and SF search anchors.

## Visual Smoke

When an Android emulator or physical device is attached:

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell pm clear com.cuuper.sfpark
adb shell logcat -c
adb shell am start -n com.cuuper.sfpark/.MainActivity
```

Then confirm:

- The real map tiles render.
- Location permission flow works.
- Header shows free-route count and live curb clock.
- Duration changes rerank candidates.
- Search radius changes rerank candidates and persists after relaunch.
- Neighborhood quick-jump chips rerank around the chosen SF area.
- Duration, search radius, vehicle profile, and chosen search area persist after relaunch.
- Privacy/accuracy notice explains local ranking, local saved choices, and posted-sign override.
- Candidate cards show free-until timing and next restriction context.
- Empty-result recovery card appears when no legal free curb matches, and its shorten/widen actions rerank immediately.
- Selected result shows a clear-time countdown and compact legal evidence badges.
- Selected result shows a confidence tier and sign-check guidance.
- Candidate cards and selected result include source provenance for the curb segment.
- TalkBack/UI tree labels identify map, duration slider, vehicle chips, neighborhood chips, route stops, candidate cards, confidence dials, and navigation actions.
- Route queue appears when candidates exist.
- Single-stop and multi-stop navigation handoffs open a maps intent.
- Crash buffer is clean:

```powershell
adb logcat -b crash -d
```

Latest emulator smoke, 2026-06-03 on AVD `CurbRunApi35`:

- `.\scripts\verify.ps1` passed with `2,668` segments, `21,268` rules, `7` rule kinds, unit tests, lint, and debug APK generation.
- Rebuilt APK installed successfully from `app/build/outputs/apk/debug/app-debug.apk` (`15,534,607` bytes).
- First-launch location permission rendered and the app opened to the main parking surface with no crash-buffer entries.
- SoMa quick-jump selected, reranked the top candidate from Jessie Street to `SF curb`, and changed route sweep from `2.6 mi` to `2.2 mi`.
- Route handoff opened Google Maps directions to `629 Bryant St, San Francisco, CA 94107` with driving route loaded and `Start driving navigation` available.
- Back navigation returned focus to `com.cuuper.sfpark/.MainActivity`; selected SoMa state and reranked candidates were still present.
- Captured smoke artifacts live under `build/curbrun-main-after-badge.png`, `build/curbrun-soma-selected.png`, `build/curbrun-route-loaded.png`, and `build/curbrun-returned-to-app.png`.

## CI Gate

GitHub Actions runs the same database and Gradle gates on pushes and pull requests to `main`, uploads the debug APK, and keeps the lint/database reports as artifacts.
