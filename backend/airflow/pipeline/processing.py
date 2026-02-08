import hashlib
import math
import os
import time
import unicodedata
import uuid
from collections import defaultdict
from dataclasses import dataclass
from datetime import date
from typing import Dict, Iterable, List, Optional, Tuple

import pandas as pd
import requests
from sqlalchemy import and_, delete, func, select, update
from sqlalchemy.dialects.postgresql import insert

from . import gtfs as gtfs_lib
from .db import (
    agencies,
    feed_imports,
    feed_regions,
    feeds,
    get_engine,
    metropolitan_regions,
    new_uuid,
    region_import_feeds,
    region_imports,
    route_common_sections,
    route_variant_stops,
    route_variants,
    routes,
    stop_spacing,
    stops,
    utc_now,
)


ALLOWED_ROUTE_TYPES = {
    0: "TRAM",
    1: "SUBWAY",
    2: "RAIL",
    3: "BUS",
    4: "FERRY",
    5: "CABLE_TRAM",
    6: "AERIAL_LIFT",
    7: "FUNICULAR",
    11: "TROLLEYBUS",
    12: "MONORAIL",
    100: "RAILWAY_SERVICE",
    101: "HIGH_SPEED_RAIL_SERVICE",
    102: "LONG_DISTANCE_TRAINS",
    103: "INTER_REGIONAL_RAIL_SERVICE",
    104: "CAR_TRANSPORT_RAIL_SERVICE",
    105: "SLEEPER_RAIL_SERVICE",
    106: "REGIONAL_RAIL_SERVICE",
    107: "TOURIST_RAILWAY_SERVICE",
    108: "RAIL_SHUTTLE_WITHIN_COMPLEX",
    109: "SUBURBAN_RAILWAY",
    110: "REPLACEMENT_RAIL_SERVICE",
    111: "SPECIAL_RAIL_SERVICE",
    112: "LORRY_TRANSPORT_RAIL_SERVICE",
    113: "ALL_RAIL_SERVICES",
    114: "CROSS_COUNTRY_RAIL_SERVICE",
    115: "VEHICLE_TRANSPORT_RAIL_SERVICE",
    116: "RACK_AND_PINION_RAILWAY",
    117: "ADDITIONAL_RAIL_SERVICE",
    200: "COACH_SERVICE",
    201: "INTERNATIONAL_COACH_SERVICE",
    202: "NATIONAL_COACH_SERVICE",
    203: "SHUTTLE_COACH_SERVICE",
    204: "REGIONAL_COACH_SERVICE",
    205: "SPECIAL_COACH_SERVICE",
    206: "SIGHTSEEING_COACH_SERVICE",
    207: "TOURIST_COACH_SERVICE",
    208: "COMMUTER_COACH_SERVICE",
    209: "ALL_COACH_SERVICES",
    400: "URBAN_RAILWAY_SERVICE",
    401: "METRO_SERVICE",
    402: "UNDERGROUND_SERVICE",
    403: "URBAN_RAILWAY_SERVICE_403",
    404: "ALL_URBAN_RAILWAY_SERVICES",
    405: "MONORAIL_SERVICE",
    700: "BUS_SERVICE",
    701: "REGIONAL_BUS_SERVICE",
    702: "EXPRESS_BUS_SERVICE",
    703: "STOPPING_BUS_SERVICE",
    704: "LOCAL_BUS_SERVICE",
    705: "NIGHT_BUS_SERVICE",
    706: "POST_BUS_SERVICE",
    707: "SPECIAL_NEEDS_BUS",
    708: "MOBILITY_BUS_SERVICE",
    709: "MOBILITY_BUS_FOR_REGISTERED_DISABLED",
    710: "SIGHTSEEING_BUS",
    711: "SHUTTLE_BUS",
    712: "SCHOOL_BUS",
    713: "SCHOOL_AND_PUBLIC_SERVICE_BUS",
    714: "RAIL_REPLACEMENT_BUS_SERVICE",
    715: "DEMAND_AND_RESPONSE_BUS_SERVICE",
    716: "ALL_BUS_SERVICES",
    800: "TROLLEYBUS_SERVICE",
    900: "TRAM_SERVICE",
    901: "CITY_TRAM_SERVICE",
    902: "LOCAL_TRAM_SERVICE",
    903: "REGIONAL_TRAM_SERVICE",
    904: "SIGHTSEEING_TRAM_SERVICE",
    905: "SHUTTLE_TRAM_SERVICE",
    906: "ALL_TRAM_SERVICES",
    1000: "WATER_TRANSPORT_SERVICE",
    1100: "AIR_SERVICE",
    1200: "FERRY_SERVICE",
    1300: "AERIAL_LIFT_SERVICE",
    1301: "TELECABIN_SERVICE",
    1302: "CABLE_CAR_SERVICE",
    1303: "ELEVATOR_SERVICE",
    1304: "CHAIR_LIFT_SERVICE",
    1305: "DRAG_LIFT_SERVICE",
    1306: "SMALL_TELECABIN_SERVICE",
    1307: "ALL_TELECABIN_SERVICES",
    1400: "FUNICULAR_SERVICE",
    1500: "TAXI_SERVICE",
    1501: "COMMUNAL_TAXI_SERVICE",
    1502: "WATER_TAXI_SERVICE",
    1503: "RAIL_TAXI_SERVICE",
    1504: "BIKE_TAXI_SERVICE",
    1505: "LICENSED_TAXI_SERVICE",
    1506: "PRIVATE_HIRE_SERVICE_VEHICLE",
    1507: "ALL_TAXI_SERVICES",
    1700: "MISCELLANEOUS_SERVICE",
    1702: "HORSE_DRAWN_CARRIAGE",
}

TRANSITLAND_BASE_URL = "https://transit.land/api/v2/rest"
TRANSITLAND_API_KEY_ENV = "MOBILISPECT_TRANSITLAND_API_KEY"
TRANSITLAND_API_KEY_FALLBACK_ENV = "TRANSITLAND_API_KEY"


CLASSIFICATION_THRESHOLDS = [
    ("LOCAL", 0.0, 400.0),
    ("LIMITED", 400.0, 800.0),
    ("RAPID", 800.0, 1500.0),
    ("SUBURBAN", 1500.0, 3000.0),
    ("REGIONAL", 3000.0, 5000.0),
    ("EXPRESS", 5000.0, 10000.0),
    ("REGIONAL_EXPRESS", 10000.0, None),
]


@dataclass
class FeedImportStartResult:
    status: str
    import_id: Optional[str]
    feed_id: str
    download_url: Optional[str]
    message: Optional[str] = None


def _get_transitland_api_key() -> str:
    api_key = os.environ.get(TRANSITLAND_API_KEY_ENV) or os.environ.get(
        TRANSITLAND_API_KEY_FALLBACK_ENV
    )
    if not api_key:
        raise RuntimeError(
            "Transit.land API key not set. Provide MOBILISPECT_TRANSITLAND_API_KEY or "
            "TRANSITLAND_API_KEY."
        )
    return api_key


def _normalize_text(value: Optional[str]) -> str:
    if not value:
        return ""
    normalized = unicodedata.normalize("NFKD", value)
    return "".join(ch for ch in normalized if not unicodedata.combining(ch)).lower().strip()


def _matches_region(place: Dict[str, Optional[str]], region: Dict[str, Optional[str]]) -> bool:
    region_name = _normalize_text(region.get("name"))
    place_city = _normalize_text(place.get("city_name"))
    if region_name and place_city:
        if region_name == place_city:
            return True
        if region_name in place_city or place_city in region_name:
            return True

    region_adm0 = _normalize_text(region.get("adm0_name"))
    region_adm1 = _normalize_text(region.get("adm1_name"))
    place_adm0 = _normalize_text(place.get("adm0_name"))
    place_adm1 = _normalize_text(place.get("adm1_name"))
    if region_adm0 and place_adm0 and region_adm0 == place_adm0:
        if region_adm1 and place_adm1 and region_adm1 == place_adm1:
            return True
    return False


def discover_region_feeds(region_id: str) -> Dict[str, int]:
    engine = get_engine()
    with engine.begin() as conn:
        region_row = (
            conn.execute(
                select(
                    metropolitan_regions.c.region_onestop_id,
                    metropolitan_regions.c.name,
                    metropolitan_regions.c.adm0_name,
                    metropolitan_regions.c.adm1_name,
                ).where(metropolitan_regions.c.region_onestop_id == region_id)
            )
            .mappings()
            .first()
        )

    if not region_row:
        raise RuntimeError(f"Region not found for discovery: {region_id}")

    region = {
        "region_onestop_id": region_row["region_onestop_id"],
        "name": region_row["name"],
        "adm0_name": region_row["adm0_name"],
        "adm1_name": region_row["adm1_name"],
    }

    api_key = _get_transitland_api_key()
    feed_ids: set[str] = set()
    after: Optional[int] = None

    while True:
        params = {"limit": 100}
        if after is not None:
            params["after"] = after
        response = requests.get(
            f"{TRANSITLAND_BASE_URL}/operators.json",
            params=params,
            headers={"apikey": api_key},
            timeout=30,
        )
        response.raise_for_status()
        payload = response.json()
        operators = payload.get("operators", [])
        for operator in operators:
            agencies = operator.get("agencies") or []
            matches_region = False
            for agency in agencies:
                places = agency.get("places") or []
                for place in places:
                    if _matches_region(place, region):
                        matches_region = True
                        break
                if matches_region:
                    break
            if not matches_region:
                continue
            for feed in operator.get("feeds") or []:
                if feed.get("spec") != "gtfs":
                    continue
                feed_id = feed.get("onestop_id")
                if feed_id:
                    feed_ids.add(feed_id)

        meta = payload.get("meta") or {}
        after = meta.get("after")
        if after is None:
            break
        time.sleep(0.2)

    if not feed_ids:
        return {"feeds_discovered": 0, "feeds_updated": 0}

    now = utc_now()
    feed_rows = []
    for feed_id in sorted(feed_ids):
        feed_response = requests.get(
            f"{TRANSITLAND_BASE_URL}/feeds.json",
            params={"onestop_id": feed_id, "include_alerts": "false"},
            headers={"apikey": api_key},
            timeout=30,
        )
        feed_response.raise_for_status()
        feed_payload = feed_response.json()
        feeds_payload = feed_payload.get("feeds") or []
        if not feeds_payload:
            continue
        feed_data = feeds_payload[0]
        feed_versions = feed_data.get("feed_versions") or []
        if not feed_versions:
            continue
        latest_version = feed_versions[0]
        download_url = latest_version.get("url")
        if not download_url:
            continue
        feed_rows.append(
            {
                "feed_onestop_id": feed_id,
                "download_url": download_url,
                "status": "active",
                "last_updated_at": now,
            }
        )
        time.sleep(0.1)

    with engine.begin() as conn:
        if feed_rows:
            conn.execute(
                delete(feed_regions).where(feed_regions.c.region_onestop_id == region_id)
            )
            for row in feed_rows:
                stmt = (
                    insert(feeds)
                    .values(**row)
                    .on_conflict_do_update(
                        index_elements=[feeds.c.feed_onestop_id],
                        set_={
                            "download_url": row["download_url"],
                            "status": row["status"],
                            "last_updated_at": now,
                        },
                    )
                )
                conn.execute(stmt)

            for row in feed_rows:
                stmt = (
                    insert(feed_regions)
                    .values(
                        feed_onestop_id=row["feed_onestop_id"],
                        region_onestop_id=region_id,
                    )
                    .on_conflict_do_nothing(
                        index_elements=[
                            feed_regions.c.feed_onestop_id,
                            feed_regions.c.region_onestop_id,
                        ]
                    )
                )
                conn.execute(stmt)

    return {"feeds_discovered": len(feed_ids), "feeds_updated": len(feed_rows)}


def normalize_trigger_type(value: str) -> str:
    lowered = value.strip().lower()
    if lowered in ("manual", "automatic"):
        return lowered
    return "automatic"


def start_feed_import(feed_id: str, trigger_type: str) -> FeedImportStartResult:
    engine = get_engine()
    trigger_type = normalize_trigger_type(trigger_type)
    now = utc_now()
    with engine.begin() as conn:
        feed_row = conn.execute(
            select(feeds.c.feed_onestop_id, feeds.c.download_url, feeds.c.status).where(
                feeds.c.feed_onestop_id == feed_id
            )
        ).fetchone()
        if not feed_row:
            raise RuntimeError(f"Feed not found: {feed_id}")

        existing = conn.execute(
            select(feed_imports.c.id, feed_imports.c.status)
            .where(
                and_(
                    feed_imports.c.feed_onestop_id == feed_id,
                    feed_imports.c.status.in_(["pending", "running"]),
                )
            )
            .order_by(feed_imports.c.started_at.desc())
            .limit(1)
        ).fetchone()
        if existing:
            return FeedImportStartResult(
                status="SKIPPED",
                import_id=str(existing.id),
                feed_id=feed_id,
                download_url=feed_row.download_url,
                message="Active import already running",
            )

        import_id = new_uuid()
        conn.execute(
            feed_imports.insert().values(
                id=import_id,
                feed_onestop_id=feed_id,
                trigger_type=trigger_type,
                status="running",
                started_at=now,
                created_at=now,
                updated_at=now,
            )
        )

    return FeedImportStartResult(
        status="STARTED",
        import_id=str(import_id),
        feed_id=feed_id,
        download_url=feed_row.download_url,
    )


def update_feed_import_failure(import_id: str, message: str) -> None:
    engine = get_engine()
    now = utc_now()
    with engine.begin() as conn:
        conn.execute(
            update(feed_imports)
            .where(feed_imports.c.id == uuid.UUID(import_id))
            .values(status="failed", completed_at=now, error_message=message, updated_at=now)
        )


def update_feed_import_success(import_id: str, feed_id: str) -> None:
    engine = get_engine()
    now = utc_now()
    with engine.begin() as conn:
        conn.execute(
            update(feed_imports)
            .where(feed_imports.c.id == uuid.UUID(import_id))
            .values(status="completed", completed_at=now, updated_at=now)
        )
        conn.execute(
            update(feeds)
            .where(feeds.c.feed_onestop_id == feed_id)
            .values(status="active", last_updated_at=now)
        )


def update_feed_import_file_size(import_id: str, file_size: int) -> None:
    engine = get_engine()
    now = utc_now()
    with engine.begin() as conn:
        conn.execute(
            update(feed_imports)
            .where(feed_imports.c.id == uuid.UUID(import_id))
            .values(file_size_bytes=file_size, updated_at=now)
        )


def start_region_import(region_id: str, trigger_type: str) -> Tuple[str, str]:
    engine = get_engine()
    trigger_type = normalize_trigger_type(trigger_type)
    now = utc_now()
    with engine.begin() as conn:
        existing = conn.execute(
            select(region_imports.c.id, region_imports.c.status)
            .where(
                and_(
                    region_imports.c.region_onestop_id == region_id,
                    region_imports.c.status.in_(["pending", "running"]),
                )
            )
            .limit(1)
        ).fetchone()
        if existing:
            return str(existing.id), "existing"

        total_feeds = conn.execute(
            select(func.count())
            .select_from(
                feed_regions.join(feeds, feeds.c.feed_onestop_id == feed_regions.c.feed_onestop_id)
            )
            .where(
                and_(
                    feed_regions.c.region_onestop_id == region_id,
                    feeds.c.status == "active",
                )
            )
        ).scalar_one()

        region_import_id = new_uuid()
        status = "running"
        completed_at = None
        if total_feeds == 0:
            status = "completed"
            completed_at = now
        conn.execute(
            region_imports.insert().values(
                id=region_import_id,
                region_onestop_id=region_id,
                trigger_type=trigger_type,
                status=status,
                total_feeds=total_feeds,
                started_count=0,
                completed_count=0,
                failed_count=0,
                skipped_count=0,
                started_at=now,
                completed_at=completed_at,
                created_at=now,
                updated_at=now,
            )
        )
        return str(region_import_id), "created"


def list_region_feeds(region_id: str) -> List[Dict[str, str]]:
    engine = get_engine()
    with engine.begin() as conn:
        rows = conn.execute(
            select(feeds.c.feed_onestop_id, feeds.c.download_url)
            .select_from(
                feed_regions.join(feeds, feeds.c.feed_onestop_id == feed_regions.c.feed_onestop_id)
            )
            .where(
                and_(
                    feed_regions.c.region_onestop_id == region_id,
                    feeds.c.status == "active",
                )
            )
        ).fetchall()
    return [{"feed_id": row.feed_onestop_id, "download_url": row.download_url} for row in rows]


def mark_region_import_feed_started(region_import_id: str, feed_import_id: str, sequence: int) -> None:
    engine = get_engine()
    now = utc_now()
    with engine.begin() as conn:
        conn.execute(
            region_import_feeds.insert().values(
                region_import_id=uuid.UUID(region_import_id),
                feed_import_id=uuid.UUID(feed_import_id),
                sequence_number=sequence,
            )
        )
        conn.execute(
            update(region_imports)
            .where(region_imports.c.id == uuid.UUID(region_import_id))
            .values(
                started_count=region_imports.c.started_count + 1,
                updated_at=now,
            )
        )


def mark_region_import_feed_completed(region_import_id: str, success: bool) -> None:
    engine = get_engine()
    now = utc_now()
    with engine.begin() as conn:
        if success:
            conn.execute(
                update(region_imports)
                .where(region_imports.c.id == uuid.UUID(region_import_id))
                .values(
                    completed_count=region_imports.c.completed_count + 1,
                    updated_at=now,
                )
            )
        else:
            conn.execute(
                update(region_imports)
                .where(region_imports.c.id == uuid.UUID(region_import_id))
                .values(
                    failed_count=region_imports.c.failed_count + 1,
                    updated_at=now,
                )
            )


def mark_region_import_feed_skipped(region_import_id: str) -> None:
    engine = get_engine()
    now = utc_now()
    with engine.begin() as conn:
        conn.execute(
            update(region_imports)
            .where(region_imports.c.id == uuid.UUID(region_import_id))
            .values(
                skipped_count=region_imports.c.skipped_count + 1,
                updated_at=now,
            )
        )


def finalize_region_import(region_import_id: str) -> str:
    engine = get_engine()
    now = utc_now()
    with engine.begin() as conn:
        row = conn.execute(
            select(
                region_imports.c.total_feeds,
                region_imports.c.completed_count,
                region_imports.c.failed_count,
                region_imports.c.skipped_count,
            ).where(region_imports.c.id == uuid.UUID(region_import_id))
        ).fetchone()
        if not row:
            raise RuntimeError(f"Region import not found: {region_import_id}")
        completed = row.completed_count
        failed = row.failed_count
        skipped = row.skipped_count

        if completed == 0 and failed == 0 and skipped >= 0:
            status = "completed"
        elif failed == 0 and completed > 0:
            status = "completed"
        elif completed > 0 and failed > 0:
            status = "partial_success"
        elif failed > 0 and completed == 0:
            status = "failed"
        else:
            status = "completed"

        conn.execute(
            update(region_imports)
            .where(region_imports.c.id == uuid.UUID(region_import_id))
            .values(status=status, completed_at=now, updated_at=now)
        )
        return status


def persist_agencies(parsed: gtfs_lib.ParsedGTFS, feed_id: str) -> Dict[str, str]:
    engine = get_engine()
    now = utc_now()
    agencies_df = parsed.agencies.copy()
    if agencies_df.empty:
        return {}

    agencies_df["agency_id"] = agencies_df.get("agency_id", "").fillna("").replace("", "default")
    agencies_df["agency_onestop_id"] = agencies_df["agency_id"].apply(
        lambda a: f"{feed_id}-{a}"
    )

    rows = []
    for _, row in agencies_df.iterrows():
        rows.append(
            {
                "agency_onestop_id": row["agency_onestop_id"],
                "feed_onestop_id": feed_id,
                "gtfs_agency_id": row["agency_id"],
                "name": row.get("agency_name") or row.get("agency_id"),
                "website": row.get("agency_url") or None,
                "phone": row.get("agency_phone") or None,
                "last_feed_import": now,
                "active": True,
                "created_at": now,
                "updated_at": now,
            }
        )

    with engine.begin() as conn:
        for row in rows:
            stmt = (
                insert(agencies)
                .values(**row)
                .on_conflict_do_update(
                    index_elements=[agencies.c.agency_onestop_id],
                    set_={
                        "name": row["name"],
                        "website": row["website"],
                        "phone": row["phone"],
                        "last_feed_import": now,
                        "active": True,
                        "updated_at": now,
                    },
                )
            )
            conn.execute(stmt)

    return {r["gtfs_agency_id"]: r["agency_onestop_id"] for r in rows}


def persist_routes(
    parsed: gtfs_lib.ParsedGTFS, feed_id: str, agency_map: Dict[str, str]
) -> Tuple[Dict[Tuple[str, str], str], Dict[str, Optional[str]]]:
    engine = get_engine()
    now = utc_now()
    routes_df = parsed.routes.copy()
    if routes_df.empty:
        return {}

    rows = []
    for _, row in routes_df.iterrows():
        gtfs_route_id = row.get("route_id")
        agency_id = row.get("agency_id") or "default-agency"
        agency_onestop_id = agency_map.get(agency_id, f"{feed_id}-{agency_id}")
        route_type_raw = row.get("route_type")
        if route_type_raw is None or str(route_type_raw).strip() == "":
            raise RuntimeError(f"Route {gtfs_route_id} missing route_type")
        route_type_code = int(float(route_type_raw))
        if route_type_code not in ALLOWED_ROUTE_TYPES:
            raise RuntimeError(f"Unsupported GTFS route_type {route_type_code} for route {gtfs_route_id}")
        route_type = ALLOWED_ROUTE_TYPES[route_type_code]
        route_id = f"r-{agency_onestop_id}_{gtfs_route_id}"
        rows.append(
            {
                "id": route_id,
                "agency_onestop_id": agency_onestop_id,
                "gtfs_route_id": gtfs_route_id,
                "short_name": row.get("route_short_name") or None,
                "long_name": row.get("route_long_name")
                or row.get("route_short_name")
                or gtfs_route_id,
                "route_type": route_type,
                "color": (row.get("route_color") or None),
                "text_color": (row.get("route_text_color") or None),
                "active": True,
                "created_at": now,
                "updated_at": now,
            }
        )

    with engine.begin() as conn:
        for row in rows:
            stmt = (
                insert(routes)
                .values(**row)
                .on_conflict_do_update(
                    index_elements=[routes.c.id],
                    set_={
                        "agency_onestop_id": row["agency_onestop_id"],
                        "short_name": row["short_name"],
                        "long_name": row["long_name"],
                        "route_type": row["route_type"],
                        "color": row["color"],
                        "text_color": row["text_color"],
                        "active": True,
                        "updated_at": now,
                    },
                )
            )
            conn.execute(stmt)

    route_map = {(r["gtfs_route_id"], r["agency_onestop_id"]): r["id"] for r in rows}
    by_gtfs: Dict[str, Optional[str]] = {}
    for r in rows:
        current = by_gtfs.get(r["gtfs_route_id"])
        if current is None:
            by_gtfs[r["gtfs_route_id"]] = r["id"]
        elif current != r["id"]:
            by_gtfs[r["gtfs_route_id"]] = None
    return route_map, by_gtfs


def persist_stops(parsed: gtfs_lib.ParsedGTFS, feed_id: str) -> Dict[str, Dict]:
    engine = get_engine()
    now = utc_now()
    stops_df = parsed.stops.copy()
    if stops_df.empty:
        return {}

    rows = []
    for _, row in stops_df.iterrows():
        stop_id = row.get("stop_id")
        if not stop_id:
            continue
        rows.append(
            {
                "stop_onestop_id": stop_id,
                "feed_onestop_id": feed_id,
                "gtfs_stop_id": stop_id,
                "name": row.get("stop_name") or stop_id,
                "latitude": float(row.get("stop_lat")) if row.get("stop_lat") else None,
                "longitude": float(row.get("stop_lon")) if row.get("stop_lon") else None,
                "stop_code": row.get("stop_code") or None,
                "stop_desc": row.get("stop_desc") or None,
                "zone_id": row.get("zone_id") or None,
                "stop_url": row.get("stop_url") or None,
                "location_type": int(row.get("location_type"))
                if row.get("location_type")
                else None,
                "parent_station": row.get("parent_station") or None,
                "active": True,
                "first_seen": now,
                "last_seen": now,
                "created_at": now,
                "updated_at": now,
            }
        )

    with engine.begin() as conn:
        for row in rows:
            stmt = (
                insert(stops)
                .values(**row)
                .on_conflict_do_update(
                    index_elements=[stops.c.stop_onestop_id],
                    set_={
                        "name": row["name"],
                        "latitude": row["latitude"],
                        "longitude": row["longitude"],
                        "stop_code": row["stop_code"],
                        "stop_desc": row["stop_desc"],
                        "zone_id": row["zone_id"],
                        "stop_url": row["stop_url"],
                        "location_type": row["location_type"],
                        "parent_station": row["parent_station"],
                        "active": True,
                        "last_seen": now,
                        "updated_at": now,
                    },
                )
            )
            conn.execute(stmt)

    return {row["stop_onestop_id"]: row for row in rows}


def _build_trip_stop_patterns(stop_times_df: pd.DataFrame) -> Dict[str, List[str]]:
    if stop_times_df.empty:
        return {}
    required = {"trip_id", "stop_id", "stop_sequence"}
    missing = required.difference(stop_times_df.columns)
    if missing:
        raise RuntimeError(f"stop_times missing columns: {', '.join(sorted(missing))}")

    ordered = stop_times_df.sort_values(["trip_id", "stop_sequence"])
    patterns: Dict[str, List[str]] = defaultdict(list)
    for _, row in ordered.iterrows():
        trip_id = row["trip_id"]
        stop_id = row["stop_id"]
        if pd.isna(trip_id) or pd.isna(stop_id):
            continue
        patterns[str(trip_id)].append(str(stop_id))
    return patterns


def _variant_hash(stop_ids: List[str]) -> str:
    joined = "|".join(stop_ids)
    digest = hashlib.sha256(joined.encode("utf-8")).hexdigest()
    return digest


def persist_route_variants(
    parsed: gtfs_lib.ParsedGTFS,
    feed_id: str,
    route_map: Dict[Tuple[str, str], str],
    route_map_by_gtfs: Dict[str, Optional[str]],
    stop_lookup: Dict[str, Dict],
) -> List[Dict]:
    engine = get_engine()
    now = utc_now()
    trips_df = parsed.trips.copy()
    stop_times_df = parsed.stop_times.copy()
    if trips_df.empty or stop_times_df.empty:
        return []

    stop_patterns = _build_trip_stop_patterns(stop_times_df)

    variants_by_id: Dict[str, Dict] = {}
    for _, row in trips_df.iterrows():
        trip_id = row.get("trip_id")
        route_id_raw = row.get("route_id")
        if not trip_id or not route_id_raw:
            continue
        stop_ids = stop_patterns.get(str(trip_id), [])
        if len(stop_ids) < 2:
            continue
        agency_id = row.get("agency_id") or "default-agency"
        route_id = route_map.get((route_id_raw, f"{feed_id}-{agency_id}"))
        if not route_id:
            route_id = route_map.get((route_id_raw, f"{feed_id}-default-agency"))
        if not route_id:
            route_id = route_map_by_gtfs.get(route_id_raw)
        if not route_id:
            continue

        stop_names = [stop_lookup.get(stop_id, {}).get("name", stop_id) for stop_id in stop_ids]
        variant_id = _variant_hash(stop_ids)
        record = variants_by_id.get(variant_id)
        if not record:
            variants_by_id[variant_id] = {
                "id": variant_id,
                "route_id": route_id,
                "direction_id": int(row.get("direction_id"))
                if row.get("direction_id") not in (None, "")
                else None,
                "headsign": row.get("trip_headsign") or None,
                "stop_pattern": "|".join(stop_ids),
                "stop_name_pattern": "|".join(stop_names),
                "stop_count": len(stop_ids),
                "first_stop_id": stop_ids[0],
                "last_stop_id": stop_ids[-1],
                "active": True,
                "first_seen": now,
                "last_seen": now,
                "created_at": now,
                "updated_at": now,
                "stops": stop_ids,
            }
        else:
            record["last_seen"] = now

    with engine.begin() as conn:
        for record in variants_by_id.values():
            stmt = (
                insert(route_variants)
                .values(
                    id=record["id"],
                    route_id=record["route_id"],
                    direction_id=record["direction_id"],
                    headsign=record["headsign"],
                    stop_pattern=record["stop_pattern"],
                    stop_name_pattern=record["stop_name_pattern"],
                    stop_count=record["stop_count"],
                    first_stop_id=record["first_stop_id"],
                    last_stop_id=record["last_stop_id"],
                    active=True,
                    first_seen=record["first_seen"],
                    last_seen=record["last_seen"],
                    created_at=record["created_at"],
                    updated_at=record["updated_at"],
                )
                .on_conflict_do_update(
                    index_elements=[route_variants.c.id],
                    set_={
                        "route_id": record["route_id"],
                        "direction_id": record["direction_id"],
                        "headsign": record["headsign"],
                        "stop_pattern": record["stop_pattern"],
                        "stop_name_pattern": record["stop_name_pattern"],
                        "stop_count": record["stop_count"],
                        "first_stop_id": record["first_stop_id"],
                        "last_stop_id": record["last_stop_id"],
                        "active": True,
                        "last_seen": record["last_seen"],
                        "updated_at": record["updated_at"],
                    },
                )
            )
            conn.execute(stmt)

        for record in variants_by_id.values():
            conn.execute(delete(route_variant_stops).where(route_variant_stops.c.variant_id == record["id"]))
            inserts = [
                {
                    "variant_id": record["id"],
                    "stop_onestop_id": stop_id,
                    "stop_sequence": idx,
                }
                for idx, stop_id in enumerate(record["stops"])
            ]
            if inserts:
                conn.execute(route_variant_stops.insert(), inserts)

    return list(variants_by_id.values())


def _haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    earth_radius_km = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (
        math.sin(dlat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(dlon / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return earth_radius_km * c * 1000.0


def persist_stop_spacing(variants: List[Dict], stop_lookup: Dict[str, Dict]) -> None:
    engine = get_engine()
    now = utc_now()
    with engine.begin() as conn:
        for variant in variants:
            variant_id = variant["id"]
            stop_ids = variant["stops"]
            conn.execute(delete(stop_spacing).where(stop_spacing.c.variant_id == variant_id))
            inserts = []
            for idx in range(len(stop_ids) - 1):
                from_stop = stop_lookup.get(stop_ids[idx])
                to_stop = stop_lookup.get(stop_ids[idx + 1])
                if not from_stop or not to_stop:
                    continue
                if from_stop.get("latitude") is None or to_stop.get("latitude") is None:
                    continue
                distance = _haversine_meters(
                    from_stop["latitude"],
                    from_stop["longitude"],
                    to_stop["latitude"],
                    to_stop["longitude"],
                )
                inserts.append(
                    {
                        "id": new_uuid(),
                        "variant_id": variant_id,
                        "from_stop_id": stop_ids[idx],
                        "to_stop_id": stop_ids[idx + 1],
                        "stop_sequence": idx,
                        "distance_meters": distance,
                        "calculated_at": now,
                        "created_at": now,
                    }
                )
            if inserts:
                conn.execute(stop_spacing.insert(), inserts)


def classify_route_variants() -> None:
    engine = get_engine()
    now = utc_now()
    with engine.begin() as conn:
        spacing = conn.execute(
            select(stop_spacing.c.variant_id, func.avg(stop_spacing.c.distance_meters))
            .group_by(stop_spacing.c.variant_id)
        ).fetchall()
        avg_map = {row[0]: row[1] for row in spacing}

        variants = conn.execute(select(route_variants.c.id)).fetchall()
        for row in variants:
            variant_id = row[0]
            avg = avg_map.get(variant_id)
            classification = "UNKNOWN"
            if avg is not None and avg >= 0:
                for name, min_val, max_val in CLASSIFICATION_THRESHOLDS:
                    if max_val is None and avg >= min_val:
                        classification = name
                        break
                    if max_val is not None and min_val <= avg < max_val:
                        classification = name
                        break
            conn.execute(
                update(route_variants)
                .where(route_variants.c.id == variant_id)
                .values(
                    classification=classification,
                    average_stop_spacing_meters=avg,
                    updated_at=now,
                )
            )


def _contains_sequence(sequence: List[str], subsequence: List[str]) -> bool:
    if not subsequence:
        return True
    if len(subsequence) > len(sequence):
        return False
    for i in range(len(sequence) - len(subsequence) + 1):
        if sequence[i : i + len(subsequence)] == subsequence:
            return True
    return False


def _longest_common_sequence(sequences: List[List[str]]) -> List[str]:
    if not sequences:
        return []
    first = sequences[0]
    longest: List[str] = []
    for length in range(len(first), 0, -1):
        for start in range(0, len(first) - length + 1):
            subseq = first[start : start + length]
            if all(_contains_sequence(seq, subseq) for seq in sequences):
                return subseq
    return longest


def persist_route_common_sections(variants: List[Dict]) -> None:
    engine = get_engine()
    now = utc_now()

    by_route_direction: Dict[Tuple[str, Optional[int]], List[Dict]] = defaultdict(list)
    for variant in variants:
        by_route_direction[(variant["route_id"], variant.get("direction_id"))].append(variant)

    with engine.begin() as conn:
        for (route_id, direction_id), items in by_route_direction.items():
            if len(items) < 2:
                continue
            sequences = [item["stop_pattern"].split("|") for item in items]
            stop_names = [item.get("stop_name_pattern", "").split("|") for item in items]
            longest = _longest_common_sequence(sequences)
            if not longest:
                continue
            stop_pattern = "|".join(longest)
            names = []
            if stop_names and stop_names[0]:
                seq0 = sequences[0]
                names0 = stop_names[0]
                for i in range(len(seq0) - len(longest) + 1):
                    if seq0[i : i + len(longest)] == longest:
                        names = names0[i : i + len(longest)]
                        break
            if not names:
                names = longest
            stop_name_pattern = "|".join(names)
            section_id_input = f"{route_id}_{direction_id if direction_id is not None else 'null'}_{stop_pattern}"
            section_id = hashlib.sha256(section_id_input.encode("utf-8")).hexdigest()

            stmt = (
                insert(route_common_sections)
                .values(
                    id=section_id,
                    route_id=route_id,
                    direction_id=direction_id,
                    stop_pattern=stop_pattern,
                    stop_name_pattern=stop_name_pattern,
                    stop_count=len(longest),
                    first_stop_id=longest[0],
                    last_stop_id=longest[-1],
                    variant_count=len(items),
                    created_at=now,
                    updated_at=now,
                )
                .on_conflict_do_update(
                    index_elements=[route_common_sections.c.route_id, route_common_sections.c.direction_id],
                    set_={
                        "stop_pattern": stop_pattern,
                        "stop_name_pattern": stop_name_pattern,
                        "stop_count": len(longest),
                        "first_stop_id": longest[0],
                        "last_stop_id": longest[-1],
                        "variant_count": len(items),
                        "updated_at": now,
                    },
                )
            )
            conn.execute(stmt)


def _departure_minutes(times: Iterable[Optional[str]]) -> List[int]:
    minutes = []
    for value in times:
        seconds = gtfs_lib.parse_time_to_seconds(value)
        if seconds is None:
            continue
        minutes.append(seconds // 60)
    return minutes


def _headway_stats(minutes: List[int]) -> Optional[Tuple[float, float, float, bool]]:
    if not minutes:
        return None
    minutes.sort()
    headways = [b - a for a, b in zip(minutes, minutes[1:]) if b - a > 0]
    if not headways:
        return (None, None, None, True)
    min_h = min(headways)
    max_h = max(headways)
    avg_h = sum(headways) / len(headways)
    irregular = abs(max_h - min_h) > avg_h
    return (None if irregular else avg_h, min_h, max_h, irregular)
