#!/usr/bin/env python3
"""
Build the CurbRun local curb database.

The app needs a legal/free recommendation engine, not just a pretty map. This
builder fuses SFMTA Digital Curb with the broader SFMTA parking map layers into
a compact SQLite asset the APK can evaluate offline.
"""

from __future__ import annotations

import argparse
import json
import math
import sqlite3
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


SFMTA_PARKING = "https://services.sfmta.com/arcgis/rest/services/Parking/parking/MapServer"
SFMTA_DIGITAL_CURB = "https://services.sfmta.com/arcgis/rest/services/Parking/digitalcurb/MapServer/0/query"
LAYER_STREET_CLEANING = f"{SFMTA_PARKING}/3/query"
LAYER_TIME_LIMITED = f"{SFMTA_PARKING}/9/query"
LAYER_OTHER_REGS = f"{SFMTA_PARKING}/10/query"
LAYER_METERED_BLOCKFACES = f"{SFMTA_PARKING}/12/query"
LAYER_COLOR_CURB = f"{SFMTA_PARKING}/18/query"

DAY_TOKENS = {
    "M": ["mon"],
    "TU": ["tue"],
    "T": ["tue", "thu"],
    "W": ["wed"],
    "TH": ["thu"],
    "F": ["fri"],
    "SA": ["sat"],
    "S": ["sun"],
    "SU": ["sun"],
}
ALL_DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]


@dataclass
class SegmentIndex:
    segment_id: str
    lat: float
    lng: float
    street: str


def fetch_json(url: str, params: dict[str, str | int]) -> dict[str, Any]:
    query = urllib.parse.urlencode(params)
    with urllib.request.urlopen(f"{url}?{query}", timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def paged_arcgis_features(url: str, where: str = "1=1", page_size: int = 2000):
    offset = 0
    while True:
        payload = fetch_json(
            url,
            {
                "where": where,
                "outFields": "*",
                "returnGeometry": "true",
                "f": "geojson",
                "resultRecordCount": page_size,
                "resultOffset": offset,
            },
        )
        features = payload.get("features", [])
        if not features:
            break
        yield from features
        if not payload.get("properties", {}).get("exceededTransferLimit"):
            break
        offset += page_size
        time.sleep(0.08)


def init_db(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)
    conn.executescript(
        """
        drop table if exists curb_segment;
        drop table if exists curb_rule;
        drop table if exists build_metadata;

        create table curb_segment (
            id text primary key,
            street text not null,
            cross_street text,
            latitude real not null,
            longitude real not null,
            polyline_json text not null,
            parkable_feet integer not null,
            base_availability real not null,
            traffic_pressure real not null,
            parked_car_density real not null,
            source text not null,
            updated_at text,
            measured_spaces integer
        );

        create table curb_rule (
            id integer primary key autoincrement,
            segment_id text not null references curb_segment(id),
            kind text not null,
            days_json text,
            start_minute integer,
            end_minute integer,
            max_stay_minutes integer,
            label text not null
        );

        create table build_metadata (
            key text primary key,
            value text not null
        );

        create index idx_curb_segment_location on curb_segment(latitude, longitude);
        create index idx_curb_rule_segment on curb_rule(segment_id);
        """
    )
    return conn


def flatten_coords(geometry: dict[str, Any]) -> list[list[float]]:
    coords = geometry.get("coordinates") or []
    gtype = geometry.get("type")
    if gtype == "LineString":
        return coords
    if gtype == "MultiLineString":
        return [point for line in coords for point in line]
    if gtype == "Point":
        return [coords]
    if gtype == "MultiPoint":
        return coords
    return []


def centroid(coords: list[list[float]]) -> tuple[float, float]:
    lng = sum(point[0] for point in coords) / len(coords)
    lat = sum(point[1] for point in coords) / len(coords)
    return lat, lng


def distance_miles(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    radius = 3958.7613
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = math.sin(dlat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng / 2) ** 2
    return radius * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def nearest_segments(index: list[SegmentIndex], lat: float, lng: float, max_count: int, max_miles: float, street_hint: str | None = None) -> list[str]:
    hint = normalize_street(street_hint)
    scored = []
    for item in index:
        dist = distance_miles(lat, lng, item.lat, item.lng)
        if dist > max_miles:
            continue
        street_bonus = 0.0 if not hint or hint in normalize_street(item.street) else 0.06
        scored.append((dist + street_bonus, item.segment_id))
    scored.sort(key=lambda row: row[0])
    return [segment_id for _, segment_id in scored[:max_count]]


def normalize_street(value: str | None) -> str:
    if not value:
        return ""
    return " ".join(value.lower().replace(".", "").split())


def insert_rule(
    conn: sqlite3.Connection,
    segment_id: str,
    kind: str,
    label: str,
    days: list[str] | None = None,
    start_minute: int | None = None,
    end_minute: int | None = None,
    max_stay_minutes: int | None = None,
) -> None:
    conn.execute(
        """
        insert into curb_rule(segment_id, kind, days_json, start_minute, end_minute, max_stay_minutes, label)
        values (?, ?, ?, ?, ?, ?, ?)
        """,
        (
            segment_id,
            kind,
            json.dumps(days, separators=(",", ":")) if days else None,
            start_minute,
            end_minute,
            max_stay_minutes,
            label,
        ),
    )


def digital_curb_to_db(conn: sqlite3.Connection, limit: int | None) -> list[SegmentIndex]:
    index: list[SegmentIndex] = []
    seen_ids: set[str] = set()
    for feature in paged_arcgis_features(SFMTA_DIGITAL_CURB, "IS_ACTIVE='Y'"):
        props = feature["properties"]
        coords = flatten_coords(feature.get("geometry") or {})
        if len(coords) < 1:
            continue
        lat, lng = centroid(coords)
        segment_id = props.get("CURB_ZONE_ID") or f"digital-{len(index)}"
        activity = (props.get("RULES_ACTIVITY") or "parking").lower()
        rate = as_int(props.get("RATE_RATE")) or 0
        kind = "FreeParking"
        label = "Free curb policy."
        if "no parking" in activity:
            kind = "NoParking"
            label = "Digital Curb: no parking."
        elif "loading" in activity or "load" in activity:
            kind = "Loading"
            label = "Digital Curb: loading zone."
        elif rate > 0:
            kind = "PaidMeter"
            label = "Digital Curb: paid meter policy."

        start_minute = parse_clock(props.get("TIME_SPANS_TIME_OF_DAY_START"))
        end_minute = parse_clock(props.get("TIME_SPANS_TIME_OF_DAY_END"))
        days = parse_days(props.get("TIME_SPANS_DAYS_OF_WEEK"))
        cross = props.get("CROSS_STREET_START_NAME") or props.get("CROSS_STREET_END_NAME")
        street = props.get("STREET_NAME") or (f"{cross} curb" if cross else "SF curb")
        spaces = as_int(props.get("NUM_SPACES")) or 1

        conn.execute(
            """
            insert or replace into curb_segment
                (id, street, cross_street, latitude, longitude, polyline_json, parkable_feet,
                 base_availability, traffic_pressure, parked_car_density, source, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                segment_id,
                street,
                cross,
                lat,
                lng,
                json.dumps(coords, separators=(",", ":")),
                max(8, spaces * 18),
                0.58,
                0.46,
                0.48,
                "sfmta_digital_curb",
                str(props.get("LAST_UPDATED_TIMESTAMP") or ""),
            ),
        )
        insert_rule(conn, segment_id, kind, label, days, start_minute, end_minute, props.get("RULES_MAX_STAY"))
        # A single CURB_ZONE_ID can appear across multiple features (one per
        # schedule). The segment row is deduped by "insert or replace" and each
        # feature still contributes a rule, so only index distinct segments to
        # keep the spatial join and the reported segment count accurate.
        if segment_id not in seen_ids:
            seen_ids.add(segment_id)
            index.append(SegmentIndex(segment_id, lat, lng, street))
        if limit and len(index) >= limit:
            break
    conn.commit()
    return index


def attach_street_cleaning(conn: sqlite3.Connection, index: list[SegmentIndex], limit: int | None) -> int:
    count = 0
    for feature in paged_arcgis_features(LAYER_STREET_CLEANING):
        props = feature["properties"]
        coords = flatten_coords(feature.get("geometry") or {})
        if not coords:
            continue
        lat, lng = centroid(coords)
        days = parse_days(props.get("DAYS") or props.get("WEEKDAY"))
        start = parse_clock(props.get("HOURS") or props.get("FROMHOUR") or props.get("HRS_BEGIN"))
        end = parse_end_clock(props.get("HOURS") or props.get("TOHOUR") or props.get("HRS_END"), start)
        label = compact_label("Street cleaning", props.get("DAYS"), props.get("HOURS"))
        for segment_id in nearest_segments(index, lat, lng, 3, 0.035, props.get("STREETNAME") or props.get("STREET")):
            insert_rule(conn, segment_id, "StreetCleaning", label, days, start, end)
            count += 1
        if limit and count >= limit:
            break
    conn.commit()
    return count


def attach_time_limits(conn: sqlite3.Connection, index: list[SegmentIndex], limit: int | None) -> int:
    count = 0
    for feature in paged_arcgis_features(LAYER_TIME_LIMITED):
        props = feature["properties"]
        coords = flatten_coords(feature.get("geometry") or {})
        if not coords:
            continue
        lat, lng = centroid(coords)
        days = parse_days(props.get("DAYS"))
        start = parse_clock(props.get("HRS_BEGIN") or props.get("FROM_TIME"))
        end = parse_clock(props.get("HRS_END") or props.get("TO_TIME"))
        max_stay = parse_hours_to_minutes(props.get("HRLIMIT"))
        area = clean(props.get("RPPAREA1"))
        kind = "ResidentialPermit" if area else "TimeLimit"
        label = f"{max_stay // 60 if max_stay else ''} hr non-permit limit".strip()
        if area:
            label += f" in RPP {area}"
        for segment_id in nearest_segments(index, lat, lng, 3, 0.045):
            insert_rule(conn, segment_id, kind, label, days, start, end, max_stay)
            count += 1
        if limit and count >= limit:
            break
    conn.commit()
    return count


def attach_other_regulations(conn: sqlite3.Connection, index: list[SegmentIndex], limit: int | None) -> int:
    count = 0
    for feature in paged_arcgis_features(LAYER_OTHER_REGS):
        props = feature["properties"]
        coords = flatten_coords(feature.get("geometry") or {})
        if not coords:
            continue
        regulation = (props.get("REGULATION") or "").lower()
        if "unregulated" in regulation:
            continue
        kind = "NoParking"
        if "tow" in regulation:
            kind = "TowAway"
        elif "time" in regulation or "rpp" in regulation:
            kind = "ResidentialPermit" if "rpp" in regulation else "TimeLimit"
        lat, lng = centroid(coords)
        days = parse_days(props.get("DAYS"))
        start = parse_clock(props.get("HRS_BEGIN") or props.get("FROM_TIME"))
        end = parse_clock(props.get("HRS_END") or props.get("TO_TIME"))
        max_stay = parse_hours_to_minutes(props.get("HRLIMIT"))
        label = clean(props.get("REGULATION")) or "Other parking regulation"
        for segment_id in nearest_segments(index, lat, lng, 2, 0.04, props.get("STREETNAME") or props.get("STREET")):
            insert_rule(conn, segment_id, kind, label, days, start, end, max_stay)
            count += 1
        if limit and count >= limit:
            break
    conn.commit()
    return count


def attach_metered_blockfaces(conn: sqlite3.Connection, index: list[SegmentIndex], limit: int | None) -> int:
    count = 0
    for feature in paged_arcgis_features(LAYER_METERED_BLOCKFACES):
        props = feature["properties"]
        coords = flatten_coords(feature.get("geometry") or {})
        if not coords:
            continue
        lat, lng = centroid(coords)
        label = "Metered blockface, payment likely required during posted meter hours."
        for segment_id in nearest_segments(index, lat, lng, 3, 0.035, props.get("STREETNAME")):
            insert_rule(conn, segment_id, "PaidMeter", label, ALL_DAYS, 9 * 60, 18 * 60)
            count += 1
        if limit and count >= limit:
            break
    conn.commit()
    return count


def attach_color_curbs(conn: sqlite3.Connection, index: list[SegmentIndex], limit: int | None) -> int:
    count = 0
    for feature in paged_arcgis_features(LAYER_COLOR_CURB):
        props = feature["properties"]
        coords = flatten_coords(feature.get("geometry") or {})
        if not coords:
            continue
        zone_type = (props.get("ZONE_TYPE") or props.get("ASSET_NAME") or "").lower()
        kind = "FreeParking"
        if "red" in zone_type or "no parking" in zone_type:
            kind = "NoParking"
        elif "white" in zone_type or "yellow" in zone_type or "loading" in zone_type:
            kind = "Loading"
        label = clean(props.get("ZONE_TYPE")) or clean(props.get("ASSET_NAME")) or "Color curb restriction"
        lat, lng = centroid(coords)
        max_matches = 1 if kind != "NoParking" else 2
        max_miles = 0.008 if kind != "NoParking" else 0.015
        for segment_id in nearest_segments(index, lat, lng, max_matches, max_miles, props.get("SUBJECT_LO")):
            insert_rule(conn, segment_id, kind, label)
            count += 1
        if limit and count >= limit:
            break
    conn.commit()
    return count


def recompute_availability(conn: sqlite3.Connection) -> None:
    rows = conn.execute(
        """
        select s.id,
               sum(case when r.kind='PaidMeter' then 1 else 0 end) paid,
               sum(case when r.kind in ('ResidentialPermit','TimeLimit') then 1 else 0 end) limited,
               sum(case when r.kind in ('NoParking','TowAway','Loading') then 1 else 0 end) hard
        from curb_segment s
        left join curb_rule r on r.segment_id = s.id
        group by s.id
        """
    ).fetchall()
    for segment_id, paid, limited, hard in rows:
        pressure = min(0.95, 0.34 + (paid or 0) * 0.07 + (limited or 0) * 0.04 + (hard or 0) * 0.025)
        density = min(0.95, 0.42 + pressure * 0.28)
        availability = max(0.12, 0.82 - pressure * 0.36 - density * 0.14)
        conn.execute(
            "update curb_segment set base_availability=?, traffic_pressure=?, parked_car_density=? where id=?",
            (availability, pressure, density, segment_id),
        )
    conn.commit()


def parse_clock(value: Any) -> int | None:
    if value is None or value == "":
        return None
    if isinstance(value, (int, float)):
        ivalue = int(value)
        if ivalue > 100:
            return normalize_clock_minutes((ivalue // 100) * 60 + (ivalue % 100))
        return normalize_clock_minutes(ivalue * 60)
    text = str(value).strip().lower()
    if "-" in text and text.replace("-", "").isdigit():
        text = text.split("-", 1)[0]
    if ":" in text:
        hour, minute = text.replace("am", "").replace("pm", "").split(":")[:2]
        h = int(hour)
        m = int("".join(ch for ch in minute if ch.isdigit()) or "0")
        if "pm" in text and h != 12:
            h += 12
        if "am" in text and h == 12:
            h = 0
        return normalize_clock_minutes(h * 60 + m)
    digits = "".join(ch for ch in text if ch.isdigit())
    if not digits:
        return None
    ivalue = int(digits)
    if ivalue > 100:
        return normalize_clock_minutes((ivalue // 100) * 60 + (ivalue % 100))
    if "pm" in text and ivalue != 12:
        ivalue += 12
    if "am" in text and ivalue == 12:
        ivalue = 0
    return normalize_clock_minutes(ivalue * 60)


def normalize_clock_minutes(minutes: int) -> int:
    return 0 if minutes == 24 * 60 else minutes


def parse_end_clock(value: Any, start: int | None) -> int | None:
    text = str(value or "")
    if "-" in text:
        return parse_clock(text.split("-", 1)[1])
    parsed = parse_clock(value)
    if parsed is not None:
        return parsed
    if start is None:
        return None
    return start + 120


def parse_days(value: Any) -> list[str] | None:
    if value is None or value == "":
        return None
    if isinstance(value, list):
        return [str(day).lower()[:3] for day in value]
    text = str(value).strip().lower()
    if text.startswith("["):
        try:
            return [str(day).lower()[:3] for day in json.loads(text)]
        except json.JSONDecodeError:
            pass
    text = text.replace("m-f", "mon,tue,wed,thu,fri")
    text = text.replace("m-sa", "mon,tue,wed,thu,fri,sat")
    text = text.replace("daily", ",".join(ALL_DAYS))
    found: list[str] = []
    for token in text.replace("/", ",").replace(";", ",").replace(" ", ",").split(","):
        token = token.strip().upper()
        if not token:
            continue
        if token in DAY_TOKENS:
            found.extend(DAY_TOKENS[token])
            continue
        short = token[:3].lower()
        if short in ALL_DAYS:
            found.append(short)
    return sorted(set(found), key=ALL_DAYS.index) if found else None


def parse_hours_to_minutes(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(float(value) * 60)
    except (TypeError, ValueError):
        return None


def as_int(value: Any) -> int | None:
    try:
        if value is None or value == "":
            return None
        return int(float(value))
    except (TypeError, ValueError):
        return None


def clean(value: Any) -> str | None:
    if value is None:
        return None
    text = " ".join(str(value).split())
    return text if text and text != " " else None


def compact_label(prefix: str, days: Any, hours: Any) -> str:
    parts = [prefix]
    if clean(days):
        parts.append(str(days).strip())
    if clean(hours):
        parts.append(str(hours).strip())
    return " ".join(parts)


def store_metadata(conn: sqlite3.Connection, metadata: dict[str, Any]) -> None:
    for key, value in metadata.items():
        conn.execute("insert or replace into build_metadata(key, value) values (?, ?)", (key, str(value)))
    conn.commit()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="data/curbrun.sqlite")
    parser.add_argument("--limit", type=int, default=None, help="Limit base Digital Curb segments for faster smoke builds.")
    parser.add_argument("--overlay-limit", type=int, default=None, help="Limit attached overlay rules for smoke builds.")
    args = parser.parse_args()

    conn = init_db(Path(args.out))
    index = digital_curb_to_db(conn, args.limit)
    metadata = {
        "digital_curb_segments": len(index),
        "street_cleaning_rules": attach_street_cleaning(conn, index, args.overlay_limit),
        "time_limit_rules": attach_time_limits(conn, index, args.overlay_limit),
        "other_regulation_rules": attach_other_regulations(conn, index, args.overlay_limit),
        "metered_blockface_rules": attach_metered_blockfaces(conn, index, args.overlay_limit),
        "color_curb_rules": attach_color_curbs(conn, index, args.overlay_limit),
        "built_at_unix": int(time.time()),
    }
    recompute_availability(conn)
    try:
        import attach_capacity
        census_rows = attach_capacity.fetch_census()
        metadata["parking_census_matches"] = attach_capacity.enrich(conn, census_rows)
        metadata["parking_census_source"] = attach_capacity.CENSUS_SOURCE
    except Exception as exc:  # capacity overlay is optional / network-dependent
        metadata["parking_census_matches"] = f"skipped: {exc}"
    store_metadata(conn, metadata)
    conn.close()
    print(f"built {args.out}")
    for key, value in metadata.items():
        print(f"  {key}: {value}")


if __name__ == "__main__":
    main()
