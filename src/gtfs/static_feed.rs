use anyhow::Result;
use chrono::Datelike;
use gtfs_structures::{DirectionType, Exception, Gtfs, RouteType};
use tracing::{info, warn};

use crate::config::AgencyConfig;
use crate::db::Database;

/// Rows to bundle per INSERT statement.
const CHUNK: usize = 500;

/// Load static GTFS data into the database if not already present for this agency.
/// Re-loads if the feed version has changed.
pub async fn load_if_needed(db: &Database, agency: &AgencyConfig) -> Result<()> {
    let stored_version = get_stored_version(db, &agency.slug).await?;

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
        agency.slug,
        feed_version,
        gtfs.routes.len(),
        gtfs.trips.len(),
        gtfs.stops.len()
    );

    // Drop stale data for this agency and bulk-insert in one transaction
    let mut tx = db.pool.begin().await?;
    let slug = &agency.slug;
    sqlx::query("DELETE FROM scheduled_stops WHERE agency_id = $1")
        .bind(slug)
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM trips WHERE agency_id = $1")
        .bind(slug)
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM stops WHERE agency_id = $1")
        .bind(slug)
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM routes WHERE agency_id = $1")
        .bind(slug)
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM calendar WHERE agency_id = $1")
        .bind(slug)
        .execute(&mut *tx)
        .await?;

    load_routes(&mut tx, slug, &gtfs).await?;
    load_trips(&mut tx, slug, &gtfs).await?;
    load_stops(&mut tx, slug, &gtfs).await?;
    load_scheduled_stops(&mut tx, slug, &gtfs).await?;
    load_calendar(&mut tx, slug, &gtfs.calendar).await?;
    load_calendar_from_dates(&mut tx, slug, &gtfs.calendar_dates).await?;
    tx.commit().await?;

    set_stored_version(db, &agency.slug, &feed_version).await?;
    info!("Static GTFS load complete for {}", agency.slug);
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

async fn load_routes(tx: &mut Tx<'_>, agency_id: &str, gtfs: &Gtfs) -> Result<()> {
    let rows: Vec<(String, String, String, String, i64)> = gtfs
        .routes
        .iter()
        .map(|(id, r)| {
            (
                agency_id.to_string(),
                id.clone(),
                r.short_name.clone(),
                r.long_name.clone(),
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

async fn load_trips(tx: &mut Tx<'_>, agency_id: &str, gtfs: &Gtfs) -> Result<()> {
    let rows: Vec<(String, String, String, String, Option<i64>, Option<String>)> = gtfs
        .trips
        .iter()
        .map(|(id, t)| {
            (
                agency_id.to_string(),
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

async fn load_stops(tx: &mut Tx<'_>, agency_id: &str, gtfs: &Gtfs) -> Result<()> {
    let mut rows: Vec<(String, String, String, f64, f64)> = Vec::new();
    for (id, stop) in &gtfs.stops {
        match (stop.latitude, stop.longitude) {
            (Some(lat), Some(lon)) => rows.push((
                agency_id.to_string(),
                id.clone(),
                stop.name.clone(),
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

async fn load_scheduled_stops(tx: &mut Tx<'_>, agency_id: &str, gtfs: &Gtfs) -> Result<()> {
    // Flatten all stop_times into (agency_id, trip_id, stop_id, seq, arrival, departure)
    let mut rows: Vec<(String, String, String, i64, String, String)> = Vec::new();
    for (trip_id, trip) in &gtfs.trips {
        for st in &trip.stop_times {
            rows.push((
                agency_id.to_string(),
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
    agency_id: &str,
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
        .bind(agency_id)
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
    agency_id: &str,
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
        .bind(agency_id)
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;
    use chrono::NaiveDate;
    use gtfs_structures::Calendar;
    use std::collections::HashMap;

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

        load_calendar(&mut tx, "stm", &calendar).await.unwrap();
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

        load_calendar_from_dates(&mut tx, "stm", &calendar_dates)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        let rows: Vec<(String, bool, bool, bool, bool, bool, bool, bool)> = sqlx::query_as(
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
        load_calendar(&mut tx, "stm", &calendar).await.unwrap();

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
        load_calendar_from_dates(&mut tx, "stm", &calendar_dates)
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

async fn get_stored_version(db: &Database, agency_slug: &str) -> Result<Option<String>> {
    let key = format!("gtfs_static_version_{agency_slug}");
    let row = sqlx::query!("SELECT value FROM feed_info WHERE key = $1", key,)
        .fetch_optional(&db.pool)
        .await?;
    Ok(row.map(|r| r.value))
}

async fn set_stored_version(db: &Database, agency_slug: &str, version: &str) -> Result<()> {
    let key = format!("gtfs_static_version_{agency_slug}");
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
    Ok(())
}
