"""Geofabrik OSM data download utilities.

Downloads PBF extracts from Geofabrik based on a geographic bounding box.
Uses the Geofabrik index-v1.json to find the smallest extract that fully
covers the requested bbox, then streams the PBF to a local destination.

Skips the download entirely when the local file already matches the
expected Content-Length reported by a HEAD request.
"""

import logging
import os
from typing import List, Optional, Tuple

import requests

log = logging.getLogger(__name__)

GEOFABRIK_INDEX_URL = "https://download.geofabrik.de/index-v1.json"


def _extract_bbox(feature: dict) -> Optional[Tuple[float, float, float, float]]:
    """Return (min_lon, min_lat, max_lon, max_lat) for a GeoJSON feature.

    Prefers the top-level ``bbox`` property when present; otherwise
    computes it from the feature's Polygon or MultiPolygon geometry.
    Returns None when neither is available.
    """
    if "bbox" in feature:
        bb = feature["bbox"]
        return (bb[0], bb[1], bb[2], bb[3])

    geom = feature.get("geometry")
    if not geom:
        return None

    coords: list = []
    geom_type = geom["type"]
    if geom_type == "Polygon":
        for ring in geom["coordinates"]:
            coords.extend(ring)
    elif geom_type == "MultiPolygon":
        for poly in geom["coordinates"]:
            for ring in poly:
                coords.extend(ring)
    else:
        return None

    if not coords:
        return None

    lons = [c[0] for c in coords]
    lats = [c[1] for c in coords]
    return (min(lons), min(lats), max(lons), max(lats))


def _bbox_contains(
    outer: Tuple[float, float, float, float],
    inner: Tuple[float, float, float, float],
) -> bool:
    """Return True when *outer* fully contains *inner*.

    Bboxes are (min_lon, min_lat, max_lon, max_lat).
    """
    return (
        outer[0] <= inner[0]
        and outer[1] <= inner[1]
        and outer[2] >= inner[2]
        and outer[3] >= inner[3]
    )


def _bbox_area(bbox: Tuple[float, float, float, float]) -> float:
    """Return the lon×lat area of a bbox (used to prefer smaller extracts)."""
    return (bbox[2] - bbox[0]) * (bbox[3] - bbox[1])


def fetch_index(session: requests.Session) -> List[dict]:
    """Fetch the Geofabrik extract index and return its feature list."""
    resp = session.get(GEOFABRIK_INDEX_URL, timeout=30)
    resp.raise_for_status()
    return resp.json()["features"]


def find_extract(
    features: List[dict],
    *,
    min_lat: float,
    min_lon: float,
    max_lat: float,
    max_lon: float,
) -> dict:
    """Return the smallest Geofabrik extract that fully covers the given bbox.

    Iterates over all features, filters to those whose bbox contains the
    requested feed bbox and that have a PBF download URL, then picks the
    one with the smallest area (most geographically specific).

    Raises:
        ValueError: when no extract covers the requested bbox.
    """
    feed_bbox = (min_lon, min_lat, max_lon, max_lat)

    candidates = []
    for feature in features:
        pbf_url = feature.get("properties", {}).get("urls", {}).get("pbf")
        if not pbf_url:
            continue
        extract_bbox = _extract_bbox(feature)
        if extract_bbox is None:
            continue
        if _bbox_contains(extract_bbox, feed_bbox):
            candidates.append((feature, _bbox_area(extract_bbox)))

    if not candidates:
        raise ValueError(
            f"No Geofabrik extract covers bbox "
            f"({min_lat:.4f},{min_lon:.4f})–({max_lat:.4f},{max_lon:.4f}). "
            "Check that the feed has valid stop coordinates."
        )

    candidates.sort(key=lambda x: x[1])
    chosen = candidates[0][0]
    log.info(
        "Selected Geofabrik extract '%s' (area=%.2f) for feed bbox",
        chosen["properties"].get("id", "?"),
        candidates[0][1],
    )
    return chosen


def download_pbf(
    feature: dict,
    dest_dir: str,
    session: requests.Session,
) -> str:
    """Download the PBF file for a Geofabrik extract to *dest_dir*.

    Performs a HEAD request first; if the local file already exists and
    matches the reported Content-Length the download is skipped.

    Returns the absolute path to the (possibly cached) PBF file.
    """
    pbf_url = feature["properties"]["urls"]["pbf"]
    filename = pbf_url.rsplit("/", 1)[-1]
    local_path = os.path.join(dest_dir, filename)

    head = session.head(pbf_url, timeout=30, allow_redirects=True)
    if head.ok:
        expected = int(head.headers.get("Content-Length", -1))
        if expected > 0 and os.path.exists(local_path):
            if os.path.getsize(local_path) == expected:
                log.info(
                    "PBF already up to date: %s (%d bytes) — skipping download",
                    local_path,
                    expected,
                )
                return local_path

    log.info("Downloading PBF: %s → %s", pbf_url, local_path)
    with session.get(pbf_url, stream=True, timeout=3600) as resp:
        resp.raise_for_status()
        with open(local_path, "wb") as fh:
            for chunk in resp.iter_content(chunk_size=8 * 1024 * 1024):
                fh.write(chunk)

    log.info("PBF download complete: %s", local_path)
    return local_path
