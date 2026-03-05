"""OTP Graph Build DAG

Builds an OpenTripPlanner 2 street + transit graph for the geographic
region covered by all active transit feeds, then restarts the OTP
service so map-matching in the feed_import DAG works immediately.

Workflow
--------
1. compute_bbox   — query the stops table for the union bounding box of
                    all imported stop coordinates.
2. download_osm   — fetch the smallest Geofabrik PBF extract that covers
                    that bbox. Download is skipped when the local file
                    already matches the upstream Content-Length.
3. collect_gtfs   — copy the latest GTFS zip for each feed from the GTFS
                    storage root into the OTP data directory.  OTP uses
                    these for transit-aware routing; they are not strictly
                    required for WALK-mode map-matching but improve results.
4. build_graph    — run OTP --build --save via Docker, writing Graph.obj
                    to the shared otp-data volume.
5. reload_otp     — restart the running OTP container so it picks up the
                    new graph with --load --serve.

Configuration (environment variables)
--------------------------------------
MOBILISPECT_OTP_DATA_DIR   Path inside the Airflow container where the
                            otp-data volume is mounted.
                            Default: /var/opentripplanner
MOBILISPECT_OTP_IMAGE       OTP Docker image used for the build step.
                            Default: docker.io/opentripplanner/opentripplanner:2.7.0
MOBILISPECT_OTP_VOLUME      Docker named volume containing OTP data.
                            Default: devcontainer_otp-data
MOBILISPECT_OTP_CONTAINER   Name (or prefix) of the running OTP container
                            to restart after the build.
                            Default: devcontainer-otp-1
MOBILISPECT_GTFS_STORAGE_ROOT
                            Root directory of downloaded GTFS zips.
                            Default: /var/lib/mobilispect/gtfs

Trigger
-------
Schedule is None — trigger manually or from another DAG once feeds have
been imported and whenever the OSM extract becomes stale.
"""

from __future__ import annotations

import glob
import logging
import os
import shutil
from datetime import datetime, timedelta

import requests
from airflow.decorators import dag, task
from sqlalchemy import func, select

from pipeline import db
from pipeline import geofabrik as gf

log = logging.getLogger(__name__)

_OTP_DATA_DIR = os.environ.get("MOBILISPECT_OTP_DATA_DIR", "/var/opentripplanner")
_OTP_IMAGE = os.environ.get(
    "MOBILISPECT_OTP_IMAGE",
    "docker.io/opentripplanner/opentripplanner:2.7.0",
)
_OTP_VOLUME = os.environ.get("MOBILISPECT_OTP_VOLUME", "devcontainer_otp-data")
_OTP_CONTAINER = os.environ.get("MOBILISPECT_OTP_CONTAINER", "devcontainer-otp-1")
_GTFS_ROOT = os.environ.get("MOBILISPECT_GTFS_STORAGE_ROOT", "/var/lib/mobilispect/gtfs")


@dag(
    dag_id="otp_graph_build",
    schedule=None,
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["otp", "graph"],
    doc_md=__doc__,
)
def otp_graph_build() -> None:

    @task
    def compute_bbox() -> dict:
        """Query the stops table for the union bounding box of all stop coordinates.

        Raises ValueError when the stops table is empty (no feeds imported yet).
        """
        engine = db.get_engine()
        with engine.connect() as conn:
            row = conn.execute(
                select(
                    func.min(db.stops.c.latitude).label("min_lat"),
                    func.min(db.stops.c.longitude).label("min_lon"),
                    func.max(db.stops.c.latitude).label("max_lat"),
                    func.max(db.stops.c.longitude).label("max_lon"),
                )
            ).one()

        if row.min_lat is None:
            raise ValueError(
                "No stops found in the database. "
                "Import at least one transit feed before building the OTP graph."
            )

        bbox = {
            "min_lat": float(row.min_lat),
            "min_lon": float(row.min_lon),
            "max_lat": float(row.max_lat),
            "max_lon": float(row.max_lon),
        }
        log.info("Feed bounding box: %s", bbox)
        return bbox

    @task(retries=3, retry_delay=timedelta(minutes=5))
    def download_osm(bbox: dict) -> str:
        """Find and download the Geofabrik PBF extract that covers the feed bbox.

        Returns the path to the PBF file inside OTP_DATA_DIR.
        Download is skipped when the local file matches the upstream Content-Length.
        """
        os.makedirs(_OTP_DATA_DIR, exist_ok=True)
        with requests.Session() as session:
            features = gf.fetch_index(session)
            extract = gf.find_extract(
                features,
                min_lat=bbox["min_lat"],
                min_lon=bbox["min_lon"],
                max_lat=bbox["max_lat"],
                max_lon=bbox["max_lon"],
            )
            return gf.download_pbf(extract, _OTP_DATA_DIR, session)

    @task
    def collect_gtfs() -> list[str]:
        """Copy the latest GTFS zip for each feed into the OTP data directory.

        Uses a sanitised filename derived from the relative path under
        GTFS_STORAGE_ROOT to avoid collisions between feeds.
        Returns the list of destination paths.
        """
        os.makedirs(_OTP_DATA_DIR, exist_ok=True)
        pattern = os.path.join(_GTFS_ROOT, "**", "*.zip")
        sources = sorted(glob.glob(pattern, recursive=True))

        if not sources:
            log.warning(
                "No GTFS zip files found under %s — graph will be streets-only",
                _GTFS_ROOT,
            )
            return []

        copied = []
        for src in sources:
            rel = os.path.relpath(src, _GTFS_ROOT)
            dest_name = rel.replace(os.sep, "_")
            dest = os.path.join(_OTP_DATA_DIR, dest_name)
            shutil.copy2(src, dest)
            log.info("Copied %s → %s", src, dest)
            copied.append(dest)

        return copied

    @task(execution_timeout=timedelta(hours=2))
    def build_graph(osm_pbf_path: str, gtfs_feed_paths: list[str]) -> None:
        """Run OTP --build --save via the Docker API to produce Graph.obj.

        Starts a temporary OTP container that shares the named otp-data
        volume, waits for it to finish, then removes it.  Streams build
        logs to the Airflow task log.

        The *osm_pbf_path* and *gtfs_feed_paths* parameters are used only
        as dependency markers — OTP discovers files by scanning the volume.

        Raises RuntimeError when the OTP build container exits non-zero.
        """
        import docker  # deferred: docker SDK is an optional runtime dep

        log.info(
            "Starting OTP graph build: OSM=%s  GTFS feeds=%d",
            osm_pbf_path,
            len(gtfs_feed_paths),
        )
        client = docker.from_env()
        container = client.containers.run(
            _OTP_IMAGE,
            command=["--build", "--save"],
            volumes={_OTP_VOLUME: {"bind": "/var/opentripplanner", "mode": "rw"}},
            detach=True,
            remove=False,
        )
        try:
            for line in container.logs(stream=True):
                log.info("[otp-build] %s", line.decode().rstrip())
            result = container.wait(timeout=7200)
            exit_code = result.get("StatusCode", -1)
            if exit_code != 0:
                raise RuntimeError(
                    f"OTP --build container exited with code {exit_code}"
                )
        finally:
            container.remove(force=True)

        log.info("OTP graph build complete — Graph.obj written to volume")

    @task
    def reload_otp() -> None:
        """Restart the OTP container so it loads the newly built Graph.obj.

        Searches for containers whose name contains MOBILISPECT_OTP_CONTAINER.
        Logs a warning and returns gracefully when no matching container is
        found (e.g. OTP not yet started in this environment).
        """
        import docker

        client = docker.from_env()
        containers = client.containers.list(filters={"name": _OTP_CONTAINER})
        if not containers:
            log.warning(
                "No running OTP container matching '%s' — start the otp service "
                "manually to serve the new graph.",
                _OTP_CONTAINER,
            )
            return

        for c in containers:
            log.info("Restarting OTP container: %s", c.name)
            c.restart(timeout=30)

        log.info("OTP reload complete")

    # -----------------------------------------------------------------------
    # DAG wiring
    #
    #   compute_bbox ──► download_osm ─┐
    #                                  ├──► build_graph ──► reload_otp
    #   collect_gtfs ──────────────────┘
    # -----------------------------------------------------------------------
    bbox = compute_bbox()
    osm_path = download_osm(bbox)
    gtfs_paths = collect_gtfs()
    build_graph(osm_path, gtfs_paths) >> reload_otp()


otp_graph_build()
