from datetime import datetime

from airflow.decorators import dag, task
from airflow.operators.python import get_current_context
from airflow.operators.trigger_dagrun import TriggerDagRunOperator
from airflow.utils.trigger_rule import TriggerRule

from pipeline import processing


@dag(
    dag_id="region_import",
    start_date=datetime(2024, 1, 1),
    schedule=None,
    catchup=False,
    tags=["mobilispect", "imports"],
)
def region_import():
    @task
    def resolve_region():
        context = get_current_context()
        conf = (context.get("dag_run") or {}).conf or {}
        region_id = conf.get("region_id")
        region_name = conf.get("region_name") or conf.get("city_name")
        return processing.resolve_region_id(region_id, region_name)

    @task
    def discover_region_feeds(region_id: str):
        return processing.discover_region_feeds(region_id)

    @task
    def start_region_import(region_id: str):
        context = get_current_context()
        conf = (context.get("dag_run") or {}).conf or {}
        trigger_type = conf.get("trigger_type", "automatic")
        region_import_id, state = processing.start_region_import(region_id, trigger_type)
        return {
            "region_id": region_id,
            "trigger_type": trigger_type,
            "region_import_id": region_import_id,
            "state": state,
        }

    @task
    def list_feeds(start_result: dict):
        return processing.list_region_feeds(start_result["region_id"])

    @task
    def build_run_confs(start_result: dict, feeds: list):
        run_confs = []
        for index, feed in enumerate(feeds):
            run_confs.append(
                {
                    "feed_id": feed["feed_id"],
                    "trigger_type": start_result["trigger_type"],
                    "region_import_id": start_result["region_import_id"],
                    "sequence": index,
                }
            )
        return run_confs

    @task(trigger_rule=TriggerRule.ALL_DONE)
    def finalize_region_import(start_result: dict):
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
        .expand(conf=run_confs)
    )

    trigger_feeds >> finalize_region_import(start_result)


region_import()
