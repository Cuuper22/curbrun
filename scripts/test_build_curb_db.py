#!/usr/bin/env python3
"""Unit tests for the pure helpers in build_curb_db.py.

These cover the parsing/normalization logic that turns messy SFMTA ArcGIS
fields into the clean clock minutes, day tokens, and geometry the app relies on.
Run with: python3 -m unittest discover -s scripts -p "test_*.py"
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import build_curb_db as b


class ParseClockTest(unittest.TestCase):
    def test_parses_hhmm_integers(self):
        self.assertEqual(b.parse_clock(900), 9 * 60)
        self.assertEqual(b.parse_clock(1830), 18 * 60 + 30)

    def test_normalizes_2400_to_midnight(self):
        self.assertEqual(b.parse_clock(2400), 0)
        self.assertEqual(b.normalize_clock_minutes(24 * 60), 0)
        self.assertEqual(b.normalize_clock_minutes(600), 600)

    def test_parses_am_pm_strings(self):
        self.assertEqual(b.parse_clock("9:00 AM"), 9 * 60)
        self.assertEqual(b.parse_clock("12:00 AM"), 0)
        self.assertEqual(b.parse_clock("12:00 PM"), 12 * 60)
        self.assertEqual(b.parse_clock("6:30 PM"), 18 * 60 + 30)

    def test_returns_none_for_empty(self):
        self.assertIsNone(b.parse_clock(None))
        self.assertIsNone(b.parse_clock(""))


class ParseEndClockTest(unittest.TestCase):
    def test_extracts_end_of_range(self):
        self.assertEqual(b.parse_end_clock("9:00-18:00", None), 18 * 60)

    def test_defaults_to_two_hours_after_start_when_unknown(self):
        self.assertEqual(b.parse_end_clock(None, 9 * 60), 9 * 60 + 120)

    def test_returns_none_without_start_or_value(self):
        self.assertIsNone(b.parse_end_clock(None, None))


class ParseDaysTest(unittest.TestCase):
    def test_expands_weekday_ranges(self):
        self.assertEqual(b.parse_days("M-F"), ["mon", "tue", "wed", "thu", "fri"])

    def test_expands_daily(self):
        self.assertEqual(b.parse_days("Daily"), b.ALL_DAYS)

    def test_handles_ambiguous_single_letter_tokens(self):
        self.assertEqual(b.parse_days("T"), ["tue", "thu"])
        self.assertEqual(b.parse_days("TH"), ["thu"])

    def test_accepts_lists(self):
        self.assertEqual(b.parse_days(["Mon", "Fri"]), ["mon", "fri"])

    def test_returns_none_for_empty(self):
        self.assertIsNone(b.parse_days(None))
        self.assertIsNone(b.parse_days(""))


class ScalarHelpersTest(unittest.TestCase):
    def test_parse_hours_to_minutes(self):
        self.assertEqual(b.parse_hours_to_minutes("2"), 120)
        self.assertIsNone(b.parse_hours_to_minutes(None))
        self.assertIsNone(b.parse_hours_to_minutes("abc"))

    def test_as_int(self):
        self.assertEqual(b.as_int("5"), 5)
        self.assertEqual(b.as_int("3.7"), 3)
        self.assertIsNone(b.as_int(None))
        self.assertIsNone(b.as_int(""))
        self.assertIsNone(b.as_int("x"))

    def test_clean_collapses_whitespace(self):
        self.assertEqual(b.clean("  hello   world "), "hello world")
        self.assertIsNone(b.clean(None))
        self.assertIsNone(b.clean("   "))

    def test_normalize_street(self):
        self.assertEqual(b.normalize_street("N. Point St."), "n point st")
        self.assertEqual(b.normalize_street(None), "")


class GeometryHelpersTest(unittest.TestCase):
    def test_flatten_coords_by_type(self):
        self.assertEqual(
            b.flatten_coords({"type": "LineString", "coordinates": [[1, 2], [3, 4]]}),
            [[1, 2], [3, 4]],
        )
        self.assertEqual(
            b.flatten_coords({"type": "MultiLineString", "coordinates": [[[1, 2], [3, 4]], [[5, 6]]]}),
            [[1, 2], [3, 4], [5, 6]],
        )
        self.assertEqual(b.flatten_coords({"type": "Point", "coordinates": [1, 2]}), [[1, 2]])
        self.assertEqual(b.flatten_coords({}), [])

    def test_centroid_returns_lat_lng(self):
        lat, lng = b.centroid([[0.0, 0.0], [2.0, 4.0]])
        self.assertAlmostEqual(lat, 2.0)
        self.assertAlmostEqual(lng, 1.0)

    def test_distance_miles_is_zero_for_identical_points(self):
        self.assertAlmostEqual(b.distance_miles(37.77, -122.41, 37.77, -122.41), 0.0)


if __name__ == "__main__":
    unittest.main()
