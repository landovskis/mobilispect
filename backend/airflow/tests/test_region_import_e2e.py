import uuid

import pytest
import responses
from sqlalchemy import select
from testcontainers.postgres import PostgresContainer

from pipeline import db, processing


@pytest.fixture(scope="session")
def postgres_container():
    with PostgresContainer("postgres:16-alpine") as container:
        yield container


@pytest.fixture()
def db_engine(postgres_container, monkeypatch):
    monkeypatch.setenv(db.DATABASE_URL_ENV, postgres_container.get_connection_url())
    monkeypatch.setenv(db.GTFS_STORAGE_ROOT_ENV, "/tmp/mobilispect-gtfs")
    engine = db.get_engine()
    db.metadata.create_all(engine)
    try:
        yield engine
    finally:
        db.metadata.drop_all(engine)


def _seed_region(
    engine,
    region_id: str,
    name: str = "San Francisco Bay Area",
    adm0_name: str = "United States",
    adm1_name: str = "California",
):
    with engine.begin() as conn:
        conn.execute(
            db.metropolitan_regions.insert().values(
                region_onestop_id=region_id,
                name=name,
                adm0_name=adm0_name,
                adm1_name=adm1_name,
            )
        )


def _mock_transitland_endpoints():
    operators_url = f"{processing.TRANSITLAND_BASE_URL}/operators.json"
    feeds_url = f"{processing.TRANSITLAND_BASE_URL}/feeds.json"

    operator_page_one = {
        "operators": [
            {
                "onestop_id": "o-bay",
                "agencies": [
                    {
                        "places": [
                            {
                                "city_name": "San Francisco Bay Area",
                                "adm0_name": "United States",
                                "adm1_name": "California",
                            }
                        ]
                    }
                ],
                "feeds": [
                    {"onestop_id": "f-bay-rail", "spec": "gtfs"},
                    {"onestop_id": "f-bay-ferry", "spec": "gtfs"},
                    {"onestop_id": "f-bay-rt", "spec": "gtfs-rt"},
                ],
            },
            {
                "onestop_id": "o-no-match",
                "agencies": [
                    {
                        "places": [
                            {
                                "city_name": "Las Vegas",
                                "adm0_name": "United States",
                                "adm1_name": "Nevada",
                            }
                        ]
                    }
                ],
                "feeds": [
                    {"onestop_id": "f-vegas", "spec": "gtfs"},
                ],
            },
        ],
        "meta": {"after": 1},
    }

    operator_page_two = {
        "operators": [
            {
                "onestop_id": "o-bay-2",
                "agencies": [
                    {
                        "places": [
                            {
                                "city_name": "Oakland",
                                "adm0_name": "United States",
                                "adm1_name": "California",
                            }
                        ]
                    }
                ],
                "feeds": [
                    {"onestop_id": "f-bay-bus", "spec": "gtfs"},
                ],
            }
        ],
        "meta": {},
    }

    feed_payloads = {
        "f-bay-rail": {
            "feeds": [
                {
                    "onestop_id": "f-bay-rail",
                    "feed_versions": [{"url": "https://example.com/bay-rail.zip"}],
                }
            ]
        },
        "f-bay-ferry": {
            "feeds": [
                {
                    "onestop_id": "f-bay-ferry",
                    "feed_versions": [{"url": "https://example.com/bay-ferry.zip"}],
                }
            ]
        },
        "f-bay-bus": {
            "feeds": [
                {
                    "onestop_id": "f-bay-bus",
                    "feed_versions": [{"url": "https://example.com/bay-bus.zip"}],
                }
            ]
        },
    }

    responses.add(
        responses.GET,
        operators_url,
        json=operator_page_one,
        match=[responses.matchers.query_param_matcher({"limit": "100"})],
    )
    responses.add(
        responses.GET,
        operators_url,
        json=operator_page_two,
        match=[responses.matchers.query_param_matcher({"limit": "100", "after": "1"})],
    )

    for feed_id, payload in feed_payloads.items():
        responses.add(
            responses.GET,
            feeds_url,
            json=payload,
            match=[
                responses.matchers.query_param_matcher(
                    {"onestop_id": feed_id, "include_alerts": "false"}
                )
            ],
        )


def test_resolve_region_id_by_name_normalizes_accents(db_engine):
    region_id = "r-ca-montreal"
    _seed_region(
        db_engine,
        region_id,
        name="Montréal",
        adm0_name="Canada",
        adm1_name="Quebec",
    )

    resolved = processing.resolve_region_id(None, "Montreal")

    assert resolved == region_id


@responses.activate
def test_region_import_end_to_end(db_engine, monkeypatch):
    region_id = "r-test-bay"
    monkeypatch.setenv(processing.TRANSITLAND_API_KEY_ENV, "test-key")
    monkeypatch.setattr(processing.time, "sleep", lambda *_: None)

    _seed_region(db_engine, region_id)
    _mock_transitland_endpoints()

    discovery_result = processing.discover_region_feeds(region_id)
    assert discovery_result == {"feeds_discovered": 3, "feeds_updated": 3}

    with db_engine.begin() as conn:
        feed_rows = conn.execute(
            select(
                db.feeds.c.feed_onestop_id,
                db.feeds.c.download_url,
                db.feeds.c.status,
            ).order_by(db.feeds.c.feed_onestop_id)
        ).fetchall()
        feed_region_rows = conn.execute(
            select(
                db.feed_regions.c.feed_onestop_id,
                db.feed_regions.c.region_onestop_id,
            ).order_by(db.feed_regions.c.feed_onestop_id)
        ).fetchall()

    assert [row.feed_onestop_id for row in feed_rows] == [
        "f-bay-bus",
        "f-bay-ferry",
        "f-bay-rail",
    ]
    assert {row.status for row in feed_rows} == {"active"}
    assert {row.region_onestop_id for row in feed_region_rows} == {region_id}

    region_import_id, status = processing.start_region_import(region_id, "automatic")
    assert status == "created"

    listed_feeds = processing.list_region_feeds(region_id)
    assert {feed["feed_id"] for feed in listed_feeds} == {
        "f-bay-rail",
        "f-bay-ferry",
        "f-bay-bus",
    }

    feed_import_a = str(db.new_uuid())
    feed_import_b = str(db.new_uuid())
    feed_import_c = str(db.new_uuid())

    processing.mark_region_import_feed_started(region_import_id, feed_import_a, 1)
    processing.mark_region_import_feed_started(region_import_id, feed_import_b, 2)
    processing.mark_region_import_feed_started(region_import_id, feed_import_c, 3)

    processing.mark_region_import_feed_completed(region_import_id, True)
    processing.mark_region_import_feed_completed(region_import_id, False)
    processing.mark_region_import_feed_completed(region_import_id, True)

    final_status = processing.finalize_region_import(region_import_id)
    assert final_status == "partial_success"

    with db_engine.begin() as conn:
        region_row = conn.execute(
            select(
                db.region_imports.c.total_feeds,
                db.region_imports.c.started_count,
                db.region_imports.c.completed_count,
                db.region_imports.c.failed_count,
                db.region_imports.c.status,
            ).where(db.region_imports.c.id == uuid.UUID(region_import_id))
        ).fetchone()
        import_feed_rows = conn.execute(
            select(db.region_import_feeds.c.feed_import_id)
            .where(db.region_import_feeds.c.region_import_id == region_import_id)
            .order_by(db.region_import_feeds.c.sequence_number)
        ).fetchall()

    assert region_row.total_feeds == 3
    assert region_row.started_count == 3
    assert region_row.completed_count == 2
    assert region_row.failed_count == 1
    assert region_row.status == "partial_success"
    assert [str(row.feed_import_id) for row in import_feed_rows] == [
        feed_import_a,
        feed_import_b,
        feed_import_c,
    ]
