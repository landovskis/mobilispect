use anyhow::Result;
use sqlx::PgPool;

pub struct DbFeed {
    pub id: i64,
    pub name: Option<String>,
    pub gtfs_static_url: String,
    pub gtfs_rt_vehicle_positions_url: Option<String>,
    pub gtfs_rt_trip_updates_url: Option<String>,
    pub transitland_onestop_id: Option<String>,
    pub gtfs_api_key: Option<String>,
    pub timezone: String,
}

pub async fn load_feeds(pool: &PgPool) -> Result<Vec<DbFeed>> {
    Ok(sqlx::query_as!(
        DbFeed,
        r#"SELECT id, name, gtfs_static_url,
                  gtfs_rt_vehicle_positions_url, gtfs_rt_trip_updates_url,
                  transitland_onestop_id, gtfs_api_key, timezone
           FROM feeds"#
    )
    .fetch_all(pool)
    .await?)
}

pub async fn load_feed_options(pool: &PgPool) -> Result<Vec<(String, String)>> {
    let rows = sqlx::query!(
        r#"SELECT f.id,
                  STRING_AGG(a.name, ' / ' ORDER BY a.name) AS "display_name!"
           FROM feeds f
           JOIN feed_agency_ids fai ON fai.feed_id = f.id
           JOIN agencies a ON a.onestop_id = fai.onestop_id
           GROUP BY f.id
           ORDER BY 2"#
    )
    .fetch_all(pool)
    .await?;
    Ok(rows
        .into_iter()
        .map(|r| (r.id.to_string(), r.display_name))
        .collect())
}

pub async fn store_discovered_feeds(
    pool: &PgPool,
    city: &str,
    feeds: &[crate::transitland::DiscoveredFeed],
) -> Result<()> {
    let mut tx = pool.begin().await?;

    sqlx::query!(
        "INSERT INTO regions (id, name, timezone)
         VALUES (1, $1, $2)
         ON CONFLICT (id) DO UPDATE SET name = $1, timezone = $2",
        city,
        feeds.first().map(|f| f.timezone.as_str()).unwrap_or("UTC"),
    )
    .execute(&mut *tx)
    .await?;

    sqlx::query!(
        "INSERT INTO networks (id, region_id, name)
         VALUES (1, 1, $1)
         ON CONFLICT (id) DO UPDATE SET name = $1",
        city,
    )
    .execute(&mut *tx)
    .await?;

    let max_id: i64 = sqlx::query_scalar!("SELECT COALESCE(MAX(id), 0) FROM feeds")
        .fetch_one(&mut *tx)
        .await?
        .expect("COALESCE(MAX(id), 0) is never NULL");

    for (i, feed) in feeds.iter().enumerate() {
        let new_id = max_id + 1 + i as i64;
        let rows = sqlx::query!(
            "INSERT INTO feeds (id, name, gtfs_static_url,
                                gtfs_rt_vehicle_positions_url, gtfs_rt_trip_updates_url,
                                transitland_onestop_id, timezone)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (transitland_onestop_id) DO NOTHING",
            new_id,
            feed.name,
            feed.gtfs_static_url,
            feed.gtfs_rt_vehicle_positions_url,
            feed.gtfs_rt_trip_updates_url,
            feed.onestop_id,
            feed.timezone,
        )
        .execute(&mut *tx)
        .await?;

        let actual_id = if rows.rows_affected() == 0 {
            sqlx::query_scalar!(
                "SELECT id FROM feeds WHERE transitland_onestop_id = $1",
                feed.onestop_id
            )
            .fetch_one(&mut *tx)
            .await?
        } else {
            new_id
        };

        sqlx::query!(
            "INSERT INTO network_feeds (network_id, feed_id) VALUES (1, $1)
             ON CONFLICT (network_id, feed_id) DO NOTHING",
            actual_id,
        )
        .execute(&mut *tx)
        .await?;
    }

    tx.commit().await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;
    use crate::transitland::DiscoveredFeed;

    #[tokio::test]
    async fn load_feeds_returns_empty_when_table_is_empty() {
        let td = test_utils::setup().await;
        let feeds = load_feeds(&td.db.pool).await.unwrap();
        assert!(feeds.is_empty());
    }

    #[tokio::test]
    async fn load_feeds_returns_stored_feed() {
        let td = test_utils::setup().await;
        sqlx::query!(
            "INSERT INTO feeds (id, gtfs_static_url, name, transitland_onestop_id, timezone)
             VALUES (1, 'https://example.com/gtfs.zip', 'Test Feed', 'f-test', 'America/Toronto')"
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        let feeds = load_feeds(&td.db.pool).await.unwrap();
        assert_eq!(feeds.len(), 1);
        assert_eq!(feeds[0].id, 1);
        assert_eq!(feeds[0].gtfs_static_url, "https://example.com/gtfs.zip");
        assert_eq!(feeds[0].name.as_deref(), Some("Test Feed"));
        assert_eq!(feeds[0].timezone, "America/Toronto");
    }

    #[tokio::test]
    async fn store_discovered_feeds_inserts_region_network_and_feeds() {
        let td = test_utils::setup().await;
        let discovered = vec![DiscoveredFeed {
            onestop_id: "f-f25d-stm".to_string(),
            name: "STM".to_string(),
            gtfs_static_url: "https://stm.info/gtfs.zip".to_string(),
            gtfs_rt_vehicle_positions_url: Some("https://stm.info/vp.pb".to_string()),
            gtfs_rt_trip_updates_url: None,
            timezone: "America/Toronto".to_string(),
        }];

        store_discovered_feeds(&td.db.pool, "Montreal", &discovered)
            .await
            .unwrap();

        let region_count: i64 = sqlx::query_scalar!("SELECT COUNT(*) FROM regions")
            .fetch_one(&td.db.pool)
            .await
            .unwrap()
            .unwrap();
        assert_eq!(region_count, 1);

        let feed = load_feeds(&td.db.pool).await.unwrap();
        assert_eq!(feed.len(), 1);
        assert_eq!(
            feed[0].transitland_onestop_id.as_deref(),
            Some("f-f25d-stm")
        );
        assert_eq!(feed[0].timezone, "America/Toronto");
    }

    #[tokio::test]
    async fn store_discovered_feeds_is_idempotent_on_retry() {
        let td = test_utils::setup().await;
        let discovered = vec![DiscoveredFeed {
            onestop_id: "f-f25d-stm".to_string(),
            name: "STM".to_string(),
            gtfs_static_url: "https://stm.info/gtfs.zip".to_string(),
            gtfs_rt_vehicle_positions_url: None,
            gtfs_rt_trip_updates_url: None,
            timezone: "America/Toronto".to_string(),
        }];

        store_discovered_feeds(&td.db.pool, "Montreal", &discovered)
            .await
            .unwrap();
        store_discovered_feeds(&td.db.pool, "Montreal", &discovered)
            .await
            .unwrap();

        let count: i64 = sqlx::query_scalar!("SELECT COUNT(*) FROM feeds")
            .fetch_one(&td.db.pool)
            .await
            .unwrap()
            .unwrap();
        assert_eq!(count, 1);
    }
}
