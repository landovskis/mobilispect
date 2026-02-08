# Airflow Import Orchestration

These DAGs run the Python import pipeline and write directly to Postgres.

## Requirements

1. Airflow 2.5+ with dynamic task mapping enabled.
2. Python dependencies from `airflow/requirements.txt`.
3. Postgres reachable from the Airflow runtime.
4. `airflow/` must be on `PYTHONPATH` so DAGs can import `pipeline`.

## Environment

Set the database URL and local GTFS storage root for the Airflow runtime.

```bash
export MOBILISPECT_DATABASE_URL="postgresql+psycopg2://user@host:5432/mobilispect"
export MOBILISPECT_GTFS_STORAGE_ROOT="/var/lib/mobilispect/gtfs"
export PYTHONPATH="/path/to/repo/airflow"
```

## DAG Inputs

Trigger `region_import` with JSON conf (use `region_id` or `region_name`):

```json
{
  "region_id": "r-dpz8-sf",
  "trigger_type": "automatic"
}
```

```json
{
  "region_name": "Montréal",
  "trigger_type": "automatic"
}
```

Trigger `feed_import` directly with JSON conf:

```json
{
  "feed_id": "f-dpz8-sf-bart",
  "trigger_type": "manual"
}
```

## Testing

```bash
python -m pip install -r airflow/requirements-dev.txt
pytest airflow/tests
```

## Notes

1. `region_import` triggers `feed_import` per feed and waits for completion
   before finalizing.
2. `feed_import` updates region import counts when `region_import_id` is provided.
