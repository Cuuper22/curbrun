#!/usr/bin/env python3
"""Unit tests for the pure spatial-join helpers in attach_capacity.py.

Network fetching is not exercised here; these cover the geometry/centroid,
parsing, grid matching, and DB enrichment that map surveyed supply onto segments.
"""

from __future__ import annotations

import sqlite3
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import attach_capacity as a


class CentroidTest(unittest.TestCase):
    def test_linestring_centroid_returns_lat_lng(self):
        # GeoJSON coordinates are [lng, lat]; centroid returns (lat, lng).
        lat, lng = a.centroid([[-122.41, 37.77], [-122.43, 37.79]])
        self.assertAlmostEqual(lat, 37.78)
        self.assertAlmostEqual(lng, -122.42)

    def test_empty_or_none_is_none(self):
        self.assertIsNone(a.centroid([]))
        self.assertIsNone(a.centroid(None))


class CensusPointsTest(unittest.TestCase):
    def test_parses_supply_and_skips_unusable_rows(self):
        rows = [
            {"shape": {"coordinates": [[-122.41, 37.77], [-122.41, 37.78]]}, "prkg_sply": "20"},
            {"shape": {"coordinates": []}, "prkg_sply": "5"},          # no geometry -> skip
            {"shape": {"coordinates": [[-122.4, 37.7]]}, "prkg_sply": None},  # no supply -> skip
            {"shape": {"coordinates": [[-122.4, 37.7]]}, "prkg_sply": "x"},   # bad supply -> skip
        ]
        points = a.census_points(rows)
        self.assertEqual(len(points), 1)
        self.assertEqual(points[0][2], 20)


class NearestSupplyTest(unittest.TestCase):
    def test_picks_closest_within_radius(self):
        grid = a.build_grid([(37.7701, -122.4101, 10), (37.8000, -122.4500, 99)])
        self.assertEqual(a.nearest_supply(grid, 37.7700, -122.4100), 10)

    def test_returns_none_outside_radius(self):
        grid = a.build_grid([(37.9000, -122.5000, 42)])
        self.assertIsNone(a.nearest_supply(grid, 37.7700, -122.4100))


class EnrichTest(unittest.TestCase):
    def test_adds_column_and_updates_only_matched_segments(self):
        conn = sqlite3.connect(":memory:")
        conn.executescript("create table curb_segment (id text primary key, latitude real, longitude real);")
        conn.execute("insert into curb_segment values ('near', 37.7700, -122.4100)")
        conn.execute("insert into curb_segment values ('far', 37.9500, -122.5500)")
        conn.commit()
        rows = [{"shape": {"coordinates": [[-122.4100, 37.7700], [-122.4100, 37.7701]]}, "prkg_sply": "25"}]

        matched = a.enrich(conn, rows)

        self.assertEqual(matched, 1)
        self.assertEqual(conn.execute("select measured_spaces from curb_segment where id='near'").fetchone()[0], 25)
        self.assertIsNone(conn.execute("select measured_spaces from curb_segment where id='far'").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
