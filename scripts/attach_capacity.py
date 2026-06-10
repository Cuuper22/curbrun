#!/usr/bin/env python3
"""Attach real SFMTA on-street parking-census capacity to curb segments.

Populates a `measured_spaces` column on `curb_segment` from San Francisco's
On-street Parking Census (data.sfgov.org dataset 9ivs-nf5y), which records the
surveyed parking supply (`prkg_sply`) per blockface. Each curb segment is matched
to the nearest surveyed blockface within a short radius. This is real measured
capacity from a field survey, not an estimate.

Usage: python3 scripts/attach_capacity.py --db app/src/main/assets/curbrun.sqlite
"""

from __future__ import annotations

import argparse
import json
import math
import sqlite3
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

CENSUS_URL = "https://data.sfgov.org/resource/9ivs-nf5y.json"
CENSUS_SOURCE = "data.sfgov.org/9ivs-nf5y (SFMTA on-street parking census)"
MATCH_RADIUS_MILES = 0.035
GRID_CELL_DEGREES = 0.003  # ~0.2 mi spatial-join buckets


def fetch_census(page_size: int = 50000) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    offset = 0
    while True:
        params = {
            "$select": "cnn,prkg_sply,shape",
            "$where": "prkg_sply IS NOT NULL",
            "$limit": page_size,
            "$offset": offset,
        }
        url = f"{CENSUS_URL}?{urllib.parse.urlencode(params)}"
        with urllib.request.urlopen(url, timeout=120) as response:
            batch = json.loads(response.read().decode("utf-8"))
        if not batch:
            break
        rows.extend(batch)
        if len(batch) < page_size:
            break
        offset += page_size
    return rows


def centroid(coords: Any) -> tuple[float, float] | None:
    pts: list[list[float]] = []

    def walk(node: Any) -> None:
        if node and isinstance(node[0], (int, float)):
            pts.append(node)
        else:
            for child in node:
                walk(child)

    walk(coords or [])
    if not pts:
        return None
    return (sum(p[1] for p in pts) / len(pts), sum(p[0] for p in pts) / len(pts))


def distance_miles(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    radius = 3958.7613
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = math.sin(dlat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng / 2) ** 2
    return radius * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def census_points(rows: list[dict[str, Any]]) -> list[tuple[float, float, int]]:
    points: list[tuple[float, float, int]] = []
    for row in rows:
        center = centroid((row.get("shape") or {}).get("coordinates"))
        supply = row.get("prkg_sply")
        if center is None or supply is None:
            continue
        try:
            points.append((center[0], center[1], int(float(supply))))
        except (TypeError, ValueError):
            continue
    return points


def build_grid(points: list[tuple[float, float, int]]) -> dict[tuple[int, int], list[tuple[float, float, int]]]:
    grid: dict[tuple[int, int], list[tuple[float, float, int]]] = {}
    for lat, lng, supply in points:
        grid.setdefault((round(lat / GRID_CELL_DEGREES), round(lng / GRID_CELL_DEGREES)), []).append((lat, lng, supply))
    return grid


def nearest_supply(grid, lat: float, lng: float, radius: float = MATCH_RADIUS_MILES) -> int | None:
    cx, cy = round(lat / GRID_CELL_DEGREES), round(lng / GRID_CELL_DEGREES)
    best: tuple[float, int] | None = None
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for clat, clng, supply in grid.get((cx + dx, cy + dy), ()):
                dist = distance_miles(lat, lng, clat, clng)
                if dist <= radius and (best is None or dist < best[0]):
                    best = (dist, supply)
    return best[1] if best else None


def ensure_column(conn: sqlite3.Connection) -> None:
    columns = {row[1] for row in conn.execute("pragma table_info(curb_segment)")}
    if "measured_spaces" not in columns:
        conn.execute("alter table curb_segment add column measured_spaces integer")


def enrich(conn: sqlite3.Connection, rows: list[dict[str, Any]]) -> int:
    ensure_column(conn)
    grid = build_grid(census_points(rows))
    matched = 0
    for segment_id, lat, lng in conn.execute("select id, latitude, longitude from curb_segment").fetchall():
        supply = nearest_supply(grid, lat, lng)
        if supply is not None:
            conn.execute("update curb_segment set measured_spaces=? where id=?", (supply, segment_id))
            matched += 1
    conn.commit()
    return matched


def main() -> None:
    parser = argparse.ArgumentParser(description="Attach SFMTA parking-census capacity to curb segments.")
    parser.add_argument("--db", default="app/src/main/assets/curbrun.sqlite")
    args = parser.parse_args()

    conn = sqlite3.connect(Path(args.db))
    rows = fetch_census()
    matched = enrich(conn, rows)
    total = conn.execute("select count(*) from curb_segment").fetchone()[0]
    conn.execute(
        "insert or replace into build_metadata(key, value) values ('parking_census_matches', ?)",
        (str(matched),),
    )
    conn.execute(
        "insert or replace into build_metadata(key, value) values ('parking_census_source', ?)",
        (CENSUS_SOURCE,),
    )
    conn.commit()
    conn.close()
    print(f"census blockfaces fetched: {len(rows)}; segments matched: {matched}/{total}")


if __name__ == "__main__":
    main()
