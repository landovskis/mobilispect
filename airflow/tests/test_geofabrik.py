"""Tests for airflow/pipeline/geofabrik.py"""

import os
from unittest.mock import MagicMock

import pytest

from pipeline import geofabrik as gf

# ---------------------------------------------------------------------------
# Sample Geofabrik index features (bbox format: [min_lon, min_lat, max_lon, max_lat])
# ---------------------------------------------------------------------------
NORTH_AMERICA = {
    "type": "Feature",
    "bbox": [-178.0, 7.0, -12.0, 84.0],
    "properties": {
        "id": "north-america",
        "urls": {"pbf": "https://download.geofabrik.de/north-america-latest.osm.pbf"},
    },
    "geometry": {"type": "Polygon", "coordinates": []},
}
CANADA = {
    "type": "Feature",
    "bbox": [-141.0, 41.7, -52.6, 83.1],
    "properties": {
        "id": "north-america/canada",
        "urls": {
            "pbf": "https://download.geofabrik.de/north-america/canada-latest.osm.pbf"
        },
    },
    "geometry": {"type": "Polygon", "coordinates": []},
}
QUEBEC = {
    "type": "Feature",
    "bbox": [-79.8, 44.9, -57.1, 62.6],
    "properties": {
        "id": "north-america/canada/quebec",
        "urls": {
            "pbf": "https://download.geofabrik.de/north-america/canada/quebec-latest.osm.pbf"
        },
    },
    "geometry": {"type": "Polygon", "coordinates": []},
}
FEATURES = [NORTH_AMERICA, CANADA, QUEBEC]


# ---------------------------------------------------------------------------
# _extract_bbox
# ---------------------------------------------------------------------------


def test_extract_bbox_uses_bbox_property():
    feature = {"bbox": [-73.9, 45.4, -73.5, 45.7]}
    assert gf._extract_bbox(feature) == (-73.9, 45.4, -73.5, 45.7)


def test_extract_bbox_from_polygon_geometry():
    feature = {
        "geometry": {
            "type": "Polygon",
            "coordinates": [
                [
                    [-73.9, 45.4],
                    [-73.5, 45.4],
                    [-73.5, 45.7],
                    [-73.9, 45.7],
                    [-73.9, 45.4],
                ]
            ],
        }
    }
    result = gf._extract_bbox(feature)
    assert result == (-73.9, 45.4, -73.5, 45.7)


def test_extract_bbox_from_multipolygon_geometry():
    feature = {
        "geometry": {
            "type": "MultiPolygon",
            "coordinates": [
                [[[-73.9, 45.4], [-73.5, 45.4], [-73.5, 45.7], [-73.9, 45.4]]],
                [[[-74.5, 45.0], [-74.0, 45.0], [-74.0, 45.3], [-74.5, 45.0]]],
            ],
        }
    }
    result = gf._extract_bbox(feature)
    assert result == (-74.5, 45.0, -73.5, 45.7)


def test_extract_bbox_none_when_no_geometry():
    feature = {"properties": {}}
    assert gf._extract_bbox(feature) is None


# ---------------------------------------------------------------------------
# _bbox_contains
# ---------------------------------------------------------------------------


def test_bbox_contains_true():
    assert gf._bbox_contains((0.0, 0.0, 10.0, 10.0), (2.0, 2.0, 8.0, 8.0)) is True


def test_bbox_contains_exact_match():
    assert gf._bbox_contains((0.0, 0.0, 10.0, 10.0), (0.0, 0.0, 10.0, 10.0)) is True


def test_bbox_contains_false_partial_overlap():
    assert gf._bbox_contains((0.0, 0.0, 5.0, 5.0), (3.0, 3.0, 8.0, 8.0)) is False


def test_bbox_contains_false_no_overlap():
    assert gf._bbox_contains((0.0, 0.0, 5.0, 5.0), (6.0, 6.0, 9.0, 9.0)) is False


# ---------------------------------------------------------------------------
# find_extract
# ---------------------------------------------------------------------------


def test_find_extract_picks_smallest_containing():
    """Montreal bbox sits inside Quebec — should prefer Quebec over Canada."""
    result = gf.find_extract(FEATURES, min_lat=45.4, min_lon=-73.9, max_lat=45.7, max_lon=-73.5)
    assert result["properties"]["id"] == "north-america/canada/quebec"


def test_find_extract_falls_back_to_larger_when_small_doesnt_cover():
    """Bbox spanning multiple provinces should fall back to Canada extract."""
    result = gf.find_extract(FEATURES, min_lat=43.0, min_lon=-140.0, max_lat=70.0, max_lon=-55.0)
    assert result["properties"]["id"] == "north-america/canada"


def test_find_extract_raises_when_no_match():
    """Bbox entirely outside all extracts should raise ValueError."""
    with pytest.raises(ValueError, match="No Geofabrik extract covers"):
        gf.find_extract(FEATURES, min_lat=-80.0, min_lon=10.0, max_lat=-70.0, max_lon=20.0)


def test_find_extract_ignores_features_without_pbf_url():
    """Features without a pbf URL should be skipped."""
    no_pbf = {
        "type": "Feature",
        "bbox": [-79.8, 44.9, -57.1, 62.6],
        "properties": {"id": "no-pbf", "urls": {}},
        "geometry": {"type": "Polygon", "coordinates": []},
    }
    # no_pbf covers Montreal but has no PBF URL — should fall back to CANADA
    result = gf.find_extract(
        [no_pbf, CANADA, NORTH_AMERICA],
        min_lat=45.4,
        min_lon=-73.9,
        max_lat=45.7,
        max_lon=-73.5,
    )
    assert result["properties"]["id"] == "north-america/canada"


# ---------------------------------------------------------------------------
# fetch_index
# ---------------------------------------------------------------------------


def test_fetch_index_returns_features():
    session = MagicMock()
    session.get.return_value.json.return_value = {"features": FEATURES}
    result = gf.fetch_index(session)
    assert result == FEATURES
    session.get.assert_called_once_with(gf.GEOFABRIK_INDEX_URL, timeout=30)


def test_fetch_index_raises_on_http_error():
    session = MagicMock()
    session.get.return_value.raise_for_status.side_effect = Exception("HTTP 503")
    with pytest.raises(Exception, match="HTTP 503"):
        gf.fetch_index(session)


# ---------------------------------------------------------------------------
# download_pbf
# ---------------------------------------------------------------------------


def test_download_pbf_skips_when_file_current(tmp_path):
    """Skip download if local file size matches Content-Length."""
    pbf = tmp_path / "quebec-latest.osm.pbf"
    pbf.write_bytes(b"x" * 100)

    feature = {
        "properties": {
            "urls": {"pbf": "https://download.geofabrik.de/north-america/canada/quebec-latest.osm.pbf"}
        }
    }
    head_resp = MagicMock()
    head_resp.ok = True
    head_resp.headers = {"Content-Length": "100"}

    session = MagicMock()
    session.head.return_value = head_resp

    result = gf.download_pbf(feature, str(tmp_path), session)

    assert result == str(pbf)
    session.get.assert_not_called()


def test_download_pbf_downloads_when_size_differs(tmp_path):
    """Re-download when local file size differs from Content-Length."""
    pbf = tmp_path / "quebec-latest.osm.pbf"
    pbf.write_bytes(b"x" * 50)  # stale

    feature = {
        "properties": {
            "urls": {"pbf": "https://download.geofabrik.de/north-america/canada/quebec-latest.osm.pbf"}
        }
    }
    head_resp = MagicMock()
    head_resp.ok = True
    head_resp.headers = {"Content-Length": "100"}

    get_resp = MagicMock()
    get_resp.raise_for_status = MagicMock()
    get_resp.iter_content.return_value = [b"y" * 100]
    get_resp.__enter__ = lambda s: s
    get_resp.__exit__ = MagicMock(return_value=False)

    session = MagicMock()
    session.head.return_value = head_resp
    session.get.return_value = get_resp

    result = gf.download_pbf(feature, str(tmp_path), session)

    session.get.assert_called_once()
    assert result == str(pbf)


def test_download_pbf_downloads_when_file_missing(tmp_path):
    """Download when no local file exists yet."""
    feature = {
        "properties": {
            "urls": {"pbf": "https://download.geofabrik.de/north-america/canada/quebec-latest.osm.pbf"}
        }
    }
    head_resp = MagicMock()
    head_resp.ok = True
    head_resp.headers = {"Content-Length": "50"}

    get_resp = MagicMock()
    get_resp.raise_for_status = MagicMock()
    get_resp.iter_content.return_value = [b"z" * 50]
    get_resp.__enter__ = lambda s: s
    get_resp.__exit__ = MagicMock(return_value=False)

    session = MagicMock()
    session.head.return_value = head_resp
    session.get.return_value = get_resp

    result = gf.download_pbf(feature, str(tmp_path), session)

    session.get.assert_called_once()
    assert os.path.basename(result) == "quebec-latest.osm.pbf"
