"""OTP2 (OpenTripPlanner 2) HTTP client for GTFS shape map-matching.

Uses the OTP2 /otp/routers/default/plan endpoint with WALK mode to snap raw
shape-point coordinates to the road network and retrieve street names.

On any OTP error (network failure, HTTP error, malformed response) the functions
fall back gracefully to the original coordinates with street_name=None, so the
pipeline never fails due to OTP unavailability.
"""

import logging
import os
from typing import Dict, List, Optional

import requests

log = logging.getLogger(__name__)

OTP_URL_ENV = "MOBILISPECT_OTP_URL"
DEFAULT_OTP_URL = "http://otp:8080"

# Maximum points per OTP /plan call (from + intermediates + to).
# OTP2 accepts up to ~50 intermediatePlaces before URL length limits bite.
CHUNK_SIZE = 50


def get_otp_url() -> str:
    return os.environ.get(OTP_URL_ENV, DEFAULT_OTP_URL)


def _extract_street_name(leg: Dict) -> Optional[str]:
    """Extract the primary street name from an OTP2 plan leg.

    OTP2 serialises street names in several ways depending on leg type; this
    function tries each in order and returns the first non-trivial value.
    """
    # OTP2 REST: streetNames is a JSON array of strings for WALK legs
    street_names = leg.get("streetNames")
    if isinstance(street_names, list):
        for name in street_names:
            if name and name not in ("", "Origin", "Destination"):
                return str(name)
    # Some versions serialise as a dict {name: count}
    if isinstance(street_names, dict):
        for name in street_names:
            if name and name not in ("", "Origin", "Destination"):
                return str(name)
    # Fall back to individual walk steps
    for step in leg.get("walkSteps") or leg.get("steps") or []:
        name = step.get("streetName") or step.get("name")
        if name and name not in ("", "Origin", "Destination"):
            return str(name)
    return None


def _parse_plan_response(data: Dict, original_points: List[Dict]) -> List[Dict]:
    """Map OTP2 plan itinerary legs back to per-point matched positions.

    For N input points OTP returns N-1 legs when all intermediate places are
    used.  The coordinate mapping is:
      point[0]   → legs[0].from
      point[i]   → legs[i-1].to   (1 ≤ i ≤ N-2)
      point[N-1] → legs[N-2].to

    Street name for each point is taken from the leg that *arrives at* that
    point (legs[i-1] for point[i], legs[0] for point[0], legs[-1] for the
    last point).  This means a point is labelled with the street it was
    approached from — consistent but one segment "behind" the outbound street.
    """
    n = len(original_points)
    fallback = [
        {"matched_lat": p["lat"], "matched_lon": p["lon"], "street_name": None}
        for p in original_points
    ]

    itineraries = data.get("plan", {}).get("itineraries", [])
    if not itineraries:
        return fallback

    legs = itineraries[0].get("legs", [])
    if not legs:
        return fallback

    results: List[Dict] = []
    for i, point in enumerate(original_points):
        if i == 0:
            leg = legs[0]
            matched_lat = leg.get("from", {}).get("lat", point["lat"])
            matched_lon = leg.get("from", {}).get("lon", point["lon"])
            street_name = _extract_street_name(leg)
        elif i < n - 1:
            leg_idx = i - 1
            if leg_idx < len(legs):
                leg = legs[leg_idx]
                matched_lat = leg.get("to", {}).get("lat", point["lat"])
                matched_lon = leg.get("to", {}).get("lon", point["lon"])
                street_name = _extract_street_name(leg)
            else:
                matched_lat = point["lat"]
                matched_lon = point["lon"]
                street_name = None
        else:
            leg = legs[-1]
            matched_lat = leg.get("to", {}).get("lat", point["lat"])
            matched_lon = leg.get("to", {}).get("lon", point["lon"])
            street_name = _extract_street_name(leg)

        results.append({
            "matched_lat": matched_lat,
            "matched_lon": matched_lon,
            "street_name": street_name,
        })

    return results


def _match_chunk(otp_url: str, points: List[Dict]) -> List[Dict]:
    """Send one chunk of points to OTP2 /plan and return matched results.

    Raises requests.RequestException or ValueError on failure; callers are
    expected to catch and fall back to raw coordinates.
    """
    if len(points) == 1:
        p = points[0]
        return [{"matched_lat": p["lat"], "matched_lon": p["lon"], "street_name": None}]

    from_place = f"{points[0]['lat']},{points[0]['lon']}"
    to_place = f"{points[-1]['lat']},{points[-1]['lon']}"

    params: Dict = {
        "fromPlace": from_place,
        "toPlace": to_place,
        "mode": "WALK",
        "numItineraries": "1",
    }
    if len(points) > 2:
        params["intermediatePlaces"] = "|".join(
            f"{p['lat']},{p['lon']}" for p in points[1:-1]
        )

    response = requests.get(
        f"{otp_url}/otp/routers/default/plan",
        params=params,
        timeout=30,
    )
    response.raise_for_status()
    data = response.json()
    return _parse_plan_response(data, points)


def match_shape(otp_url: str, raw_points: List[Dict]) -> List[Dict]:
    """Match a full list of shape points via OTP2, chunking as needed.

    Each element of raw_points must be ``{"lat": float, "lon": float}``.
    Returns a parallel list of ``{"matched_lat", "matched_lon", "street_name"}``.
    On any per-chunk OTP error the chunk falls back to original coordinates
    with street_name=None; the rest of the shape is unaffected.
    """
    if not raw_points:
        return []

    results: List[Dict] = []
    for chunk_start in range(0, len(raw_points), CHUNK_SIZE):
        chunk = raw_points[chunk_start:chunk_start + CHUNK_SIZE]
        try:
            chunk_results = _match_chunk(otp_url, chunk)
        except Exception as exc:
            log.warning(
                "OTP match failed for chunk starting at index %d (url=%s): %s — "
                "falling back to raw coordinates for this chunk.",
                chunk_start,
                otp_url,
                exc,
            )
            chunk_results = [
                {"matched_lat": p["lat"], "matched_lon": p["lon"], "street_name": None}
                for p in chunk
            ]
        results.extend(chunk_results)

    return results
