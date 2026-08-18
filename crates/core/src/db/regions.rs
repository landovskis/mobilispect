use anyhow::Result;
use sqlx::PgPool;

pub struct DbRegion {
    pub id: i64,
    pub name: String,
    pub timezone: String,
    pub min_lat: Option<f64>,
    pub min_lon: Option<f64>,
    pub max_lat: Option<f64>,
    pub max_lon: Option<f64>,
}

pub async fn load_regions(pool: &PgPool) -> Result<Vec<DbRegion>> {
    Ok(sqlx::query_as!(
        DbRegion,
        r#"SELECT id, name, timezone, min_lat, min_lon, max_lat, max_lon FROM regions"#
    )
    .fetch_all(pool)
    .await?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;

    #[tokio::test]
    async fn load_regions_returns_seeded_rows() {
        let td = test_utils::setup().await;
        sqlx::query!("INSERT INTO regions (id, name, timezone) VALUES (1, 'Test Region', 'UTC')")
            .execute(&td.db.pool)
            .await
            .unwrap();

        let regions = load_regions(&td.db.pool).await.unwrap();
        assert_eq!(regions.len(), 1);
        assert_eq!(regions[0].name, "Test Region");
        assert_eq!(regions[0].min_lat, None);
    }

    #[tokio::test]
    async fn load_regions_returns_populated_bbox() {
        let td = test_utils::setup().await;
        sqlx::query!(
            "INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon)
             VALUES (1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50)"
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let regions = load_regions(&td.db.pool).await.unwrap();
        assert_eq!(regions[0].min_lat, Some(45.40));
        assert_eq!(regions[0].max_lon, Some(-73.50));
    }
}
