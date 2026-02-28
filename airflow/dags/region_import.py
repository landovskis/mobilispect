import re
from datetime import datetime
from typing import List, Optional

from airflow.sdk import dag, task, get_current_context
from airflow.providers.standard.operators.trigger_dagrun import TriggerDagRunOperator
from airflow.task.trigger_rule import TriggerRule

from pipeline import processing
from pipeline.models import (
    DiscoveryResult,
    FeedImportConf,
    RegionFeedInfo,
    RegionImportStartResult,
)


@dag(
    dag_id="region_import",
    start_date=datetime(2024, 1, 1),
    schedule=None,
    catchup=False,
    tags=["mobilispect", "imports"],
)
def region_import() -> None:
    @task
    def resolve_region() -> str:
        context = get_current_context()
        conf = (context.get("dag_run") or {}).conf or {}
        region_id = conf.get("region_id")
        region_name = conf.get("region_name") or conf.get("city_name")
        return processing.resolve_region_id(region_id, region_name)

    @task
    def discover_region_feeds(region_id: str) -> DiscoveryResult:
        context = get_current_context()
        conf = (context.get("dag_run") or {}).conf or {}
        region_name = conf.get("region_name") or conf.get("city_name")
        if not region_name:
            raise ValueError("region_name or city_name is required for feed discovery")
        return processing.discover_region_feeds(region_name, region_id)

    @task
    def start_region_import(region_id: str) -> RegionImportStartResult:
        context = get_current_context()
        conf = (context.get("dag_run") or {}).conf or {}
        trigger_type = conf.get("trigger_type", "automatic")
        region_import_id, state = processing.start_region_import(region_id, trigger_type)
        return RegionImportStartResult(
            region_id=region_id,
            trigger_type=trigger_type,
            region_import_id=region_import_id,
            state=state,
        )

    @task
    def list_feeds(start_result: RegionImportStartResult) -> List[RegionFeedInfo]:
        return processing.list_region_feeds(start_result["region_id"])

    @task
    def build_run_confs(start_result: RegionImportStartResult, feeds: List[RegionFeedInfo]) -> List[dict]:
        run_confs = []
        for index, feed in enumerate(feeds):
            feed_id = feed["feed_id"]
            safe_feed_id = re.sub(r"[^a-zA-Z0-9_.-]", "_", feed_id)
            run_confs.append({
                "conf": FeedImportConf(
                    feed_id=feed_id,
                    trigger_type=start_result["trigger_type"],
                    region_import_id=start_result["region_import_id"],
                    sequence=index,
                ),
                "trigger_run_id": "feed_import__%s" % safe_feed_id,
            })
        return run_confs

    @task(trigger_rule=TriggerRule.ALL_DONE)
    def finalize_region_import(start_result: Optional[RegionImportStartResult]) -> str:
        if start_result is None or start_result.get("region_import_id") is None:
            return "skipped"
        return processing.finalize_region_import(start_result["region_import_id"])

    region_id = resolve_region()
    discovery = discover_region_feeds(region_id)
    start_result = start_region_import(region_id)
    discovery >> start_result
    feeds = list_feeds(start_result)
    run_confs = build_run_confs(start_result, feeds)

    trigger_feeds = (
        TriggerDagRunOperator.partial(
            task_id="trigger_feed_imports",
            trigger_dag_id="feed_import",
            wait_for_completion=True,
            reset_dag_run=True,
            poke_interval=30,
        )
        .expand_kwargs(run_confs)
    )

    trigger_feeds >> finalize_region_import(start_result)


region_import()
