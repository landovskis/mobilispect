use anyhow::Result;
use sqlx::PgPool;
use sqlx::postgres::{PgConnectOptions, PgPoolOptions};
use std::str::FromStr;
use std::time::Duration;

#[derive(Clone, Debug)]
pub struct Database {
    pub pool: PgPool,
}

impl Database {
    pub async fn connect(database_url: &str) -> Result<Self> {
        let options = PgConnectOptions::from_str(database_url)?;
        let pool = PgPoolOptions::new()
            .max_connections(5)
            .idle_timeout(Duration::from_secs(300))
            .test_before_acquire(true)
            .connect_with(options)
            .await?;
        Ok(Self { pool })
    }

    pub async fn migrate(&self) -> Result<()> {
        sqlx::migrate!("./migrations").run(&self.pool).await?;
        Ok(())
    }
}

pub mod feeds;

#[cfg(any(test, feature = "test-utils"))]
pub mod test_utils {
    use super::Database;
    use testcontainers::{ContainerAsync, ImageExt, runners::AsyncRunner};
    use testcontainers_modules::postgres::Postgres;

    pub struct TestDb {
        pub db: Database,
        _container: ContainerAsync<Postgres>,
    }

    pub async fn setup() -> TestDb {
        let td = setup_unmigrated().await;
        td.db.migrate().await.unwrap();
        td
    }

    /// A fresh Postgres container with NO migrations applied. For migration
    /// tests that need to apply migrations in stages -- seeding fixture rows in
    /// between -- via `apply_migrations_in_range`. Ordinary tests want
    /// `setup()`.
    pub async fn setup_unmigrated() -> TestDb {
        let container = Postgres::default()
            .with_tag("16-alpine")
            .start()
            .await
            .unwrap();
        let port = container.get_host_port_ipv4(5432).await.unwrap();
        let db = Database::connect(&format!(
            "postgres://postgres:postgres@127.0.0.1:{port}/postgres"
        ))
        .await
        .unwrap();
        TestDb {
            db,
            _container: container,
        }
    }

    /// Runs the raw SQL of every bundled migration whose version falls inside
    /// `versions`, in ascending version order.
    ///
    /// Deliberately bypasses sqlx's `_sqlx_migrations` ledger: the point is to
    /// stop partway through the migration sequence, seed rows as they existed at
    /// that moment, and then apply the migration under test to them --
    /// `Migrator::run` has no "up to version N" mode. Nothing in a test asserts
    /// on the ledger, so leaving it unwritten is fine.
    pub async fn apply_migrations_in_range<R>(pool: &sqlx::PgPool, versions: R)
    where
        R: std::ops::RangeBounds<i64>,
    {
        let migrator = sqlx::migrate!("./migrations");
        for migration in migrator.iter() {
            if migration.migration_type.is_down_migration()
                || !versions.contains(&migration.version)
            {
                continue;
            }
            sqlx::raw_sql(&migration.sql)
                .execute(pool)
                .await
                .unwrap_or_else(|e| {
                    panic!(
                        "migration {} ({}) failed: {e}",
                        migration.version, migration.description
                    )
                });
        }
    }
}

/// Tests for migration SQL itself, rather than for code that runs against an
/// already-migrated schema.
#[cfg(test)]
mod migration_tests {
    use super::test_utils;

    /// Migration 028 moves #027's per-cross-section treatment data onto the new
    /// `intersections` table and then DROPs `intersection_treatments` and
    /// `cross_sections.bus_stop`. Its backfill originally minted an Intersection
    /// only for each corridor's first/last cross-section, but #027 let an
    /// analyst set treatments on ANY cross-section (`intersection_treatments`'
    /// primary key is a bare `cross_section_id` FK, and the lane editor's
    /// bus-stop PATCH operated on whichever cross-section was selected). So the
    /// carry-over UPDATEs saw only the endpoint subset and the DROPs silently
    /// destroyed every interior cross-section's treatment data.
    ///
    /// Every other test in this crate starts from an empty testcontainers
    /// database, so this destructive path had no coverage at all before this
    /// test: there was never any pre-028 data present for it to destroy.
    #[tokio::test]
    async fn migration_028_preserves_treatment_data_on_interior_cross_sections() {
        let td = test_utils::setup_unmigrated().await;
        let pool = &td.db.pool;

        // Bring the schema up to main-plus-#027 -- the state a production
        // database is in immediately before 028 runs.
        test_utils::apply_migrations_in_range(pool, ..=27).await;

        sqlx::query("INSERT INTO regions (id, name, timezone) VALUES (1, 'Test Region', 'UTC')")
            .execute(pool)
            .await
            .unwrap();
        let remix_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Remix', 1) RETURNING id",
        )
        .fetch_one(pool)
        .await
        .unwrap();
        let corridor_id: i64 = sqlx::query_scalar(
            "INSERT INTO corridors (name, geometry_source, remix_id) \
             VALUES ('Treated Corridor', 'manual', $1) RETURNING id",
        )
        .bind(remix_id)
        .fetch_one(pool)
        .await
        .unwrap();

        // Four cross-sections. Positions 1 and 2 are INTERIOR -- neither the
        // corridor's first nor its last -- and both carry #027 treatment data:
        // one an `intersection_treatments` row, the other a `bus_stop`.
        let mut cross_section_ids = Vec::new();
        for (position, lat) in [45.500, 45.501, 45.502, 45.503].into_iter().enumerate() {
            // `position` is NUMERIC as of migration 022 -- bind through
            // `::float8` so Postgres' float8 -> numeric assignment cast applies.
            let id: i64 = sqlx::query_scalar(
                "INSERT INTO cross_sections (corridor_id, position, lat, lon) \
                 VALUES ($1, $2::float8, $3, -73.600) RETURNING id",
            )
            .bind(corridor_id)
            .bind(position as f64)
            .bind(lat)
            .fetch_one(pool)
            .await
            .unwrap();
            cross_section_ids.push(id);
        }
        sqlx::query(
            "INSERT INTO intersection_treatments (cross_section_id, bus_gate, turn_conflict) \
             VALUES ($1, 'signal_controlled', 'right_in_right_out')",
        )
        .bind(cross_section_ids[1])
        .execute(pool)
        .await
        .unwrap();
        sqlx::query("UPDATE cross_sections SET bus_stop = 'bus_bulb' WHERE id = $1")
            .bind(cross_section_ids[2])
            .execute(pool)
            .await
            .unwrap();

        // Now run the migration under test (and everything after it, so the
        // final schema matches what the rest of the crate expects).
        test_utils::apply_migrations_in_range(pool, 28..).await;

        // The interior cross-section's bus gate / turn conflict survived onto an
        // Intersection, and that Intersection is the one its cross-section now
        // points at.
        let treated: Option<(String, String)> = sqlx::query_as(
            "SELECT i.bus_gate, i.turn_conflict FROM intersections i \
             JOIN cross_sections cs ON cs.intersection_id = i.id WHERE cs.id = $1",
        )
        .bind(cross_section_ids[1])
        .fetch_optional(pool)
        .await
        .unwrap();
        assert_eq!(
            treated,
            Some((
                "signal_controlled".to_string(),
                "right_in_right_out".to_string()
            )),
            "an INTERIOR cross-section's #027 bus_gate/turn_conflict must survive migration 028, \
             not be silently dropped by its DROP TABLE intersection_treatments"
        );

        // Same for the interior cross-section's bus_stop, whose column 028
        // drops.
        let bus_stop: Option<(Option<String>,)> = sqlx::query_as(
            "SELECT i.bus_stop FROM intersections i \
             JOIN cross_sections cs ON cs.intersection_id = i.id WHERE cs.id = $1",
        )
        .bind(cross_section_ids[2])
        .fetch_optional(pool)
        .await
        .unwrap();
        assert_eq!(
            bus_stop,
            Some((Some("bus_bulb".to_string()),)),
            "an INTERIOR cross-section's #027 bus_stop must survive migration 028, not be \
             silently dropped by its DROP COLUMN bus_stop"
        );

        // No treatment data ends up on an Intersection nothing references, and
        // the endpoint backfill still ran: 2 endpoints + 2 treated interior
        // rows = 4 Intersections, each referenced by its own cross-section.
        let intersection_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM intersections")
            .fetch_one(pool)
            .await
            .unwrap();
        assert_eq!(intersection_count, 4);
        let unreferenced: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM intersections i \
             WHERE NOT EXISTS (SELECT 1 FROM cross_sections cs WHERE cs.intersection_id = i.id)",
        )
        .fetch_one(pool)
        .await
        .unwrap();
        assert_eq!(unreferenced, 0);
    }

    /// When several cross-sections resolve onto ONE Intersection (two corridors
    /// meeting at the same `osm_node_id`) and more than one carries #027
    /// treatment data, the carry-over UPDATE has to pick a winner. It used to be
    /// whichever row Postgres happened to process last; it is now documented as
    /// the lowest `cross_sections.id`.
    #[tokio::test]
    async fn migration_028_treatment_carry_over_tie_break_is_deterministic() {
        let td = test_utils::setup_unmigrated().await;
        let pool = &td.db.pool;
        test_utils::apply_migrations_in_range(pool, ..=27).await;

        sqlx::query("INSERT INTO regions (id, name, timezone) VALUES (1, 'Test Region', 'UTC')")
            .execute(pool)
            .await
            .unwrap();
        let remix_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id) VALUES ('Remix', 1) RETURNING id",
        )
        .fetch_one(pool)
        .await
        .unwrap();

        // Two single-point corridors whose only cross-section shares one
        // `osm_node_id`, so 028's backfill matches both onto ONE Intersection.
        // Both carry a bus gate, and they disagree.
        let mut cross_section_ids = Vec::new();
        for (name, bus_gate) in [
            ("Corridor A", "signal_controlled"),
            ("Corridor B", "yield_controlled"),
        ] {
            let corridor_id: i64 = sqlx::query_scalar(
                "INSERT INTO corridors (name, geometry_source, remix_id) \
                 VALUES ($1, 'manual', $2) RETURNING id",
            )
            .bind(name)
            .bind(remix_id)
            .fetch_one(pool)
            .await
            .unwrap();
            let cross_section_id: i64 = sqlx::query_scalar(
                "INSERT INTO cross_sections (corridor_id, position, lat, lon, osm_node_id) \
                 VALUES ($1, 0, 45.500, -73.600, 999) RETURNING id",
            )
            .bind(corridor_id)
            .fetch_one(pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO intersection_treatments (cross_section_id, bus_gate) VALUES ($1, $2)",
            )
            .bind(cross_section_id)
            .bind(bus_gate)
            .execute(pool)
            .await
            .unwrap();
            cross_section_ids.push(cross_section_id);
        }

        test_utils::apply_migrations_in_range(pool, 28..).await;

        let intersection_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM intersections")
            .fetch_one(pool)
            .await
            .unwrap();
        assert_eq!(
            intersection_count, 1,
            "both cross-sections share one osm_node_id, so they share one Intersection"
        );
        let bus_gate: String = sqlx::query_scalar("SELECT bus_gate FROM intersections")
            .fetch_one(pool)
            .await
            .unwrap();
        assert_eq!(
            bus_gate, "signal_controlled",
            "the lower cross_section_id's value must win the tie-break"
        );
    }
}
