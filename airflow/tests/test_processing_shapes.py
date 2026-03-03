"""Unit tests for shape-related logic in pipeline.processing."""

import sys
import os
import unittest
from unittest.mock import MagicMock, call, patch

import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pipeline.gtfs import ParsedGTFS
from pipeline import processing


def _make_parsed(shapes_data=None):
    """Build a minimal ParsedGTFS with optional shapes."""
    shapes_df = pd.DataFrame(shapes_data) if shapes_data else pd.DataFrame()
    return ParsedGTFS(
        agencies=pd.DataFrame(),
        routes=pd.DataFrame(),
        stops=pd.DataFrame(),
        trips=pd.DataFrame(),
        stop_times=pd.DataFrame(),
        shapes=shapes_df,
    )


class TestVariantShapeIdTracking(unittest.TestCase):
    """Verify that persist_route_variants captures shape_id from trips.txt."""

    def _make_trips_df(self, shape_id="shape-1"):
        return pd.DataFrame([{
            "trip_id": "t1",
            "route_id": "r1",
            "direction_id": "0",
            "trip_headsign": "Downtown",
            "shape_id": shape_id,
        }])

    def _make_stop_times_df(self):
        return pd.DataFrame([
            {"trip_id": "t1", "stop_id": "s1", "stop_sequence": "1"},
            {"trip_id": "t1", "stop_id": "s2", "stop_sequence": "2"},
        ])

    @patch("pipeline.processing.get_engine")
    def test_shape_id_is_captured_in_variant_record(self, mock_get_engine):
        """shape_id from trips.txt should appear in the returned variant dict."""
        # Arrange: mock DB so no actual inserts happen
        mock_conn = MagicMock()
        mock_conn.__enter__ = MagicMock(return_value=mock_conn)
        mock_conn.__exit__ = MagicMock(return_value=False)
        mock_engine = MagicMock()
        mock_engine.begin.return_value = mock_conn
        mock_get_engine.return_value = mock_engine

        parsed = _make_parsed()
        parsed = ParsedGTFS(
            agencies=pd.DataFrame(),
            routes=pd.DataFrame(),
            stops=pd.DataFrame(),
            trips=self._make_trips_df("shape-42"),
            stop_times=self._make_stop_times_df(),
            shapes=pd.DataFrame(),
        )
        route_map = {("r1", "feed1-default"): "r-feed1-default_r1"}
        route_map_by_gtfs = {"r1": "r-feed1-default_r1"}
        stop_lookup = {
            "s1": {"name": "Stop 1", "latitude": 1.0, "longitude": 1.0},
            "s2": {"name": "Stop 2", "latitude": 2.0, "longitude": 2.0},
        }

        variants = processing.persist_route_variants(
            parsed, "feed1", route_map, route_map_by_gtfs, stop_lookup
        )

        self.assertEqual(len(variants), 1)
        self.assertEqual(variants[0]["shape_id"], "shape-42")

    @patch("pipeline.processing.get_engine")
    def test_missing_shape_id_stored_as_none(self, mock_get_engine):
        """When trips.txt omits shape_id the variant record has shape_id=None."""
        mock_conn = MagicMock()
        mock_conn.__enter__ = MagicMock(return_value=mock_conn)
        mock_conn.__exit__ = MagicMock(return_value=False)
        mock_engine = MagicMock()
        mock_engine.begin.return_value = mock_conn
        mock_get_engine.return_value = mock_engine

        trips_no_shape = pd.DataFrame([{
            "trip_id": "t1",
            "route_id": "r1",
            "direction_id": "0",
            "trip_headsign": "Uptown",
            # shape_id deliberately absent
        }])
        parsed = ParsedGTFS(
            agencies=pd.DataFrame(),
            routes=pd.DataFrame(),
            stops=pd.DataFrame(),
            trips=trips_no_shape,
            stop_times=pd.DataFrame([
                {"trip_id": "t1", "stop_id": "s1", "stop_sequence": "1"},
                {"trip_id": "t1", "stop_id": "s2", "stop_sequence": "2"},
            ]),
            shapes=pd.DataFrame(),
        )
        route_map = {("r1", "feed1-default"): "r-feed1-default_r1"}
        route_map_by_gtfs = {"r1": "r-feed1-default_r1"}
        stop_lookup = {
            "s1": {"name": "Stop 1", "latitude": 1.0, "longitude": 1.0},
            "s2": {"name": "Stop 2", "latitude": 2.0, "longitude": 2.0},
        }

        variants = processing.persist_route_variants(
            parsed, "feed1", route_map, route_map_by_gtfs, stop_lookup
        )

        self.assertEqual(len(variants), 1)
        self.assertIsNone(variants[0]["shape_id"])


class TestMatchAndPersistShapes(unittest.TestCase):
    """Verify the match_and_persist_shapes orchestration logic."""

    def _make_shapes_df(self, shape_id="s1"):
        return pd.DataFrame([
            {"shape_id": shape_id, "shape_pt_lat": "1.0", "shape_pt_lon": "2.0", "shape_pt_sequence": "0"},
            {"shape_id": shape_id, "shape_pt_lat": "3.0", "shape_pt_lon": "4.0", "shape_pt_sequence": "1"},
        ])

    def test_no_op_on_empty_variant_shape_map(self):
        parsed = _make_parsed({"shape_id": ["s1"], "shape_pt_lat": ["1.0"],
                                "shape_pt_lon": ["2.0"], "shape_pt_sequence": ["0"]})
        with patch("pipeline.processing.get_engine") as mock_engine, \
             patch("pipeline.processing._otp.match_shape") as mock_match:
            processing.match_and_persist_shapes(parsed, {}, "http://otp")
        mock_match.assert_not_called()
        mock_engine.assert_not_called()

    def test_no_op_on_empty_shapes(self):
        parsed = _make_parsed()  # empty shapes
        with patch("pipeline.processing.get_engine") as mock_engine, \
             patch("pipeline.processing._otp.match_shape") as mock_match:
            processing.match_and_persist_shapes(parsed, {"v1": "s1"}, "http://otp")
        mock_match.assert_not_called()
        mock_engine.assert_not_called()

    def test_skips_variant_with_none_shape_id(self):
        parsed = _make_parsed({
            "shape_id": ["s1"], "shape_pt_lat": ["1.0"],
            "shape_pt_lon": ["2.0"], "shape_pt_sequence": ["0"],
        })
        with patch("pipeline.processing.get_engine") as mock_engine, \
             patch("pipeline.processing._otp.match_shape") as mock_match:
            processing.match_and_persist_shapes(parsed, {"v1": None}, "http://otp")
        mock_match.assert_not_called()
        mock_engine.assert_not_called()

    @patch("pipeline.processing.get_engine")
    @patch("pipeline.processing._otp.match_shape")
    def test_calls_otp_once_per_unique_shape(self, mock_match, mock_get_engine):
        """Two variants sharing a shape_id should trigger only one OTP call."""
        mock_match.return_value = [
            {"matched_lat": 1.1, "matched_lon": 2.1, "street_name": "High St"},
            {"matched_lat": 3.1, "matched_lon": 4.1, "street_name": "Low St"},
        ]
        mock_conn = MagicMock()
        mock_conn.__enter__ = MagicMock(return_value=mock_conn)
        mock_conn.__exit__ = MagicMock(return_value=False)
        mock_engine = MagicMock()
        mock_engine.begin.return_value = mock_conn
        mock_get_engine.return_value = mock_engine

        shapes_df = self._make_shapes_df("s1")
        parsed = ParsedGTFS(
            agencies=pd.DataFrame(), routes=pd.DataFrame(), stops=pd.DataFrame(),
            trips=pd.DataFrame(), stop_times=pd.DataFrame(), shapes=shapes_df,
        )

        # Two variants, same shape_id
        processing.match_and_persist_shapes(parsed, {"v1": "s1", "v2": "s1"}, "http://otp")

        # OTP called only once despite two variants
        mock_match.assert_called_once()
        raw_points = mock_match.call_args[0][1]
        self.assertEqual(len(raw_points), 2)
        self.assertAlmostEqual(raw_points[0]["lat"], 1.0)

    @patch("pipeline.processing.get_engine")
    @patch("pipeline.processing._otp.match_shape")
    def test_persists_correct_row_count(self, mock_match, mock_get_engine):
        """Number of inserted rows equals number of shape points."""
        mock_match.return_value = [
            {"matched_lat": 1.1, "matched_lon": 2.1, "street_name": "A St"},
            {"matched_lat": 3.1, "matched_lon": 4.1, "street_name": "B St"},
        ]
        inserted_rows = []

        inserted_rows = []

        def capture_execute(stmt, rows=None):
            if rows is not None:
                inserted_rows.extend(rows)

        mock_conn = MagicMock()
        mock_conn.__enter__ = MagicMock(return_value=mock_conn)
        mock_conn.__exit__ = MagicMock(return_value=False)
        mock_conn.execute = capture_execute
        mock_engine = MagicMock()
        mock_engine.begin.return_value = mock_conn
        mock_get_engine.return_value = mock_engine

        shapes_df = self._make_shapes_df("s1")
        parsed = ParsedGTFS(
            agencies=pd.DataFrame(), routes=pd.DataFrame(), stops=pd.DataFrame(),
            trips=pd.DataFrame(), stop_times=pd.DataFrame(), shapes=shapes_df,
        )

        processing.match_and_persist_shapes(parsed, {"v1": "s1"}, "http://otp")

        # All variants are persisted in a single transaction
        mock_engine.begin.assert_called_once()

        self.assertEqual(len(inserted_rows), 2)
        self.assertEqual(inserted_rows[0]["variant_id"], "v1")
        self.assertEqual(inserted_rows[0]["sequence"], 0)
        self.assertAlmostEqual(inserted_rows[0]["original_lat"], 1.0)
        self.assertAlmostEqual(inserted_rows[0]["matched_lat"], 1.1)
        self.assertEqual(inserted_rows[0]["street_name"], "A St")
        self.assertEqual(inserted_rows[1]["sequence"], 1)

    def test_missing_shapes_columns_is_skipped_gracefully(self):
        """If shapes.txt lacks required columns, the function skips without error."""
        shapes_df = pd.DataFrame([{"shape_id": "s1"}])  # missing other columns
        parsed = ParsedGTFS(
            agencies=pd.DataFrame(), routes=pd.DataFrame(), stops=pd.DataFrame(),
            trips=pd.DataFrame(), stop_times=pd.DataFrame(), shapes=shapes_df,
        )
        with patch("pipeline.processing.get_engine") as mock_engine, \
             patch("pipeline.processing._otp.match_shape") as mock_match:
            # Should not raise
            processing.match_and_persist_shapes(parsed, {"v1": "s1"}, "http://otp")
        mock_match.assert_not_called()
        mock_engine.assert_not_called()


if __name__ == "__main__":
    unittest.main()
