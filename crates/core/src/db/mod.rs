use anyhow::Result;
use sqlx::PgPool;
use sqlx::postgres::{PgConnectOptions, PgPoolOptions};
use std::str::FromStr;

#[derive(Clone, Debug)]
pub struct Database {
    pub pool: PgPool,
}

impl Database {
    pub async fn connect(database_url: &str) -> Result<Self> {
        let options = PgConnectOptions::from_str(database_url)?;
        let pool = PgPoolOptions::new()
            .max_connections(5)
            .connect_with(options)
            .await?;
        Ok(Self { pool })
    }

    pub async fn migrate(&self) -> Result<()> {
        sqlx::migrate!("./migrations").run(&self.pool).await?;
        Ok(())
    }
}

#[cfg(any(test, feature = "test-utils"))]
pub mod test_utils {
    use super::Database;
    use sqlx::PgPool;
    use std::sync::atomic::{AtomicU64, Ordering};
    use testcontainers::{ContainerAsync, runners::AsyncRunner};
    use testcontainers_modules::postgres::Postgres;
    use tokio::sync::OnceCell;

    static CONTAINER: OnceCell<ContainerAsync<Postgres>> = OnceCell::const_new();
    static DB_COUNTER: AtomicU64 = AtomicU64::new(0);

    async fn container_port() -> u16 {
        let container = CONTAINER
            .get_or_init(|| async {
                use testcontainers::ImageExt;
                Postgres::default()
                    .with_tag("16-alpine")
                    .start()
                    .await
                    .unwrap()
            })
            .await;
        container.get_host_port_ipv4(5432).await.unwrap()
    }

    pub struct TestDb {
        pub db: Database,
    }

    pub async fn setup() -> TestDb {
        let port = container_port().await;
        let db_name = format!("test_{}", DB_COUNTER.fetch_add(1, Ordering::Relaxed));

        let admin = PgPool::connect(&format!(
            "postgres://postgres:postgres@127.0.0.1:{port}/postgres"
        ))
        .await
        .unwrap();
        sqlx::query(&format!("CREATE DATABASE {db_name}"))
            .execute(&admin)
            .await
            .unwrap();

        let db = Database::connect(&format!(
            "postgres://postgres:postgres@127.0.0.1:{port}/{db_name}"
        ))
        .await
        .unwrap();
        db.migrate().await.unwrap();
        TestDb { db }
    }
}
