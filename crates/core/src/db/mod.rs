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
        db.migrate().await.unwrap();
        TestDb {
            db,
            _container: container,
        }
    }
}
