import os
import uuid
from datetime import datetime, timezone

from sqlalchemy import (
    Boolean,
    Column,
    Date,
    DateTime,
    Float,
    Integer,
    MetaData,
    String,
    Table,
    Text,
    create_engine,
)
from sqlalchemy.dialects.postgresql import UUID
from uuid6 import uuid7

DATABASE_URL_ENV = "MOBILISPECT_DATABASE_URL"
FALLBACK_DATABASE_URL_ENV = "DATABASE_URL"
GTFS_STORAGE_ROOT_ENV = "MOBILISPECT_GTFS_STORAGE_ROOT"
DEFAULT_GTFS_STORAGE_ROOT = "/var/lib/mobilispect/gtfs"


def utc_now():
    return datetime.now(timezone.utc)


def get_database_url() -> str:
    url = os.environ.get(DATABASE_URL_ENV) or os.environ.get(FALLBACK_DATABASE_URL_ENV)
    if not url:
        raise RuntimeError(
            "Database URL not set. Provide MOBILISPECT_DATABASE_URL or DATABASE_URL."
        )
    return url


def get_engine():
    return create_engine(get_database_url(), pool_pre_ping=True)


def get_gtfs_storage_root() -> str:
    return os.environ.get(GTFS_STORAGE_ROOT_ENV, DEFAULT_GTFS_STORAGE_ROOT)


metadata = MetaData()

feeds = Table(
    "feeds",
    metadata,
    Column("feed_onestop_id", String(512), primary_key=True),
    Column("download_url", Text),
    Column("status", String(32)),
    Column("last_updated_at", DateTime(timezone=True)),
)

feed_regions = Table(
    "feed_regions",
    metadata,
    Column("feed_onestop_id", String(512), primary_key=True),
    Column("region_onestop_id", String(255), primary_key=True),
)

feed_imports = Table(
    "feed_imports",
    metadata,
    Column("id", UUID(as_uuid=True), primary_key=True),
    Column("feed_onestop_id", String(512)),
    Column("trigger_type", String(32)),
    Column("status", String(32)),
    Column("version_sha1", String(40)),
    Column("started_at", DateTime(timezone=True)),
    Column("completed_at", DateTime(timezone=True)),
    Column("file_size_bytes", Integer),
    Column("error_message", Text),
    Column("created_at", DateTime(timezone=True)),
    Column("updated_at", DateTime(timezone=True)),
)

region_imports = Table(
    "region_imports",
    metadata,
    Column("id", UUID(as_uuid=True), primary_key=True),
    Column("region_onestop_id", String(512)),
    Column("trigger_type", String(32)),
    Column("status", String(32)),
    Column("total_feeds", Integer),
    Column("started_count", Integer),
    Column("completed_count", Integer),
    Column("failed_count", Integer),
    Column("skipped_count", Integer),
    Column("started_at", DateTime(timezone=True)),
    Column("completed_at", DateTime(timezone=True)),
    Column("error_message", Text),
    Column("created_at", DateTime(timezone=True)),
    Column("updated_at", DateTime(timezone=True)),
)

region_import_feeds = Table(
    "region_import_feeds",
    metadata,
    Column("region_import_id", UUID(as_uuid=True), primary_key=True),
    Column("feed_import_id", UUID(as_uuid=True), primary_key=True),
    Column("sequence_number", Integer),
)

agencies = Table(
    "agencies",
    metadata,
    Column("agency_onestop_id", String(255), primary_key=True),
    Column("feed_onestop_id", String(512)),
    Column("gtfs_agency_id", String(255)),
    Column("name", String(255)),
    Column("website", String(512)),
    Column("phone", String(50)),
    Column("last_feed_import", DateTime(timezone=True)),
    Column("active", Boolean),
    Column("created_at", DateTime(timezone=True)),
    Column("updated_at", DateTime(timezone=True)),
)

routes = Table(
    "routes",
    metadata,
    Column("id", String(50), primary_key=True),
    Column("agency_onestop_id", String(255)),
    Column("gtfs_route_id", String(255)),
    Column("short_name", String(255)),
    Column("long_name", String(255)),
    Column("route_type", String(64)),
    Column("color", String(6)),
    Column("text_color", String(6)),
    Column("active", Boolean),
    Column("created_at", DateTime(timezone=True)),
    Column("updated_at", DateTime(timezone=True)),
)

route_variants = Table(
    "route_variants",
    metadata,
    Column("id", String(64), primary_key=True),
    Column("route_id", String(50)),
    Column("direction_id", Integer),
    Column("headsign", String(255)),
    Column("stop_pattern", Text),
    Column("stop_name_pattern", Text),
    Column("stop_count", Integer),
    Column("first_stop_id", String(255)),
    Column("last_stop_id", String(255)),
    Column("classification", String(20)),
    Column("average_stop_spacing_meters", Float),
    Column("active", Boolean),
    Column("first_seen", DateTime(timezone=True)),
    Column("last_seen", DateTime(timezone=True)),
    Column("created_at", DateTime(timezone=True)),
    Column("updated_at", DateTime(timezone=True)),
)

stops = Table(
    "stops",
    metadata,
    Column("stop_onestop_id", String(255), primary_key=True),
    Column("feed_onestop_id", String(512)),
    Column("gtfs_stop_id", String(255)),
    Column("name", String(255)),
    Column("latitude", Float),
    Column("longitude", Float),
    Column("stop_code", String(50)),
    Column("stop_desc", Text),
    Column("zone_id", String(50)),
    Column("stop_url", String(512)),
    Column("location_type", Integer),
    Column("parent_station", String(255)),
    Column("active", Boolean),
    Column("first_seen", DateTime(timezone=True)),
    Column("last_seen", DateTime(timezone=True)),
    Column("created_at", DateTime(timezone=True)),
    Column("updated_at", DateTime(timezone=True)),
)

route_variant_stops = Table(
    "route_variant_stops",
    metadata,
    Column("variant_id", String(64), primary_key=True),
    Column("stop_onestop_id", String(255), primary_key=True),
    Column("stop_sequence", Integer, primary_key=True),
)

stop_spacing = Table(
    "stop_spacing",
    metadata,
    Column("id", UUID(as_uuid=True), primary_key=True),
    Column("variant_id", String(64)),
    Column("from_stop_id", String(64)),
    Column("to_stop_id", String(64)),
    Column("stop_sequence", Integer),
    Column("distance_meters", Float),
    Column("calculated_at", DateTime(timezone=True)),
    Column("created_at", DateTime(timezone=True)),
)

def new_uuid() -> uuid.UUID:
    return uuid7()
