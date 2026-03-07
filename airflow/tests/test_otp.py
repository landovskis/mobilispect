"""Unit tests for pipeline.otp — OTP2 map-matching client."""

import sys
import os
import unittest
from unittest.mock import MagicMock, patch

# Ensure the airflow root is on the path so `pipeline` can be imported directly.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pipeline.otp import (
    CHUNK_SIZE,
    _extract_street_name,
    _parse_plan_response,
    match_shape,
)


class TestExtractStreetName(unittest.TestCase):
    def test_returns_first_element_from_list(self):
        leg = {"streetNames": ["Main St", "Broadway"]}
        self.assertEqual(_extract_street_name(leg), "Main St")

    def test_returns_first_key_from_dict(self):
        leg = {"streetNames": {"Elm Ave": 3, "Oak Rd": 1}}
        result = _extract_street_name(leg)
        self.assertIn(result, ("Elm Ave", "Oak Rd"))

    def test_skips_trivial_names_in_list(self):
        leg = {"streetNames": ["Origin", "Destination", "Real St"]}
        self.assertEqual(_extract_street_name(leg), "Real St")

    def test_falls_back_to_walk_steps(self):
        leg = {"streetNames": [], "walkSteps": [{"streetName": "Birch Blvd"}]}
        self.assertEqual(_extract_street_name(leg), "Birch Blvd")

    def test_returns_none_when_no_name(self):
        leg = {"streetNames": []}
        self.assertIsNone(_extract_street_name(leg))

    def test_returns_none_for_empty_leg(self):
        self.assertIsNone(_extract_street_name({}))

    def test_skips_empty_string(self):
        leg = {"streetNames": ["", "Cedar Lane"]}
        self.assertEqual(_extract_street_name(leg), "Cedar Lane")


class TestParsePlanResponse(unittest.TestCase):
    def _make_leg(self, from_lat, from_lon, to_lat, to_lon, street="Test St"):
        return {
            "from": {"lat": from_lat, "lon": from_lon},
            "to": {"lat": to_lat, "lon": to_lon},
            "streetNames": [street],
        }

    def test_two_points_single_leg(self):
        points = [{"lat": 1.0, "lon": 2.0}, {"lat": 3.0, "lon": 4.0}]
        data = {
            "plan": {
                "itineraries": [{
                    "legs": [self._make_leg(1.1, 2.1, 3.1, 4.1, "King St")]
                }]
            }
        }
        result = _parse_plan_response(data, points)
        self.assertEqual(len(result), 2)
        self.assertAlmostEqual(result[0]["matched_lat"], 1.1)
        self.assertAlmostEqual(result[0]["matched_lon"], 2.1)
        self.assertEqual(result[0]["street_name"], "King St")
        self.assertAlmostEqual(result[1]["matched_lat"], 3.1)
        self.assertAlmostEqual(result[1]["matched_lon"], 4.1)
        self.assertEqual(result[1]["street_name"], "King St")

    def test_three_points_two_legs(self):
        points = [{"lat": 0.0, "lon": 0.0}, {"lat": 1.0, "lon": 1.0}, {"lat": 2.0, "lon": 2.0}]
        data = {
            "plan": {
                "itineraries": [{
                    "legs": [
                        self._make_leg(0.1, 0.1, 1.1, 1.1, "Street A"),
                        self._make_leg(1.1, 1.1, 2.1, 2.1, "Street B"),
                    ]
                }]
            }
        }
        result = _parse_plan_response(data, points)
        self.assertEqual(len(result), 3)
        # point[0] → legs[0].from
        self.assertAlmostEqual(result[0]["matched_lat"], 0.1)
        self.assertEqual(result[0]["street_name"], "Street A")
        # point[1] → legs[0].to
        self.assertAlmostEqual(result[1]["matched_lat"], 1.1)
        self.assertEqual(result[1]["street_name"], "Street A")
        # point[2] → legs[-1].to
        self.assertAlmostEqual(result[2]["matched_lat"], 2.1)
        self.assertEqual(result[2]["street_name"], "Street B")

    def test_falls_back_on_empty_itineraries(self):
        points = [{"lat": 1.0, "lon": 2.0}, {"lat": 3.0, "lon": 4.0}]
        data = {"plan": {"itineraries": []}}
        result = _parse_plan_response(data, points)
        self.assertEqual(result[0]["matched_lat"], 1.0)
        self.assertIsNone(result[0]["street_name"])

    def test_falls_back_on_missing_plan(self):
        points = [{"lat": 5.0, "lon": 6.0}]
        result = _parse_plan_response({}, points)
        self.assertEqual(result[0]["matched_lat"], 5.0)
        self.assertIsNone(result[0]["street_name"])

    def test_falls_back_on_empty_legs(self):
        points = [{"lat": 1.0, "lon": 2.0}, {"lat": 3.0, "lon": 4.0}]
        data = {"plan": {"itineraries": [{"legs": []}]}}
        result = _parse_plan_response(data, points)
        self.assertEqual(result[0]["matched_lat"], 1.0)
        self.assertIsNone(result[0]["street_name"])


class TestMatchShape(unittest.TestCase):
    def test_empty_returns_empty(self):
        self.assertEqual(match_shape("http://otp", []), [])

    def test_single_point_no_otp_call(self):
        with patch("pipeline.otp.requests.get") as mock_get:
            result = match_shape("http://otp", [{"lat": 1.0, "lon": 2.0}])
        mock_get.assert_not_called()
        self.assertEqual(len(result), 1)
        self.assertAlmostEqual(result[0]["matched_lat"], 1.0)

    def test_fallback_on_http_error(self):
        import requests as req_lib
        with patch("pipeline.otp.requests.get") as mock_get:
            mock_get.side_effect = req_lib.exceptions.ConnectionError("refused")
            points = [{"lat": 1.0, "lon": 2.0}, {"lat": 3.0, "lon": 4.0}]
            result = match_shape("http://otp:8080", points)

        self.assertEqual(len(result), 2)
        # Falls back to raw coords
        self.assertAlmostEqual(result[0]["matched_lat"], 1.0)
        self.assertAlmostEqual(result[1]["matched_lat"], 3.0)
        self.assertIsNone(result[0]["street_name"])

    def test_returns_matched_coords_on_success(self):
        matched_response = {
            "plan": {
                "itineraries": [{
                    "legs": [{
                        "from": {"lat": 1.11, "lon": 2.11},
                        "to": {"lat": 3.11, "lon": 4.11},
                        "streetNames": ["Maple Ave"],
                    }]
                }]
            }
        }
        mock_resp = MagicMock()
        mock_resp.json.return_value = matched_response
        mock_resp.raise_for_status.return_value = None

        with patch("pipeline.otp.requests.get", return_value=mock_resp):
            points = [{"lat": 1.0, "lon": 2.0}, {"lat": 3.0, "lon": 4.0}]
            result = match_shape("http://otp:8080", points)

        self.assertEqual(len(result), 2)
        self.assertAlmostEqual(result[0]["matched_lat"], 1.11)
        self.assertEqual(result[0]["street_name"], "Maple Ave")
        self.assertAlmostEqual(result[1]["matched_lat"], 3.11)

    def test_chunks_large_shape(self):
        """Shapes larger than CHUNK_SIZE trigger multiple OTP calls."""
        total_points = CHUNK_SIZE + 5
        points = [{"lat": float(i), "lon": float(i)} for i in range(total_points)]

        call_count = 0

        def fake_get(url, params=None, timeout=None):
            nonlocal call_count
            call_count += 1
            # Return a minimal valid response
            resp = MagicMock()
            resp.raise_for_status.return_value = None
            resp.json.return_value = {"plan": {"itineraries": []}}
            return resp

        with patch("pipeline.otp.requests.get", side_effect=fake_get):
            result = match_shape("http://otp:8080", points)

        self.assertEqual(len(result), total_points)
        # Expect exactly 2 calls: first chunk (CHUNK_SIZE) + remainder
        self.assertEqual(call_count, 2)

    def test_partial_chunk_failure_does_not_affect_other_chunks(self):
        """A failure in one chunk falls back to raw; other chunks succeed."""
        import requests as req_lib

        total_points = CHUNK_SIZE + 2
        points = [{"lat": float(i), "lon": float(i)} for i in range(total_points)]

        call_number = 0

        def fake_get(url, params=None, timeout=None):
            nonlocal call_number
            call_number += 1
            if call_number == 1:
                # First chunk raises
                raise req_lib.exceptions.ConnectionError("first chunk fails")
            # Second chunk succeeds with raw fallback data
            resp = MagicMock()
            resp.raise_for_status.return_value = None
            resp.json.return_value = {"plan": {"itineraries": []}}
            return resp

        with patch("pipeline.otp.requests.get", side_effect=fake_get):
            result = match_shape("http://otp:8080", points)

        self.assertEqual(len(result), total_points)
        # First chunk falls back to raw
        self.assertAlmostEqual(result[0]["matched_lat"], 0.0)
        self.assertIsNone(result[0]["street_name"])


if __name__ == "__main__":
    unittest.main()
