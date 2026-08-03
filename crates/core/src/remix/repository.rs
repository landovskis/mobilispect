//! Remix repository: the imperative I/O shell for regions, remixes, and the
//! corridors that belong to them. Pure logic (highlight rule, GeoJSON
//! assembly) lives in `highlight.rs`/`geojson.rs`; this module persists and
//! reads — no validation or geometry computation happens here.

use sqlx::PgPool;

use crate::corridor_design::GeometrySource;
use crate::ids::{CorridorId, CrossSectionId, RegionId, RemixId};
use crate::remix::highlight::is_corridor_edited;
use crate::remix::{BoundingBox, CorridorForMap, CrossSectionPointForMap, Region, Remix};

/// Regions with a bounding box already set, ordered by name — the only
/// regions that appear in the metro-region picker. See the design spec's
/// note on bounding-box population (manual one-time operator step, no admin
/// UI yet).
pub async fn list_regions_with_bounding_box(pool: &PgPool) -> anyhow::Result<Vec<Region>> {
    let rows = sqlx::query!(
        r#"SELECT id, name, min_lat, min_lon, max_lat, max_lon
           FROM regions
           WHERE min_lat IS NOT NULL AND min_lon IS NOT NULL
             AND max_lat IS NOT NULL AND max_lon IS NOT NULL
           ORDER BY name"#
    )
    .fetch_all(pool)
    .await?;

    Ok(rows
        .into_iter()
        .filter_map(|row| {
            let region_id = row.id;
            let bounding_box = BoundingBox {
                // Safe: the WHERE clause guarantees these four columns are non-null.
                min_lat: row.min_lat.unwrap(),
                min_lon: row.min_lon.unwrap(),
                max_lat: row.max_lat.unwrap(),
                max_lon: row.max_lon.unwrap(),
            };
            if bounding_box.validate().is_err() {
                tracing::warn!(
                    region_id,
                    "region has an invalid bounding box; excluding it from the metro-region picker"
                );
                return None;
            }
            Some(Region {
                id: RegionId::from(region_id),
                name: row.name,
                bounding_box,
            })
        })
        .collect())
}

pub async fn insert_remix(
    pool: &PgPool,
    name: &str,
    region_id: RegionId,
) -> anyhow::Result<RemixId> {
    let row = sqlx::query!(
        "INSERT INTO remixes (name, region_id) VALUES ($1, $2) RETURNING id",
        name,
        region_id.as_i64(),
    )
    .fetch_one(pool)
    .await?;
    Ok(RemixId::from(row.id))
}

pub async fn list_remixes_for_region(
    pool: &PgPool,
    region_id: RegionId,
) -> anyhow::Result<Vec<Remix>> {
    let rows = sqlx::query!(
        "SELECT id, name, region_id FROM remixes WHERE region_id = $1 ORDER BY updated_at DESC",
        region_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    Ok(rows
        .into_iter()
        .map(|row| Remix {
            id: RemixId::from(row.id),
            name: row.name,
            region_id: RegionId::from(row.region_id),
        })
        .collect())
}

/// A remix plus its region (with bounding box), for `GET /api/remixes/:id`.
/// Returns `Ok(None)` if `remix_id` doesn't exist. Returns `Err` (rather
/// than silently defaulting) if the remix's region has no bounding box —
/// that can only happen if an operator cleared a bbox out from under an
/// existing remix, a data-integrity problem worth surfacing loudly.
pub async fn get_remix(
    pool: &PgPool,
    remix_id: RemixId,
) -> anyhow::Result<Option<(Remix, Region)>> {
    let row = sqlx::query!(
        r#"SELECT r.id AS remix_id, r.name AS remix_name, r.region_id,
                  reg.name AS region_name, reg.min_lat, reg.min_lon, reg.max_lat, reg.max_lon
           FROM remixes r
           JOIN regions reg ON reg.id = r.region_id
           WHERE r.id = $1"#,
        remix_id.as_i64(),
    )
    .fetch_optional(pool)
    .await?;

    let Some(row) = row else {
        return Ok(None);
    };

    let (Some(min_lat), Some(min_lon), Some(max_lat), Some(max_lon)) =
        (row.min_lat, row.min_lon, row.max_lat, row.max_lon)
    else {
        anyhow::bail!(
            "remix {} references region {} which has no bounding box set",
            remix_id,
            row.region_id
        );
    };

    Ok(Some((
        Remix {
            id: RemixId::from(row.remix_id),
            name: row.remix_name,
            region_id: RegionId::from(row.region_id),
        },
        Region {
            id: RegionId::from(row.region_id),
            name: row.region_name,
            bounding_box: BoundingBox {
                min_lat,
                min_lon,
                max_lat,
                max_lon,
            },
        },
    )))
}

/// A remix's corridors with their ordered cross-sections and highlight
/// state, for the region map's GeoJSON overlay (see `geojson.rs`).
pub async fn list_corridors_for_remix(
    pool: &PgPool,
    remix_id: RemixId,
) -> anyhow::Result<Vec<CorridorForMap>> {
    let rows = sqlx::query!(
        r#"SELECT c.id AS corridor_id, c.geometry_source, c.created_at, c.updated_at,
                  cs.id AS cross_section_id, cs.lat, cs.lon
           FROM corridors c
           JOIN cross_sections cs ON cs.corridor_id = c.id
           WHERE c.remix_id = $1
           ORDER BY c.id, cs.position"#,
        remix_id.as_i64(),
    )
    .fetch_all(pool)
    .await?;

    let mut corridors: Vec<CorridorForMap> = Vec::new();

    for row in rows {
        let geometry_source =
            GeometrySource::from_db_str(&row.geometry_source).ok_or_else(|| {
                anyhow::anyhow!("unknown geometry_source value: {}", row.geometry_source)
            })?;
        let highlighted = is_corridor_edited(geometry_source, row.created_at, row.updated_at);
        let point = CrossSectionPointForMap {
            cross_section_id: CrossSectionId::from(row.cross_section_id),
            lat: row.lat,
            lon: row.lon,
        };
        let corridor_id = CorridorId::from(row.corridor_id);

        match corridors.last_mut() {
            Some(last) if last.corridor_id == corridor_id => {
                last.cross_sections.push(point);
            }
            _ => corridors.push(CorridorForMap {
                corridor_id,
                highlighted,
                cross_sections: vec![point],
            }),
        }
    }

    Ok(corridors)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;

    async fn seed_region(
        pool: &PgPool,
        id: i64,
        name: &str,
        bbox: Option<BoundingBox>,
    ) -> RegionId {
        match bbox {
            Some(b) => {
                sqlx::query!(
                    "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon)
                     VALUES ($1, $2, 'UTC', $3, $4, $5, $6)",
                    id,
                    name,
                    b.min_lat,
                    b.min_lon,
                    b.max_lat,
                    b.max_lon,
                )
                .execute(pool)
                .await
                .unwrap();
            }
            None => {
                sqlx::query!(
                    "INSERT INTO regions (id, name, timezone) VALUES ($1, $2, 'UTC')",
                    id,
                    name,
                )
                .execute(pool)
                .await
                .unwrap();
            }
        }
        RegionId::from(id)
    }

    fn sample_bbox() -> BoundingBox {
        BoundingBox {
            min_lat: 45.40,
            min_lon: -73.70,
            max_lat: 45.60,
            max_lon: -73.50,
        }
    }

    #[tokio::test]
    async fn list_regions_with_bounding_box_excludes_regions_missing_a_bbox() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        seed_region(pool, 1, "Has Bbox", Some(sample_bbox())).await;
        seed_region(pool, 2, "No Bbox", None).await;

        let regions = list_regions_with_bounding_box(pool).await.unwrap();

        assert_eq!(regions.len(), 1);
        assert_eq!(regions[0].name, "Has Bbox");
    }

    #[tokio::test]
    async fn list_regions_with_bounding_box_excludes_regions_with_an_invalid_bounding_box() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let invalid_bbox = BoundingBox {
            min_lat: 45.60,
            min_lon: -73.70,
            max_lat: 45.40, // min_lat > max_lat: degenerate
            max_lon: -73.50,
        };
        seed_region(pool, 1, "Invalid Bbox", Some(invalid_bbox)).await;

        let regions = list_regions_with_bounding_box(pool).await.unwrap();

        assert_eq!(regions.len(), 0);
    }

    #[tokio::test]
    async fn list_regions_with_bounding_box_returns_correct_bbox_values() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let bbox = sample_bbox();
        seed_region(pool, 1, "Test Region", Some(bbox)).await;

        let regions = list_regions_with_bounding_box(pool).await.unwrap();

        assert_eq!(regions.len(), 1);
        assert_eq!(regions[0].bounding_box, bbox);
    }

    #[tokio::test]
    async fn insert_remix_persists_a_row_with_the_correct_region() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;

        let remix_id = insert_remix(pool, "Downtown bike lanes", region_id)
            .await
            .unwrap();

        let row: (String, i64) =
            sqlx::query_as("SELECT name, region_id FROM remixes WHERE id = $1")
                .bind(remix_id.as_i64())
                .fetch_one(pool)
                .await
                .unwrap();
        assert_eq!(row.0, "Downtown bike lanes");
        assert_eq!(row.1, region_id.as_i64());
    }

    #[tokio::test]
    async fn list_remixes_for_region_orders_most_recently_updated_first() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;

        let older_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id, updated_at) \
             VALUES ($1, $2, now() - interval '1 day') RETURNING id",
        )
        .bind("Older remix")
        .bind(region_id.as_i64())
        .fetch_one(pool)
        .await
        .unwrap();
        let newer_id: i64 = sqlx::query_scalar(
            "INSERT INTO remixes (name, region_id, updated_at) VALUES ($1, $2, now()) RETURNING id",
        )
        .bind("Newer remix")
        .bind(region_id.as_i64())
        .fetch_one(pool)
        .await
        .unwrap();

        let remixes = list_remixes_for_region(pool, region_id).await.unwrap();

        assert_eq!(remixes.len(), 2);
        assert_eq!(remixes[0].id, RemixId::from(newer_id));
        assert_eq!(remixes[1].id, RemixId::from(older_id));
    }

    #[tokio::test]
    async fn get_remix_returns_none_for_unknown_id() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;

        let result = get_remix(pool, RemixId::from(999_999)).await.unwrap();

        assert!(result.is_none());
    }

    #[tokio::test]
    async fn get_remix_returns_remix_and_region_with_bbox() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Downtown bike lanes", region_id)
            .await
            .unwrap();

        let (remix, region) = get_remix(pool, remix_id).await.unwrap().unwrap();

        assert_eq!(remix.id, remix_id);
        assert_eq!(remix.name, "Downtown bike lanes");
        assert_eq!(region.id, region_id);
        assert_eq!(region.name, "Test Region");
        assert_eq!(region.bounding_box, sample_bbox());
    }

    #[tokio::test]
    async fn get_remix_errors_when_regions_bbox_was_cleared_after_remix_creation() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Downtown bike lanes", region_id)
            .await
            .unwrap();
        sqlx::query(
            "UPDATE regions SET min_lat = NULL, min_lon = NULL, max_lat = NULL, max_lon = NULL \
             WHERE id = $1",
        )
        .bind(region_id.as_i64())
        .execute(pool)
        .await
        .unwrap();

        let result = get_remix(pool, remix_id).await;

        assert!(result.is_err());
    }

    async fn seed_corridor(
        pool: &PgPool,
        remix_id: RemixId,
        name: &str,
        geometry_source: &str,
        points: &[(f64, f64)],
    ) -> CorridorId {
        let corridor_id: i64 = sqlx::query_scalar(
            "INSERT INTO corridors (name, geometry_source, remix_id) VALUES ($1, $2, $3) \
             RETURNING id",
        )
        .bind(name)
        .bind(geometry_source)
        .bind(remix_id.as_i64())
        .fetch_one(pool)
        .await
        .unwrap();

        for (i, (lat, lon)) in points.iter().enumerate() {
            sqlx::query(
                "INSERT INTO cross_sections (corridor_id, position, lat, lon) \
                 VALUES ($1, $2, $3, $4)",
            )
            .bind(corridor_id)
            .bind(i as f64)
            .bind(lat)
            .bind(lon)
            .execute(pool)
            .await
            .unwrap();
        }

        CorridorId::from(corridor_id)
    }

    #[tokio::test]
    async fn list_corridors_for_remix_returns_ordered_cross_sections() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Test Remix", region_id).await.unwrap();
        let corridor_id = seed_corridor(
            pool,
            remix_id,
            "Main St",
            "manual",
            &[(45.50, -73.60), (45.51, -73.59), (45.52, -73.58)],
        )
        .await;

        let corridors = list_corridors_for_remix(pool, remix_id).await.unwrap();

        assert_eq!(corridors.len(), 1);
        assert_eq!(corridors[0].corridor_id, corridor_id);
        assert_eq!(corridors[0].cross_sections.len(), 3);
        assert_eq!(corridors[0].cross_sections[0].lat, 45.50);
        assert_eq!(corridors[0].cross_sections[2].lat, 45.52);
    }

    #[tokio::test]
    async fn list_corridors_for_remix_marks_manual_corridors_highlighted() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Test Remix", region_id).await.unwrap();
        seed_corridor(
            pool,
            remix_id,
            "Manual corridor",
            "manual",
            &[(45.50, -73.60), (45.51, -73.59)],
        )
        .await;

        let corridors = list_corridors_for_remix(pool, remix_id).await.unwrap();

        assert_eq!(corridors.len(), 1);
        assert!(corridors[0].highlighted);
    }

    #[tokio::test]
    async fn list_corridors_for_remix_marks_untouched_imported_corridors_not_highlighted() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_id = insert_remix(pool, "Test Remix", region_id).await.unwrap();
        seed_corridor(
            pool,
            remix_id,
            "Imported corridor",
            "imported",
            &[(45.50, -73.60), (45.51, -73.59)],
        )
        .await;

        let corridors = list_corridors_for_remix(pool, remix_id).await.unwrap();

        assert_eq!(corridors.len(), 1);
        assert!(!corridors[0].highlighted);
    }

    #[tokio::test]
    async fn list_corridors_for_remix_excludes_corridors_outside_the_remix() {
        let td = test_utils::setup().await;
        let pool = &td.db.pool;
        let region_id = seed_region(pool, 1, "Test Region", Some(sample_bbox())).await;
        let remix_a = insert_remix(pool, "Remix A", region_id).await.unwrap();
        let remix_b = insert_remix(pool, "Remix B", region_id).await.unwrap();
        seed_corridor(
            pool,
            remix_a,
            "In remix A",
            "manual",
            &[(45.50, -73.60), (45.51, -73.59)],
        )
        .await;
        seed_corridor(
            pool,
            remix_b,
            "In remix B",
            "manual",
            &[(46.00, -74.00), (46.01, -74.01)],
        )
        .await;

        let corridors = list_corridors_for_remix(pool, remix_a).await.unwrap();

        assert_eq!(corridors.len(), 1);
        assert_eq!(corridors[0].cross_sections[0].lat, 45.50);
    }
}
