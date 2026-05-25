use anyhow::Result;
use sqlx::PgPool;

pub async fn db_ping(pool: &PgPool) -> Result<()> {
    // Runtime query intentional: SELECT 1 is a constant, parameter-free probe with
    // no type-safety concerns, and compile-time checking would require updating the
    // sqlx offline cache. pool.acquire() is insufficient — it may return a cached
    // connection without a real round-trip.
    sqlx::query("SELECT 1").execute(pool).await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;

    #[tokio::test]
    async fn db_ping_succeeds_with_live_db() {
        let td = test_utils::setup().await;
        assert!(db_ping(&td.db.pool).await.is_ok());
    }
}
