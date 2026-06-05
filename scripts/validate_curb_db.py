#!/usr/bin/env python3
"""Validate the bundled CurbRun SQLite curb asset."""

from __future__ import annotations

import argparse
import json
import sqlite3
from pathlib import Path
from typing import Any


EXPECTED_KINDS = {
    "FreeParking",
    "Loading",
    "NoParking",
    "PaidMeter",
    "ResidentialPermit",
    "StreetCleaning",
    "TimeLimit",
    "TowAway",
}

SF_LAT_MIN = 37.68
SF_LAT_MAX = 37.84
SF_LNG_MIN = -122.54
SF_LNG_MAX = -122.34


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate CurbRun's bundled curb database.")
    parser.add_argument("--db", default="app/src/main/assets/curbrun.sqlite", help="SQLite database path")
    parser.add_argument("--report", default="build/curb-db-validation.json", help="JSON report path")
    parser.add_argument("--min-segments", type=int, default=2000)
    parser.add_argument("--min-rules", type=int, default=10000)
    args = parser.parse_args()

    db_path = Path(args.db)
    report_path = Path(args.report)
    failures: list[str] = []
    stats: dict[str, Any] = {"db": str(db_path)}

    if not db_path.exists():
        raise SystemExit(f"Missing database: {db_path}")
    if db_path.stat().st_size < 1_000_000:
        failures.append(f"Database is unexpectedly small: {db_path.stat().st_size} bytes")

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    required_tables = {"curb_segment", "curb_rule", "build_metadata"}
    tables = {row["name"] for row in cur.execute("select name from sqlite_master where type='table'")}
    missing_tables = sorted(required_tables - tables)
    if missing_tables:
        failures.append(f"Missing tables: {', '.join(missing_tables)}")

    segment_count = cur.execute("select count(*) from curb_segment").fetchone()[0]
    rule_count = cur.execute("select count(*) from curb_rule").fetchone()[0]
    metadata_count = cur.execute("select count(*) from build_metadata").fetchone()[0]
    stats.update(
        segment_count=segment_count,
        rule_count=rule_count,
        metadata_count=metadata_count,
    )

    if segment_count < args.min_segments:
        failures.append(f"Too few curb segments: {segment_count} < {args.min_segments}")
    if rule_count < args.min_rules:
        failures.append(f"Too few curb rules: {rule_count} < {args.min_rules}")
    if metadata_count < 3:
        failures.append(f"Too few build metadata rows: {metadata_count}")

    bounds = cur.execute(
        """
        select min(latitude), max(latitude), min(longitude), max(longitude),
               min(parkable_feet), max(parkable_feet),
               min(base_availability), max(base_availability),
               min(traffic_pressure), max(traffic_pressure),
               min(parked_car_density), max(parked_car_density)
        from curb_segment
        """
    ).fetchone()
    stats["bounds"] = {
        "min_lat": bounds[0],
        "max_lat": bounds[1],
        "min_lng": bounds[2],
        "max_lng": bounds[3],
    }
    if not (SF_LAT_MIN <= bounds[0] <= bounds[1] <= SF_LAT_MAX):
        failures.append(f"Latitude bounds are outside San Francisco: {bounds[0]}..{bounds[1]}")
    if not (SF_LNG_MIN <= bounds[2] <= bounds[3] <= SF_LNG_MAX):
        failures.append(f"Longitude bounds are outside San Francisco: {bounds[2]}..{bounds[3]}")
    if bounds[4] < 8:
        failures.append(f"Parkable feet contains too-small values: min={bounds[4]}")
    for label, low, high in [
        ("base_availability", bounds[6], bounds[7]),
        ("traffic_pressure", bounds[8], bounds[9]),
        ("parked_car_density", bounds[10], bounds[11]),
    ]:
        if low < 0 or high > 1:
            failures.append(f"{label} is outside 0..1: {low}..{high}")

    kinds = {
        row["kind"]: row["count"]
        for row in cur.execute("select kind, count(*) as count from curb_rule group by kind")
    }
    stats["rule_kinds"] = kinds
    unknown_kinds = sorted(set(kinds) - EXPECTED_KINDS)
    if unknown_kinds:
        failures.append(f"Unknown rule kinds: {', '.join(unknown_kinds)}")
    for required_kind in ["FreeParking", "PaidMeter", "StreetCleaning", "NoParking"]:
        if kinds.get(required_kind, 0) == 0:
            failures.append(f"Missing required rule kind: {required_kind}")

    orphan_rules = cur.execute(
        """
        select count(*)
        from curb_rule rule
        left join curb_segment segment on segment.id = rule.segment_id
        where segment.id is null
        """
    ).fetchone()[0]
    stats["orphan_rules"] = orphan_rules
    if orphan_rules:
        failures.append(f"Found orphan rules: {orphan_rules}")

    sources = {
        row["source"]: row["count"]
        for row in cur.execute("select source, count(*) as count from curb_segment group by source")
    }
    stats["sources"] = sources
    missing_sources = cur.execute(
        "select count(*) from curb_segment where source is null or trim(source) = ''"
    ).fetchone()[0]
    stats["missing_sources"] = missing_sources
    if missing_sources:
        failures.append(f"Found curb segments without source provenance: {missing_sources}")
    if not sources:
        failures.append("No curb segment source provenance found")

    bad_time_windows = cur.execute(
        """
        select count(*)
        from curb_rule
        where (start_minute is not null and (start_minute < 0 or start_minute >= 1440))
           or (end_minute is not null and (end_minute < 0 or end_minute > 1440))
        """
    ).fetchone()[0]
    stats["bad_time_windows"] = bad_time_windows
    if bad_time_windows:
        failures.append(f"Found invalid time windows: {bad_time_windows}")

    bad_days = 0
    bad_polylines = 0
    allowed_days = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"}
    for row in cur.execute("select days_json from curb_rule where days_json is not null"):
        try:
            days = json.loads(row["days_json"])
        except json.JSONDecodeError:
            bad_days += 1
            continue
        if not isinstance(days, list) or not set(days).issubset(allowed_days):
            bad_days += 1
    for row in cur.execute("select polyline_json from curb_segment"):
        try:
            polyline = json.loads(row["polyline_json"])
        except json.JSONDecodeError:
            bad_polylines += 1
            continue
        if not isinstance(polyline, list) or not polyline:
            bad_polylines += 1
            continue
        for point in polyline:
            if not (
                isinstance(point, list)
                and len(point) >= 2
                and SF_LNG_MIN <= float(point[0]) <= SF_LNG_MAX
                and SF_LAT_MIN <= float(point[1]) <= SF_LAT_MAX
            ):
                bad_polylines += 1
                break

    stats["bad_days_json"] = bad_days
    stats["bad_polyline_json"] = bad_polylines
    if bad_days:
        failures.append(f"Found invalid days JSON rows: {bad_days}")
    if bad_polylines:
        failures.append(f"Found invalid polyline JSON rows: {bad_polylines}")

    metadata = {
        row["key"]: row["value"]
        for row in cur.execute("select key, value from build_metadata order by key")
    }
    stats["metadata"] = metadata
    if "built_at_unix" not in metadata:
        failures.append("Missing build timestamp metadata")

    report = {"ok": not failures, "failures": failures, "stats": stats}
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        print(f"Wrote validation report: {report_path}")
        return 1

    print(
        "Curb DB validation passed: "
        f"{segment_count:,} segments, {rule_count:,} rules, "
        f"{len(kinds)} rule kind(s)."
    )
    print(f"Wrote validation report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
