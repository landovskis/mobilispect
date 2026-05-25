use anyhow::Result;
use sqlx::PgPool;

pub async fn db_ping(pool: &PgPool) -> Result<()> {
    pool.acquire().await?;
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
