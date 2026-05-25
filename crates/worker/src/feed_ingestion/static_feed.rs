use anyhow::Result;
use chrono::Datelike;
use chrono::Utc;
use gtfs_structures::{DirectionType, Exception, Gtfs, RouteType};
use hex;
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use tracing::{info, warn};

use mobilispect_core::config::AgencyConfig;
use mobilispect_core::db::Database;
use mobilispect_core::ids::AgencyId;

type TripRow = (String, String, String, String, Option<i64>, Option<String>);
type PatternKey = (String, i64, String);
type PatternVal = (String, Vec<String>, Option<String>);
#[cfg(test)]
type CalendarRow = (String, bool, bool, bool, bool, bool, bool, bool);

/// Rows to bundle per INSERT statement.
const CHUNK: usize = 500;

/// Load static GTFS data into the database if not already present for this agency.
/// Re-loads if the feed version has changed.
pub async fn load_if_needed(db: &Database, agency: &AgencyConfig) -> Result<()> {
    let agency_id = AgencyId::from(agency.id);
    let stored_version = get_stored_version(db, &agency_id).await?;
    let last_download = get_last_download(db, &agency_id).await?;

    if let Some(last) = last_download {
        let last_date = chrono::NaiveDate::parse_from_str(&last, "%Y-%m-%d").ok();
        let today = Utc::now().date_naive();
        if let Some(_date) = last_date.filter(|d| *d >= today) {
            info!("Static GTFS already downloaded today, skipping download");
            return Ok(());
        }
    }

    // Download and parse the GTFS zip (blocking I/O — run on thread pool)
    info!("Downloading static GTFS from {}", agency.gtfs_static_url);
    let url = agency.gtfs_static_url.clone();
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
        "Loading static GTFS for {} version: {} ({} routes, {} trips, {} stops)",
        agency_id,
        feed_version,
        gtfs.routes.len(),
        gtfs.trips.len(),
        gtfs.stops.len()
    );

    // Drop stale data for this agency and bulk-insert in one transaction
    let mut tx = db.pool.begin().await?;
    sqlx::query("DELETE FROM route_variant_stops WHERE agency_id = $1")
        .bind(agency_id.as_str())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM route_variants WHERE agency_id = $1")
        .bind(agency_id.as_str())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM scheduled_stops WHERE agency_id = $1")
        .bind(agency_id.as_str())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM trips WHERE agency_id = $1")
        .bind(agency_id.as_str())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM stops WHERE agency_id = $1")
        .bind(agency_id.as_str())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM routes WHERE agency_id = $1")
        .bind(agency_id.as_str())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM calendar WHERE agency_id = $1")
        .bind(agency_id.as_str())
        .execute(&mut *tx)
        .await?;

    load_routes(&mut tx, &agency_id, &gtfs).await?;
    load_trips(&mut tx, &agency_id, &gtfs).await?;
    load_stops(&mut tx, &agency_id, &gtfs).await?;
    load_scheduled_stops(&mut tx, &agency_id, &gtfs).await?;
    load_calendar(&mut tx, &agency_id, &gtfs.calendar).await?;
    load_calendar_from_dates(&mut tx, &agency_id, &gtfs.calendar_dates).await?;
    load_variants(&mut tx, &agency_id, &gtfs).await?;
    tx.commit().await?;

    set_stored_version(db, &agency_id, &feed_version).await?;
    info!("Static GTFS load complete for {}", agency_id);
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

type Tx<'a> = sqlx::Transaction<'a, sqlx::Postgres>;

/// Generate Postgres-style numbered placeholders for a bulk INSERT.
/// E.g. pg_placeholders(2, 3) → "($1,$2,$3),($4,$5,$6)"
fn pg_placeholders(rows: usize, cols: usize) -> String {
    (0..rows)
        .map(|r| {
            let params = (0..cols)
                .map(|c| format!("${}", r * cols + c + 1))
                .collect::<Vec<_>>()
                .join(",");
            format!("({params})")
        })
        .collect::<Vec<_>>()
        .join(",")
}

async fn load_routes(tx: &mut Tx<'_>, agency_id: &AgencyId, gtfs: &Gtfs) -> Result<()> {
    let rows: Vec<(String, String, String, String, i64)> = gtfs
        .routes
        .iter()
        .map(|(id, r)| {
            (
                agency_id.0.clone(),
                id.clone(),
                r.short_name.clone().unwrap_or_default(),
                r.long_name.clone().unwrap_or_default(),
                route_type_to_int(&r.route_type),
            )
        })
        .collect();

    for chunk in rows.chunks(CHUNK) {
        let placeholders = pg_placeholders(chunk.len(), 5);
        let sql = format!(
            "INSERT INTO routes (agency_id, route_id, short_name, long_name, route_type) VALUES {placeholders}
             ON CONFLICT (agency_id, route_id) DO UPDATE SET
               short_name = EXCLUDED.short_name,
               long_name = EXCLUDED.long_name,
               route_type = EXCLUDED.route_type"
        );
        let mut q = sqlx::query(&sql);
        for (aid, id, short, long, rt) in chunk {
            q = q.bind(aid).bind(id).bind(short).bind(long).bind(rt);
        }
        q.execute(&mut **tx).await?;
    }
    info!("Loaded {} routes", gtfs.routes.len());
    Ok(())
}

async fn load_trips(tx: &mut Tx<'_>, agency_id: &AgencyId, gtfs: &Gtfs) -> Result<()> {
    let rows: Vec<TripRow> = gtfs
        .trips
        .iter()
        .map(|(id, t)| {
            (
                agency_id.0.clone(),
                id.clone(),
                t.route_id.clone(),
                t.service_id.clone(),
                t.direction_id.as_ref().map(direction_to_int),
                t.trip_headsign.clone(),
            )
        })
        .collect();

    for chunk in rows.chunks(CHUNK) {
        let placeholders = pg_placeholders(chunk.len(), 6);
        let sql = format!(
            "INSERT INTO trips (agency_id, trip_id, route_id, service_id, direction_id, trip_headsign) VALUES {placeholders}
             ON CONFLICT (agency_id, trip_id) DO UPDATE SET
               route_id = EXCLUDED.route_id,
               service_id = EXCLUDED.service_id,
               direction_id = EXCLUDED.direction_id,
               trip_headsign = EXCLUDED.trip_headsign"
        );
        let mut q = sqlx::query(&sql);
        for (aid, id, route, svc, dir, head) in chunk {
            q = q
                .bind(aid)
                .bind(id)
                .bind(route)
                .bind(svc)
                .bind(dir)
                .bind(head);
        }
        q.execute(&mut **tx).await?;
    }
    info!("Loaded {} trips", gtfs.trips.len());
    Ok(())
}

async fn load_stops(tx: &mut Tx<'_>, agency_id: &AgencyId, gtfs: &Gtfs) -> Result<()> {
    let mut rows: Vec<(String, String, String, f64, f64)> = Vec::new();
    for (id, stop) in &gtfs.stops {
        match (stop.latitude, stop.longitude) {
            (Some(lat), Some(lon)) => rows.push((
                agency_id.0.clone(),
                id.clone(),
                stop.name.clone().unwrap_or_default(),
                lat,
                lon,
            )),
            _ => warn!("Stop {} missing coordinates, skipping", id),
        }
    }

    for chunk in rows.chunks(CHUNK) {
        let placeholders = pg_placeholders(chunk.len(), 5);
        let sql = format!(
            "INSERT INTO stops (agency_id, stop_id, stop_name, stop_lat, stop_lon) VALUES {placeholders}
             ON CONFLICT (agency_id, stop_id) DO UPDATE SET
               stop_name = EXCLUDED.stop_name,
               stop_lat = EXCLUDED.stop_lat,
               stop_lon = EXCLUDED.stop_lon"
        );
        let mut q = sqlx::query(&sql);
        for (aid, id, name, lat, lon) in chunk {
            q = q.bind(aid).bind(id).bind(name).bind(lat).bind(lon);
        }
        q.execute(&mut **tx).await?;
    }
    info!("Loaded {} stops", rows.len());
    Ok(())
}

async fn load_scheduled_stops(tx: &mut Tx<'_>, agency_id: &AgencyId, gtfs: &Gtfs) -> Result<()> {
    // Flatten all stop_times into (agency_id, trip_id, stop_id, seq, arrival, departure)
    let mut rows: Vec<(String, String, String, i64, String, String)> = Vec::new();
    for (trip_id, trip) in &gtfs.trips {
        for st in &trip.stop_times {
            rows.push((
                agency_id.0.clone(),
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
        let placeholders = pg_placeholders(chunk.len(), 6);
        let sql = format!(
            "INSERT INTO scheduled_stops \
             (agency_id, trip_id, stop_id, stop_sequence, arrival_time, departure_time) VALUES {placeholders}
             ON CONFLICT (agency_id, trip_id, stop_sequence) DO UPDATE SET
               stop_id = EXCLUDED.stop_id,
               arrival_time = EXCLUDED.arrival_time,
               departure_time = EXCLUDED.departure_time"
        );
        let mut q = sqlx::query(&sql);
        for (aid, tid, sid, seq, arr, dep) in chunk {
            q = q
                .bind(aid)
                .bind(tid)
                .bind(sid)
                .bind(seq)
                .bind(arr)
                .bind(dep);
        }
        q.execute(&mut **tx).await?;

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

async fn load_calendar(
    tx: &mut Tx<'_>,
    agency_id: &AgencyId,
    calendar: &std::collections::HashMap<String, gtfs_structures::Calendar>,
) -> Result<()> {
    for cal in calendar.values() {
        sqlx::query(
            "INSERT INTO calendar
             (agency_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
             ON CONFLICT (agency_id, service_id) DO UPDATE SET
               monday    = EXCLUDED.monday,
               tuesday   = EXCLUDED.tuesday,
               wednesday = EXCLUDED.wednesday,
               thursday  = EXCLUDED.thursday,
               friday    = EXCLUDED.friday,
               saturday  = EXCLUDED.saturday,
               sunday    = EXCLUDED.sunday",
        )
        .bind(agency_id.as_str())
        .bind(&cal.id)
        .bind(cal.monday)
        .bind(cal.tuesday)
        .bind(cal.wednesday)
        .bind(cal.thursday)
        .bind(cal.friday)
        .bind(cal.saturday)
        .bind(cal.sunday)
        .execute(&mut **tx)
        .await?;
    }
    info!("Loaded {} calendar entries", calendar.len());
    Ok(())
}

/// Synthesize calendar rows from `calendar_dates.txt` for service_ids that have no
/// entry in `calendar.txt`. For each such service, inspects the Added dates and sets
/// the day-of-week flags based on which weekdays those dates fall on.
/// Uses `ON CONFLICT DO NOTHING` so real `calendar.txt` rows are never overwritten.
pub(crate) async fn load_calendar_from_dates(
    tx: &mut Tx<'_>,
    agency_id: &AgencyId,
    calendar_dates: &std::collections::HashMap<String, Vec<gtfs_structures::CalendarDate>>,
) -> Result<()> {
    let mut synthesized = 0usize;
    for (service_id, dates) in calendar_dates {
        let mut monday = false;
        let mut tuesday = false;
        let mut wednesday = false;
        let mut thursday = false;
        let mut friday = false;
        let mut saturday = false;
        let mut sunday = false;

        for cd in dates {
            if cd.exception_type != Exception::Added {
                continue;
            }
            match cd.date.weekday() {
                chrono::Weekday::Mon => monday = true,
                chrono::Weekday::Tue => tuesday = true,
                chrono::Weekday::Wed => wednesday = true,
                chrono::Weekday::Thu => thursday = true,
                chrono::Weekday::Fri => friday = true,
                chrono::Weekday::Sat => saturday = true,
                chrono::Weekday::Sun => sunday = true,
            }
        }

        if !monday && !tuesday && !wednesday && !thursday && !friday && !saturday && !sunday {
            continue;
        }

        sqlx::query(
            "INSERT INTO calendar
             (agency_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
             ON CONFLICT (agency_id, service_id) DO NOTHING",
        )
        .bind(agency_id.as_str())
        .bind(service_id.as_str())
        .bind(monday)
        .bind(tuesday)
        .bind(wednesday)
        .bind(thursday)
        .bind(friday)
        .bind(saturday)
        .bind(sunday)
        .execute(&mut **tx)
        .await?;
        synthesized += 1;
    }
    info!(
        "Synthesized {} calendar entries from calendar_dates",
        synthesized
    );
    Ok(())
}

/// Compute a deterministic variant_id for a given stop sequence.
/// Input: "{stop1_id},{stop2_id},..." — route and direction are intentionally
/// excluded so the same physical pattern gets the same ID regardless of
/// how the agency numbers or labels the route.
fn variant_id_for(stop_ids: &[String]) -> String {
    let input = stop_ids.join(",");
    let digest = Sha256::digest(input.as_bytes());
    hex::encode(&digest[..16])
}

/// Build and store route variants from already-loaded trips + scheduled_stops.
/// Groups trips by their ordered stop sequence, assigns a deterministic variant_id,
/// marks the variant with the most trips as primary, and links trips to their variant.
pub(crate) async fn load_variants(
    tx: &mut Tx<'_>,
    agency_id: &AgencyId,
    gtfs: &Gtfs,
) -> Result<()> {
    // Collect stop sequences per trip in memory (already loaded into DB, but gtfs struct is handy).
    // key: (route_id, direction_id, stop_ids_csv) → (variant_id, trip_ids, headsign)
    let mut pattern_map: HashMap<PatternKey, PatternVal> = HashMap::new();

    for (trip_id, trip) in &gtfs.trips {
        let direction_id = trip
            .direction_id
            .as_ref()
            .map(direction_to_int)
            .unwrap_or(0);

        let stop_ids: Vec<String> = trip
            .stop_times
            .iter()
            .map(|st| st.stop.id.clone())
            .collect();
        // stop_times from gtfs-structures are already ordered by stop_sequence
        let stop_ids_csv = stop_ids.join(",");

        let key = (trip.route_id.clone(), direction_id, stop_ids_csv.clone());
        let vid = variant_id_for(&stop_ids);

        let entry = pattern_map.entry(key).or_insert_with(|| {
            let headsign = trip.trip_headsign.clone();
            (vid.clone(), Vec::new(), headsign)
        });
        entry.1.push(trip_id.clone());
    }

    // Determine is_primary per (route_id, direction_id): variant with highest trip_count.
    // Build: (route_id, direction_id) → max trip_count seen so far
    let mut primary_map: HashMap<(String, i64), (usize, String)> = HashMap::new();
    for ((route_id, direction_id, _), (vid, trip_ids, _)) in &pattern_map {
        let count = trip_ids.len();
        let entry = primary_map
            .entry((route_id.clone(), *direction_id))
            .or_insert((0, vid.clone()));
        if count > entry.0 || (count == entry.0 && vid < &entry.1) {
            *entry = (count, vid.clone());
        }
    }

    for ((route_id, direction_id, stop_ids_csv), (vid, trip_ids, headsign)) in &pattern_map {
        let stop_ids: Vec<&str> = stop_ids_csv.split(',').collect();
        let stop_count = stop_ids.len() as i64;
        let trip_count = trip_ids.len() as i64;
        let is_primary = primary_map
            .get(&(route_id.clone(), *direction_id))
            .map(|(_, primary_vid)| primary_vid == vid)
            .unwrap_or(false);

        sqlx::query(
            "INSERT INTO route_variants
             (agency_id, variant_id, route_id, direction_id, headsign, stop_count, trip_count, is_primary)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
             ON CONFLICT (agency_id, route_id, direction_id, variant_id) DO UPDATE SET
               trip_count = EXCLUDED.trip_count,
               is_primary = EXCLUDED.is_primary,
               headsign   = EXCLUDED.headsign",
        )
        .bind(agency_id.as_str())
        .bind(vid)
        .bind(route_id)
        .bind(direction_id)
        .bind(headsign.as_deref())
        .bind(stop_count)
        .bind(trip_count)
        .bind(is_primary)
        .execute(&mut **tx)
        .await?;

        for (seq, sid) in stop_ids.iter().enumerate() {
            sqlx::query(
                "INSERT INTO route_variant_stops (agency_id, variant_id, stop_sequence, stop_id)
                 VALUES ($1, $2, $3, $4)
                 ON CONFLICT (agency_id, variant_id, stop_sequence) DO NOTHING",
            )
            .bind(agency_id.as_str())
            .bind(vid)
            .bind(seq as i64)
            .bind(sid)
            .execute(&mut **tx)
            .await?;
        }

        for trip_id in trip_ids {
            sqlx::query(
                "UPDATE trips SET variant_id = $1
                 WHERE agency_id = $2 AND trip_id = $3",
            )
            .bind(vid)
            .bind(agency_id.as_str())
            .bind(trip_id)
            .execute(&mut **tx)
            .await?;
        }
    }

    info!(
        "Loaded {} route variants for agency {}",
        pattern_map.len(),
        agency_id
    );
    Ok(())
}

async fn get_stored_version(db: &Database, agency_id: &AgencyId) -> Result<Option<String>> {
    let key = format!("gtfs_static_version_{agency_id}");
    let row = sqlx::query!("SELECT value FROM feed_info WHERE key = $1", key,)
        .fetch_optional(&db.pool)
        .await?;
    Ok(row.map(|r| r.value))
}

async fn get_last_download(db: &Database, agency_id: &AgencyId) -> Result<Option<String>> {
    let key = format!("gtfs_static_last_download_{agency_id}");
    let row = sqlx::query!("SELECT value FROM feed_info WHERE key = $1", key,)
        .fetch_optional(&db.pool)
        .await?;
    Ok(row.map(|r| r.value))
}

async fn set_stored_version(db: &Database, agency_id: &AgencyId, version: &str) -> Result<()> {
    let key = format!("gtfs_static_version_{agency_id}");
    let now = chrono::Utc::now().to_rfc3339();
    sqlx::query!(
        "INSERT INTO feed_info (key, value, updated_at) VALUES ($1, $2, $3)
         ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at",
        key,
        version,
        now,
    )
    .execute(&db.pool)
    .await?;
    set_last_download(db, agency_id).await?;
    Ok(())
}

async fn set_last_download(db: &Database, agency_id: &AgencyId) -> Result<()> {
    let key = format!("gtfs_static_last_download_{agency_id}");
    let now = chrono::Utc::now();
    let today = now.date_naive().format("%Y-%m-%d").to_string();
    let now = now.to_rfc3339();
    sqlx::query!(
        "INSERT INTO feed_info (key, value, updated_at) VALUES ($1, $2, $3)
         ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at",
        key,
        today,
        now,
    )
    .execute(&db.pool)
    .await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::NaiveDate;
    use gtfs_structures::Calendar;
    use mobilispect_core::db::test_utils;
    use std::collections::HashMap;

    // ── helpers for building minimal Gtfs structs ──────────────────────────

    fn make_stop(id: &str) -> std::sync::Arc<gtfs_structures::Stop> {
        std::sync::Arc::new(gtfs_structures::Stop {
            id: id.to_string(),
            name: Some(id.to_string()),
            latitude: Some(45.0),
            longitude: Some(-73.0),
            ..Default::default()
        })
    }

    fn make_stop_time(
        stop: std::sync::Arc<gtfs_structures::Stop>,
        seq: u32,
    ) -> gtfs_structures::StopTime {
        gtfs_structures::StopTime {
            stop,
            stop_sequence: seq,
            arrival_time: Some(seq * 60),
            departure_time: Some(seq * 60),
            ..Default::default()
        }
    }

    fn make_route(id: &str) -> gtfs_structures::Route {
        gtfs_structures::Route {
            id: id.to_string(),
            short_name: Some(id.to_string()),
            long_name: Some(id.to_string()),
            route_type: gtfs_structures::RouteType::Bus,
            ..Default::default()
        }
    }

    fn make_trip(
        id: &str,
        route_id: &str,
        direction: Option<gtfs_structures::DirectionType>,
        stops: Vec<std::sync::Arc<gtfs_structures::Stop>>,
    ) -> gtfs_structures::Trip {
        let stop_times = stops
            .into_iter()
            .enumerate()
            .map(|(i, s)| make_stop_time(s, i as u32))
            .collect();
        gtfs_structures::Trip {
            id: id.to_string(),
            route_id: route_id.to_string(),
            service_id: "WD".to_string(),
            direction_id: direction,
            stop_times,
            ..Default::default()
        }
    }

    // ── load_variants tests ────────────────────────────────────────────────

    #[tokio::test]
    async fn load_variants_creates_variant_and_links_trips() {
        let td = test_utils::setup().await;
        let db = td.db;

        // Insert a route so the FK is satisfied.
        sqlx::query(
            "INSERT INTO routes (agency_id, route_id, short_name, long_name, route_type)
             VALUES ('stm', '45', '45', 'Papineau', 3)",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let sa = make_stop("A");
        let sb = make_stop("B");
        let sc = make_stop("C");

        // Two trips with the same stop sequence → same variant.
        let trip1 = make_trip(
            "T1",
            "45",
            Some(gtfs_structures::DirectionType::Outbound),
            vec![sa.clone(), sb.clone(), sc.clone()],
        );
        let trip2 = make_trip(
            "T2",
            "45",
            Some(gtfs_structures::DirectionType::Outbound),
            vec![sa.clone(), sb.clone(), sc.clone()],
        );

        // Insert the trips first (load_variants expects them already in trips table).
        for t in [&trip1, &trip2] {
            sqlx::query(
                "INSERT INTO trips (agency_id, trip_id, route_id, service_id, direction_id)
                 VALUES ('stm', $1, $2, 'WD', 0)",
            )
            .bind(&t.id)
            .bind(&t.route_id)
            .execute(&db.pool)
            .await
            .unwrap();
        }

        let mut gtfs = Gtfs::default();
        gtfs.trips.insert("T1".to_string(), trip1);
        gtfs.trips.insert("T2".to_string(), trip2);
        gtfs.routes.insert("45".to_string(), make_route("45"));

        let mut tx = db.pool.begin().await.unwrap();
        load_variants(&mut tx, &AgencyId::from("stm"), &gtfs)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        // One variant should exist.
        let variant_count: (i64,) =
            sqlx::query_as("SELECT COUNT(*) FROM route_variants WHERE agency_id = 'stm'")
                .fetch_one(&db.pool)
                .await
                .unwrap();
        assert_eq!(variant_count.0, 1, "expected exactly one variant");

        // That variant should be primary with trip_count = 2.
        let (trip_count, is_primary): (i64, bool) = sqlx::query_as(
            "SELECT trip_count, is_primary FROM route_variants WHERE agency_id = 'stm'",
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();
        assert_eq!(trip_count, 2);
        assert!(is_primary);

        // Stops should be recorded in order.
        let stop_ids: Vec<(i64, String)> = sqlx::query_as(
            "SELECT stop_sequence, stop_id FROM route_variant_stops WHERE agency_id = 'stm' ORDER BY stop_sequence",
        )
        .fetch_all(&db.pool)
        .await
        .unwrap();
        assert_eq!(stop_ids.len(), 3);
        assert_eq!(stop_ids[0].1, "A");
        assert_eq!(stop_ids[1].1, "B");
        assert_eq!(stop_ids[2].1, "C");

        // Both trips should be linked to the variant.
        let linked: (i64,) = sqlx::query_as(
            "SELECT COUNT(*) FROM trips WHERE agency_id = 'stm' AND variant_id IS NOT NULL",
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();
        assert_eq!(linked.0, 2);
    }

    #[tokio::test]
    async fn load_variants_marks_most_common_as_primary() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query(
            "INSERT INTO routes (agency_id, route_id, short_name, long_name, route_type)
             VALUES ('stm', '45', '45', 'Papineau', 3)",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let sa = make_stop("A");
        let sb = make_stop("B");
        let sc = make_stop("C");
        let sd = make_stop("D");

        // Full route: A→B→C→D — 3 trips
        let full_stops = vec![sa.clone(), sb.clone(), sc.clone(), sd.clone()];
        // Short turn: A→B→C — 1 trip
        let short_stops = vec![sa.clone(), sb.clone(), sc.clone()];

        let mut gtfs = Gtfs::default();
        gtfs.routes.insert("45".to_string(), make_route("45"));

        for (i, stops) in [&full_stops, &full_stops, &full_stops, &short_stops]
            .iter()
            .enumerate()
        {
            let id = format!("T{i}");
            let trip = make_trip(
                &id,
                "45",
                Some(gtfs_structures::DirectionType::Outbound),
                (*stops).clone(),
            );
            sqlx::query(
                "INSERT INTO trips (agency_id, trip_id, route_id, service_id, direction_id)
                 VALUES ('stm', $1, '45', 'WD', 0)",
            )
            .bind(&id)
            .execute(&db.pool)
            .await
            .unwrap();
            gtfs.trips.insert(id, trip);
        }

        let mut tx = db.pool.begin().await.unwrap();
        load_variants(&mut tx, &AgencyId::from("stm"), &gtfs)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        // Two variants should exist.
        let count: (i64,) =
            sqlx::query_as("SELECT COUNT(*) FROM route_variants WHERE agency_id = 'stm'")
                .fetch_one(&db.pool)
                .await
                .unwrap();
        assert_eq!(count.0, 2);

        // The full-route variant (3 trips) should be primary.
        let primary_stop_count: (i64,) = sqlx::query_as(
            "SELECT stop_count FROM route_variants WHERE agency_id = 'stm' AND is_primary = TRUE",
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();
        assert_eq!(
            primary_stop_count.0, 4,
            "primary variant should be the 4-stop full route"
        );
    }

    #[tokio::test]
    async fn load_calendar_inserts_service_day_flags() {
        let td = test_utils::setup().await;
        let db = td.db;
        let mut tx = db.pool.begin().await.unwrap();

        let mut calendar: HashMap<String, Calendar> = HashMap::new();
        calendar.insert(
            "WD".to_string(),
            Calendar {
                id: "WD".to_string(),
                monday: true,
                tuesday: true,
                wednesday: true,
                thursday: true,
                friday: true,
                saturday: false,
                sunday: false,
                start_date: NaiveDate::from_ymd_opt(2026, 1, 1).unwrap(),
                end_date: NaiveDate::from_ymd_opt(2026, 12, 31).unwrap(),
            },
        );
        calendar.insert(
            "SAT".to_string(),
            Calendar {
                id: "SAT".to_string(),
                monday: false,
                tuesday: false,
                wednesday: false,
                thursday: false,
                friday: false,
                saturday: true,
                sunday: false,
                start_date: NaiveDate::from_ymd_opt(2026, 1, 1).unwrap(),
                end_date: NaiveDate::from_ymd_opt(2026, 12, 31).unwrap(),
            },
        );

        load_calendar(&mut tx, &AgencyId::from("stm"), &calendar)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        let rows: Vec<(String, bool, bool, bool)> = sqlx::query_as(
            "SELECT service_id, monday, saturday, sunday FROM calendar WHERE agency_id = 'stm' ORDER BY service_id",
        )
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 2);
        let wd = rows.iter().find(|r| r.0 == "WD").unwrap();
        assert!(wd.1, "WD monday should be true");
        assert!(!wd.2, "WD saturday should be false");
        let sat = rows.iter().find(|r| r.0 == "SAT").unwrap();
        assert!(sat.2, "SAT saturday should be true");
    }

    #[tokio::test]
    async fn load_calendar_from_dates_synthesizes_day_flags_from_added_dates() {
        let td = test_utils::setup().await;
        let db = td.db;
        let mut tx = db.pool.begin().await.unwrap();

        // 2026-01-05 = Mon, 2026-01-09 = Fri, 2026-01-10 = Sat, 2026-01-11 = Sun
        let mut calendar_dates: HashMap<String, Vec<gtfs_structures::CalendarDate>> =
            HashMap::new();
        calendar_dates.insert(
            "WD".to_string(),
            vec![
                gtfs_structures::CalendarDate {
                    service_id: "WD".to_string(),
                    date: NaiveDate::from_ymd_opt(2026, 1, 5).unwrap(),
                    exception_type: gtfs_structures::Exception::Added,
                },
                gtfs_structures::CalendarDate {
                    service_id: "WD".to_string(),
                    date: NaiveDate::from_ymd_opt(2026, 1, 9).unwrap(),
                    exception_type: gtfs_structures::Exception::Added,
                },
            ],
        );
        calendar_dates.insert(
            "SAT".to_string(),
            vec![gtfs_structures::CalendarDate {
                service_id: "SAT".to_string(),
                date: NaiveDate::from_ymd_opt(2026, 1, 10).unwrap(),
                exception_type: gtfs_structures::Exception::Added,
            }],
        );
        calendar_dates.insert(
            "SUN".to_string(),
            vec![gtfs_structures::CalendarDate {
                service_id: "SUN".to_string(),
                date: NaiveDate::from_ymd_opt(2026, 1, 11).unwrap(),
                exception_type: gtfs_structures::Exception::Added,
            }],
        );

        load_calendar_from_dates(&mut tx, &AgencyId::from("stm"), &calendar_dates)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        let rows: Vec<CalendarRow> = sqlx::query_as(
            "SELECT service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday
             FROM calendar WHERE agency_id = 'stm' ORDER BY service_id",
        )
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 3);

        let wd = rows.iter().find(|r| r.0 == "WD").unwrap();
        assert!(wd.1, "WD monday");
        assert!(!wd.2, "WD tuesday");
        assert!(!wd.3, "WD wednesday");
        assert!(!wd.4, "WD thursday");
        assert!(wd.5, "WD friday");
        assert!(!wd.6, "WD saturday");
        assert!(!wd.7, "WD sunday");

        let sat = rows.iter().find(|r| r.0 == "SAT").unwrap();
        assert!(!sat.1, "SAT monday");
        assert!(sat.6, "SAT saturday");
        assert!(!sat.7, "SAT sunday");

        let sun = rows.iter().find(|r| r.0 == "SUN").unwrap();
        assert!(!sun.1, "SUN monday");
        assert!(!sun.6, "SUN saturday");
        assert!(sun.7, "SUN sunday");
    }

    #[tokio::test]
    async fn load_calendar_from_dates_does_not_overwrite_calendar_txt_entry() {
        let td = test_utils::setup().await;
        let db = td.db;
        let mut tx = db.pool.begin().await.unwrap();

        // Load a real calendar.txt entry: WD is weekdays-only
        let mut calendar: HashMap<String, Calendar> = HashMap::new();
        calendar.insert(
            "WD".to_string(),
            Calendar {
                id: "WD".to_string(),
                monday: true,
                tuesday: true,
                wednesday: true,
                thursday: true,
                friday: true,
                saturday: false,
                sunday: false,
                start_date: NaiveDate::from_ymd_opt(2026, 1, 1).unwrap(),
                end_date: NaiveDate::from_ymd_opt(2026, 12, 31).unwrap(),
            },
        );
        load_calendar(&mut tx, &AgencyId::from("stm"), &calendar)
            .await
            .unwrap();

        // calendar_dates claims WD runs on Saturday — should be ignored
        let mut calendar_dates: HashMap<String, Vec<gtfs_structures::CalendarDate>> =
            HashMap::new();
        calendar_dates.insert(
            "WD".to_string(),
            vec![gtfs_structures::CalendarDate {
                service_id: "WD".to_string(),
                date: NaiveDate::from_ymd_opt(2026, 1, 10).unwrap(), // Saturday
                exception_type: gtfs_structures::Exception::Added,
            }],
        );
        load_calendar_from_dates(&mut tx, &AgencyId::from("stm"), &calendar_dates)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        let row: (bool, bool) = sqlx::query_as(
            "SELECT saturday, monday FROM calendar WHERE agency_id = 'stm' AND service_id = 'WD'",
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        assert!(
            !row.0,
            "saturday should remain false — calendar.txt takes precedence"
        );
        assert!(row.1, "monday should still be true");
    }
}
