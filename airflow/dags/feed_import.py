import logging
import os
import zipfile
from datetime import datetime, timedelta
from typing import Optional

from airflow.sdk import dag, task, get_current_context
from airflow.task.trigger_rule import TriggerRule

log = logging.getLogger(__name__)

from pipeline import gtfs, processing
from pipeline.models import FeedImportPayload, PersistResult


@dag(
    dag_id="feed_import",
    start_date=datetime(2024, 1, 1),
    schedule=None,
    catchup=False,
    tags=["mobilispect", "imports"],
)
def feed_import() -> None:
    @task
    def start_import() -> FeedImportPayload:
        context = get_current_context()
        conf = (context.get("dag_run") or {}).conf or {}
        feed_id = conf.get("feed_id")
        if not feed_id:
            raise ValueError("feed_id is required in dag_run.conf")

        trigger_type = conf.get("trigger_type", "automatic")
        region_import_id = conf.get("region_import_id")
        sequence = int(conf.get("sequence", 0))

        result = processing.start_feed_import(feed_id, trigger_type)
        payload = FeedImportPayload(
            status=result.status,
            import_id=result.import_id,
            feed_id=result.feed_id,
            download_url=result.download_url,
            trigger_type=trigger_type,
            region_import_id=region_import_id,
            sequence=sequence,
            message=result.message,
        )

        if region_import_id:
            if result.status == "STARTED" and result.import_id:
                processing.mark_region_import_feed_started(
                    region_import_id, result.import_id, sequence
                )
            else:
                processing.mark_region_import_feed_skipped(region_import_id)

        return payload

    @task.short_circuit
    def should_continue(start_result: FeedImportPayload) -> bool:
        return start_result.get("status") == "STARTED"

    @task(retries=3, retry_delay=timedelta(minutes=2))
    def download_feed(start_result: FeedImportPayload) -> str:
        import_id: Optional[str] = start_result["import_id"]
        download_url: Optional[str] = start_result["download_url"]
        if not import_id or not download_url:
            raise RuntimeError("import_id and download_url are required")
        log.info("Downloading GTFS feed from %s", download_url)
        zip_path = gtfs.download_gtfs_zip(download_url, import_id)
        if not zipfile.is_zipfile(zip_path):
            raise RuntimeError(f"Downloaded file is not a valid zip: {download_url}")
        file_size = os.path.getsize(zip_path)
        processing.update_feed_import_file_size(import_id, file_size)
        return zip_path

    @task
    def extract_feed(zip_path: str, start_result: FeedImportPayload) -> str:
        return gtfs.extract_gtfs_zip(zip_path, start_result["import_id"])

    @task
    def validate_feed(extract_dir: str) -> str:
        gtfs.validate_gtfs_files(extract_dir)
        return extract_dir

    @task(retries=3, retry_delay=timedelta(minutes=2))
    def parse_feed(extract_dir: str, start_result: FeedImportPayload) -> str:
        parsed = gtfs.parse_gtfs(extract_dir)
        gtfs.write_metadata(start_result["import_id"], gtfs.snapshot_metadata(parsed))
        return gtfs.save_parsed(parsed, start_result["import_id"])

    @task
    def persist_feed(parsed_path: str, start_result: FeedImportPayload) -> PersistResult:
        parsed = gtfs.load_parsed(parsed_path)
        feed_id = start_result["feed_id"]
        agency_map = processing.persist_agencies(parsed, feed_id)
        route_map, route_map_by_gtfs = processing.persist_routes(
            parsed, feed_id, agency_map
        )
        stop_lookup = processing.persist_stops(parsed, feed_id)
        variants = processing.persist_route_variants(
            parsed, feed_id, route_map, route_map_by_gtfs, stop_lookup
        )
        processing.persist_stop_spacing(variants, stop_lookup)
        processing.classify_route_variants(variants)
        processing.persist_route_common_sections(variants)
        variant_shape_map = {v["id"]: v.get("shape_id") for v in variants}
        return PersistResult(variants=len(variants), variant_shape_map=variant_shape_map)

    @task(retries=2, retry_delay=timedelta(minutes=1))
    def match_shapes(persist_result: PersistResult, parsed_path: str, start_result: FeedImportPayload) -> None:
        otp_url = os.environ.get("MOBILISPECT_OTP_URL", "http://otp:8080")
        parsed = gtfs.load_parsed(parsed_path)
        variant_shape_map = persist_result.get("variant_shape_map") or {}
        processing.match_and_persist_shapes(parsed, variant_shape_map, otp_url)

    @task(trigger_rule=TriggerRule.ALL_SUCCESS)
    def finalize_success(start_result: FeedImportPayload) -> None:
        processing.update_feed_import_success(
            start_result["import_id"], start_result["feed_id"]
        )
        if start_result.get("region_import_id"):
            processing.mark_region_import_feed_completed(
                start_result["region_import_id"], True
            )

    @task(trigger_rule=TriggerRule.ONE_FAILED)
    def finalize_failure(start_result: FeedImportPayload) -> None:
        if start_result.get("import_id"):
            processing.update_feed_import_failure(
                start_result["import_id"], "Airflow task failed"
            )
        if start_result.get("region_import_id"):
            processing.mark_region_import_feed_completed(
                start_result["region_import_id"], False
            )

    started = start_import()
    proceed = should_continue(started)
    zip_path = download_feed(started)
    extract_dir = extract_feed(zip_path, started)
    validated = validate_feed(extract_dir)
    parsed_path = parse_feed(validated, started)
    persisted = persist_feed(parsed_path, started)
    matched = match_shapes(persisted, parsed_path, started)

    proceed >> zip_path
    matched >> finalize_success(started)
    [
        zip_path,
        extract_dir,
        validated,
        parsed_path,
        persisted,
        matched,
    ] >> finalize_failure(started)


feed_import()
