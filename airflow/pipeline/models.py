from typing import Optional

from typing import TypedDict


# ---------------------------------------------------------------------------
# Shared across DAGs
# ---------------------------------------------------------------------------

class FeedImportConf(TypedDict):
    """Configuration passed via dag_run.conf to trigger a feed_import DAG run."""

    feed_id: str
    trigger_type: str
    region_import_id: str
    sequence: int


class RegionFeedInfo(TypedDict):
    """A single feed associated with a region (returned by list_region_feeds)."""

    feed_id: str
    download_url: str


class DiscoveryResult(TypedDict):
    """Result of discovering feeds for a region via Transit.land."""

    feeds_discovered: int
    feeds_updated: int


# ---------------------------------------------------------------------------
# region_import DAG-local
# ---------------------------------------------------------------------------

class RegionImportStartResult(TypedDict):
    """XCom payload from the start_region_import task."""

    region_id: str
    trigger_type: str
    region_import_id: str
    state: str


# ---------------------------------------------------------------------------
# feed_import DAG-local
# ---------------------------------------------------------------------------

class FeedImportPayload(TypedDict):
    """XCom payload from the start_import task in feed_import."""

    status: str
    import_id: Optional[str]
    feed_id: str
    download_url: Optional[str]
    trigger_type: str
    region_import_id: Optional[str]
    sequence: int
    message: Optional[str]


class PersistResult(TypedDict):
    """Result of persisting parsed GTFS data."""

    variants: int
