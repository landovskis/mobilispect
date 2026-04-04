use anyhow::Result;
use gtfs_structures::{DirectionType, Gtfs, RouteType};
use tracing::{info, warn};

use crate::db::Database;

/// Rows to bundle per INSERT statement. SQLite limit is 999 params (32766 in recent builds).
/// 5 params per scheduled_stop row → 500 rows = 2500 params, well within limits.
const CHUNK: usize = 500;

/// Load static GTFS data into the database if not already present.
/// Re-loads if the feed version has changed.
pub async fn load_if_needed(db: &Database, gtfs_url: &str) -> Result<()> {
    let stored_version = get_stored_version(db).await?;

    // Download and parse the GTFS zip (blocking I/O — run on thread pool)
    info!("Downloading static GTFS from {}", gtfs_url);
    let url = gtfs_url.to_string();
    let gtfs = tokio::task::spawn_blocking(move || {
        Gtfs::from_url(&url).map_err(|e| anyhow::anyhow!("Failed to load GTFS: {}", e))
    })
    .await??;

    let feed_version = gtfs
        .feed_info
        .first()
        .and_then(|f| f.version.clone())
        .unwrap_or_else(|| "unknown".to_string());

    if stored_version.as_deref() == Some(&feed_version) {
        info!("Static GTFS already up to date (version: {})", feed_version);
        return Ok(());
    }

    info!(
        "Loading static GTFS version: {} ({} routes, {} trips, {} stops)",
        feed_version,
        gtfs.routes.len(),
        gtfs.trips.len(),
        gtfs.stops.len()
    );

    // Enable bulk-load optimisations for this session
    sqlx::query("PRAGMA journal_mode = WAL").execute(&db.pool).await?;
    sqlx::query("PRAGMA synchronous = OFF").execute(&db.pool).await?;
    sqlx::query("PRAGMA cache_size = -64000").execute(&db.pool).await?; // 64 MB cache

    // Drop stale data and bulk-insert in one transaction
    let mut tx = db.pool.begin().await?;
    for table in &["scheduled_stops", "trips", "stops", "routes"] {
        sqlx::query(&format!("DELETE FROM {table}"))
            .execute(&mut *tx)
            .await?;
    }
    load_routes(&mut tx, &gtfs).await?;
    load_trips(&mut tx, &gtfs).await?;
    load_stops(&mut tx, &gtfs).await?;
    load_scheduled_stops(&mut tx, &gtfs).await?;
    tx.commit().await?;

    // Restore safe sync mode after bulk load
    sqlx::query("PRAGMA synchronous = NORMAL").execute(&db.pool).await?;

    set_stored_version(db, &feed_version).await?;
    info!("Static GTFS load complete");
    Ok(())
}

fn route_type_to_int(rt: &RouteType) -> i64 {
    match rt {
        RouteType::Tramway => 0,
        RouteType::Subway => 1,
        RouteType::Rail => 2,
        RouteType::Bus => 3,
        RouteType::Ferry => 4,
        RouteType::CableCar => 5,
        RouteType::Gondola => 6,
        RouteType::Funicular => 7,
        RouteType::Coach => 200,
        RouteType::Air => 1100,
        RouteType::Taxi => 1500,
        RouteType::Other(n) => *n as i64,
    }
}

fn direction_to_int(d: &DirectionType) -> i64 {
    match d {
        DirectionType::Outbound => 0,
        DirectionType::Inbound => 1,
    }
}

type Tx<'a> = sqlx::Transaction<'a, sqlx::Sqlite>;

async fn load_routes(tx: &mut Tx<'_>, gtfs: &Gtfs) -> Result<()> {
    let rows: Vec<(String, String, String, i64)> = gtfs
        .routes
        .iter()
        .map(|(id, r)| {
            (
                id.clone(),
                r.short_name.clone(),
                r.long_name.clone(),
                route_type_to_int(&r.route_type),
            )
        })
        .collect();

    for chunk in rows.chunks(CHUNK) {
        let placeholders = chunk.iter().map(|_| "(?,?,?,?)").collect::<Vec<_>>().join(",");
        let sql = format!(
            "INSERT OR REPLACE INTO routes (route_id, short_name, long_name, route_type) VALUES {placeholders}"
        );
        let mut q = sqlx::query(&sql);
        for (id, short, long, rt) in chunk {
            q = q.bind(id).bind(short).bind(long).bind(rt);
        }
        q.execute(&mut **tx).await?;
    }
    info!("Loaded {} routes", gtfs.routes.len());
    Ok(())
}

async fn load_trips(tx: &mut Tx<'_>, gtfs: &Gtfs) -> Result<()> {
    let rows: Vec<(String, String, String, Option<i64>, Option<String>)> = gtfs
        .trips
        .iter()
        .map(|(id, t)| {
            (
                id.clone(),
                t.route_id.clone(),
                t.service_id.clone(),
                t.direction_id.as_ref().map(direction_to_int),
                t.trip_headsign.clone(),
            )
        })
        .collect();

    for chunk in rows.chunks(CHUNK) {
        let placeholders = chunk.iter().map(|_| "(?,?,?,?,?)").collect::<Vec<_>>().join(",");
        let sql = format!(
            "INSERT OR REPLACE INTO trips (trip_id, route_id, service_id, direction_id, trip_headsign) VALUES {placeholders}"
        );
        let mut q = sqlx::query(&sql);
        for (id, route, svc, dir, head) in chunk {
            q = q.bind(id).bind(route).bind(svc).bind(dir).bind(head);
        }
        q.execute(&mut **tx).await?;
    }
    info!("Loaded {} trips", gtfs.trips.len());
    Ok(())
}

async fn load_stops(tx: &mut Tx<'_>, gtfs: &Gtfs) -> Result<()> {
    let mut rows: Vec<(String, String, f64, f64)> = Vec::new();
    for (id, stop) in &gtfs.stops {
        match (stop.latitude, stop.longitude) {
            (Some(lat), Some(lon)) => rows.push((id.clone(), stop.name.clone(), lat, lon)),
            _ => warn!("Stop {} missing coordinates, skipping", id),
        }
    }

    for chunk in rows.chunks(CHUNK) {
        let placeholders = chunk.iter().map(|_| "(?,?,?,?)").collect::<Vec<_>>().join(",");
        let sql = format!(
            "INSERT OR REPLACE INTO stops (stop_id, stop_name, stop_lat, stop_lon) VALUES {placeholders}"
        );
        let mut q = sqlx::query(&sql);
        for (id, name, lat, lon) in chunk {
            q = q.bind(id).bind(name).bind(lat).bind(lon);
        }
        q.execute(&mut **tx).await?;
    }
    info!("Loaded {} stops", rows.len());
    Ok(())
}

async fn load_scheduled_stops(tx: &mut Tx<'_>, gtfs: &Gtfs) -> Result<()> {
    // Flatten all stop_times into (trip_id, stop_id, seq, arrival, departure)
    let mut rows: Vec<(String, String, i64, String, String)> = Vec::new();
    for (trip_id, trip) in &gtfs.trips {
        for st in &trip.stop_times {
            rows.push((
                trip_id.clone(),
                st.stop.id.clone(),
                st.stop_sequence as i64,
                format_gtfs_time(st.arrival_time),
                format_gtfs_time(st.departure_time),
            ));
        }
    }

    let total = rows.len();
    for (i, chunk) in rows.chunks(CHUNK).enumerate() {
        let placeholders = chunk.iter().map(|_| "(?,?,?,?,?)").collect::<Vec<_>>().join(",");
        let sql = format!(
            "INSERT OR REPLACE INTO scheduled_stops \
             (trip_id, stop_id, stop_sequence, arrival_time, departure_time) VALUES {placeholders}"
        );
        let mut q = sqlx::query(&sql);
        for (tid, sid, seq, arr, dep) in chunk {
            q = q.bind(tid).bind(sid).bind(seq).bind(arr).bind(dep);
        }
        q.execute(&mut **tx).await?;

        // Progress every 100k rows
        let done = (i + 1) * CHUNK;
        if done % 100_000 < CHUNK {
            info!("  scheduled_stops: {}/{} rows", done.min(total), total);
        }
    }
    info!("Loaded {} scheduled stops", total);
    Ok(())
}

/// Format GTFS time (seconds-since-midnight) as HH:MM:SS.
/// GTFS allows times >= 24:00:00 for overnight service.
fn format_gtfs_time(secs: Option<u32>) -> String {
    match secs {
        None => "00:00:00".to_string(),
        Some(s) => {
            let h = s / 3600;
            let m = (s % 3600) / 60;
            let sec = s % 60;
            format!("{h:02}:{m:02}:{sec:02}")
        }
    }
}

async fn get_stored_version(db: &Database) -> Result<Option<String>> {
    let row = sqlx::query!(
        "SELECT value FROM feed_info WHERE key = 'gtfs_static_version'"
    )
    .fetch_optional(&db.pool)
    .await?;
    Ok(row.map(|r| r.value))
}

async fn set_stored_version(db: &Database, version: &str) -> Result<()> {
    let now = chrono::Utc::now().to_rfc3339();
    sqlx::query!(
        "INSERT OR REPLACE INTO feed_info (key, value, updated_at) VALUES ('gtfs_static_version', ?, ?)",
        version,
        now,
    )
    .execute(&db.pool)
    .await?;
    Ok(())
}
