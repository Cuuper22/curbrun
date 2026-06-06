# Data Safety

CurbRun is designed as a local-first Android utility.

## Location

- The app requests fine/coarse location so it can rank nearby San Francisco curb segments.
- Location is used in memory to build parking queries and is not written to the bundled database.
- If no usable San Francisco location is available, the app falls back to a San Francisco center point or the selected neighborhood search anchor.

## Stored Data

The app stores only local preferences through Android `SharedPreferences`:

- requested parking duration
- requested search radius
- vehicle profile
- selected San Francisco search anchor

These preferences stay on the device and are used to restore the app state on relaunch.

## Network

- The bundled curb database is packaged inside the APK.
- Map tiles are loaded through osmdroid/OpenStreetMap rendering.
- Navigation handoff opens external map intents for single-stop or multi-stop routes.

## Accuracy

CurbRun filters for free legal curb candidates using bundled SFMTA-derived data and local timing rules, but curb signs, temporary restrictions, construction, driveways, hydrants, and enforcement changes can supersede the bundled data. Users should verify posted signs before leaving a vehicle.

Availability, traffic-pressure, and curb-density scores (and the confidence tier derived from them) are modeled heuristics computed from the density of nearby parking regulations, not measured real-time occupancy. They indicate relative competition between candidates rather than a guaranteed open space.
