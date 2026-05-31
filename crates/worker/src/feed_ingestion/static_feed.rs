use anyhow::Result;
use chrono::Datelike;
use chrono::Utc;
use gtfs_structures::{DirectionType, Exception, Gtfs, RouteType};
use hex;
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use tracing::{info, warn};

use mobilispect_core::config::FeedConfig;
use mobilispect_core::db::Database;
use mobilispect_core::ids::{AgencyId, FeedId, RouteId, StopId};

use crate::transitland::TransitlandClient;

type PatternKey = (String, i64, String);
type PatternVal = (String, Vec<String>, Option<String>);

/// Rows to bundle per INSERT statement.
const CHUNK: usize = 500;

/// Load static GTFS data into the database if not already present for this feed.
/// Re-loads if the feed version has changed.
pub async fn load_if_needed(
    db: &Database,
    feed: &FeedConfig,
    feed_id: FeedId,
    transitland: &TransitlandClient,
) -> Result<()> {
    let stored_version = get_stored_version(db, feed_id).await?;
    let last_ingested = get_last_ingested(db, feed_id).await?;

    if let Some(last) = last_ingested {
        let today = Utc::now().date_naive();
        if last.date_naive() >= today {
            info!("Static GTFS already downloaded today, skipping download");
            return Ok(());
        }
    }

    // Download and parse the GTFS zip (blocking I/O — run on thread pool)
    info!("Downloading static GTFS from {}", feed.gtfs_static_url);
    let url = feed.gtfs_static_url.clone();
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
        "Loading static GTFS for feed {} version: {} ({} routes, {} trips, {} stops)",
        feed_id,
        feed_version,
        gtfs.routes.len(),
        gtfs.trips.len(),
        gtfs.stops.len()
    );

    // Drop stale data for this feed and bulk-insert in one transaction
    let mut tx = db.pool.begin().await?;

    sqlx::query("DELETE FROM route_variant_stops WHERE feed_id = $1")
        .bind(feed_id.as_i64())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM scheduled_stops WHERE feed_id = $1")
        .bind(feed_id.as_i64())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM trips WHERE feed_id = $1")
        .bind(feed_id.as_i64())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM route_variants WHERE feed_id = $1")
        .bind(feed_id.as_i64())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM services WHERE feed_id = $1")
        .bind(feed_id.as_i64())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM service_exceptions WHERE feed_id = $1")
        .bind(feed_id.as_i64())
        .execute(&mut *tx)
        .await?;
    // Remove feed_*_ids mappings — canonical rows (agencies/routes/stops) are shared, keep them
    sqlx::query("DELETE FROM feed_stop_ids WHERE feed_id = $1")
        .bind(feed_id.as_i64())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM feed_route_ids WHERE feed_id = $1")
        .bind(feed_id.as_i64())
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM feed_agency_ids WHERE feed_id = $1")
        .bind(feed_id.as_i64())
        .execute(&mut *tx)
        .await?;

    // Resolve Transitland Onestop IDs (if configured)
    let (agency_map, route_map, stop_map) = if let Some(tl_feed_id) = &feed.transitland_feed_id {
        resolve_transitland_entities(
            &mut tx,
            feed_id,
            tl_feed_id,
            &gtfs,
            transitland,
        )
        .await?
    } else {
        warn!(
            "No transitland_feed_id configured for feed {} — skipping Onestop ID resolution",
            feed_id
        );
        (HashMap::new(), HashMap::new(), HashMap::new())
    };

    // Load timetable data
    load_variants(&mut tx, feed_id, &gtfs, &route_map, &stop_map).await?;
    load_trips(&mut tx, feed_id, &gtfs).await?;
    load_scheduled_stops(&mut tx, feed_id, &gtfs, &stop_map).await?;
    load_services(&mut tx, feed_id, &agency_map, &gtfs.calendar).await?;
    load_service_exceptions(&mut tx, feed_id, &gtfs.calendar_dates).await?;
    load_services_from_dates(&mut tx, feed_id, &agency_map, &gtfs.calendar_dates).await?;

    tx.commit().await?;

    set_stored_version(db, feed_id, &feed_version).await?;
    info!("Static GTFS load complete for feed {}", feed_id);
    Ok(())
}

/// Resolve agencies, routes, and stops via Transitland.
/// Returns maps from GTFS IDs to Onestop IDs.
async fn resolve_transitland_entities(
    tx: &mut Tx<'_>,
    feed_id: FeedId,
    tl_feed_id: &str,
    gtfs: &Gtfs,
    transitland: &TransitlandClient,
) -> Result<(
    HashMap<String, AgencyId>,
    HashMap<String, RouteId>,
    HashMap<String, StopId>,
)> {
    let agency_map = resolve_agencies(tx, feed_id, tl_feed_id, gtfs, transitland).await?;
    let route_map = resolve_routes(tx, feed_id, tl_feed_id, gtfs, &agency_map, transitland).await?;
    let stop_map = resolve_stops(tx, feed_id, tl_feed_id, gtfs, transitland).await?;
    Ok((agency_map, route_map, stop_map))
}

async fn resolve_agencies(
    tx: &mut Tx<'_>,
    feed_id: FeedId,
    tl_feed_id: &str,
    gtfs: &Gtfs,
    transitland: &TransitlandClient,
) -> Result<HashMap<String, AgencyId>> {
    let mut map: HashMap<String, AgencyId> = HashMap::new();

    for agency in &gtfs.agencies {
        // agency.id is Option<String>; use empty string as fallback for GTFS agencies without an explicit ID
        let gtfs_agency_id = agency.id.as_deref().unwrap_or("");
        match transitland.resolve_agency(gtfs_agency_id, tl_feed_id).await {
            Ok(Some(onestop_id)) => {
                // Upsert canonical agency
                sqlx::query(
                    "INSERT INTO agencies (onestop_id, name, url, timezone, lang, phone)
                     VALUES ($1, $2, $3, $4, $5, $6)
                     ON CONFLICT (onestop_id) DO UPDATE SET
                       name = EXCLUDED.name,
                       url = EXCLUDED.url,
                       timezone = EXCLUDED.timezone,
                       lang = EXCLUDED.lang,
                       phone = EXCLUDED.phone",
                )
                .bind(onestop_id.as_str())
                .bind(&agency.name)
                .bind(&agency.url)
                .bind(&agency.timezone)
                .bind(agency.lang.as_deref())
                .bind(agency.phone.as_deref())
                .execute(&mut **tx)
                .await?;

                // Record feed mapping
                sqlx::query(
                    "INSERT INTO feed_agency_ids (feed_id, gtfs_agency_id, onestop_id)
                     VALUES ($1, $2, $3)
                     ON CONFLICT (feed_id, gtfs_agency_id) DO UPDATE SET
                       onestop_id = EXCLUDED.onestop_id",
                )
                .bind(feed_id.as_i64())
                .bind(gtfs_agency_id)
                .bind(onestop_id.as_str())
                .execute(&mut **tx)
                .await?;

                map.insert(agency.id.clone().unwrap_or_default(), onestop_id);
            }
            Ok(None) => {
                warn!(
                    "Transitland: no match for agency {} in feed {} — skipping",
                    gtfs_agency_id, tl_feed_id
                );
            }
            Err(e) => {
                warn!(
                    "Transitland: error resolving agency {} in feed {}: {:#} — skipping",
                    gtfs_agency_id, tl_feed_id, e
                );
            }
        }
    }

    info!("Resolved {}/{} agencies via Transitland", map.len(), gtfs.agencies.len());
    Ok(map)
}

async fn resolve_routes(
    tx: &mut Tx<'_>,
    feed_id: FeedId,
    tl_feed_id: &str,
    gtfs: &Gtfs,
    agency_map: &HashMap<String, AgencyId>,
    transitland: &TransitlandClient,
) -> Result<HashMap<String, RouteId>> {
    let mut map: HashMap<String, RouteId> = HashMap::new();
    let mut skipped = 0usize;

    for (gtfs_route_id, route) in &gtfs.routes {
        // Look up the canonical agency ID for this route
        // route.agency_id is Option<String>; use empty string as fallback
        let gtfs_agency_key = route.agency_id.as_deref().unwrap_or("");
        let canonical_agency_id = agency_map.get(gtfs_agency_key);
        if canonical_agency_id.is_none() {
            warn!(
                "No canonical agency ID for route {} (agency {:?}), skipping",
                gtfs_route_id, route.agency_id
            );
            skipped += 1;
            continue;
        }
        let canonical_agency_id = canonical_agency_id.unwrap();

        match transitland.resolve_route(gtfs_route_id, tl_feed_id).await {
            Ok(Some(onestop_id)) => {
                // Upsert canonical route
                sqlx::query(
                    "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type)
                     VALUES ($1, $2, $3, $4, $5)
                     ON CONFLICT (onestop_id) DO UPDATE SET
                       agency_id = EXCLUDED.agency_id,
                       short_name = EXCLUDED.short_name,
                       long_name = EXCLUDED.long_name,
                       route_type = EXCLUDED.route_type",
                )
                .bind(onestop_id.as_str())
                .bind(canonical_agency_id.as_str())
                .bind(route.short_name.as_deref().unwrap_or(""))
                .bind(route.long_name.as_deref().unwrap_or(""))
                .bind(route_type_to_int(&route.route_type))
                .execute(&mut **tx)
                .await?;

                // Record feed mapping
                sqlx::query(
                    "INSERT INTO feed_route_ids (feed_id, gtfs_route_id, onestop_id)
                     VALUES ($1, $2, $3)
                     ON CONFLICT (feed_id, gtfs_route_id) DO UPDATE SET
                       onestop_id = EXCLUDED.onestop_id",
                )
                .bind(feed_id.as_i64())
                .bind(gtfs_route_id.as_str())
                .bind(onestop_id.as_str())
                .execute(&mut **tx)
                .await?;

                map.insert(gtfs_route_id.clone(), onestop_id);
            }
            Ok(None) => {
                warn!(
                    "Transitland: no match for route {} in feed {} — skipping",
                    gtfs_route_id, tl_feed_id
                );
                skipped += 1;
            }
            Err(e) => {
                warn!(
                    "Transitland: error resolving route {} in feed {}: {:#} — skipping",
                    gtfs_route_id, tl_feed_id, e
                );
                skipped += 1;
            }
        }
    }

    info!(
        "Resolved {}/{} routes via Transitland ({} skipped)",
        map.len(),
        gtfs.routes.len(),
        skipped
    );
    Ok(map)
}

async fn resolve_stops(
    tx: &mut Tx<'_>,
    feed_id: FeedId,
    tl_feed_id: &str,
    gtfs: &Gtfs,
    transitland: &TransitlandClient,
) -> Result<HashMap<String, StopId>> {
    let mut map: HashMap<String, StopId> = HashMap::new();
    let mut skipped = 0usize;

    for (gtfs_stop_id, stop) in &gtfs.stops {
        match transitland.resolve_stop(gtfs_stop_id, tl_feed_id).await {
            Ok(Some((stop_onestop_id, maybe_station_id))) => {
                // Upsert parent station if present
                if let Some(station_id) = &maybe_station_id {
                    let (lat, lon) = (
                        stop.latitude.unwrap_or(0.0),
                        stop.longitude.unwrap_or(0.0),
                    );
                    sqlx::query(
                        "INSERT INTO stations (onestop_id, name, lat, lon)
                         VALUES ($1, $2, $3, $4)
                         ON CONFLICT (onestop_id) DO UPDATE SET
                           name = EXCLUDED.name,
                           lat = EXCLUDED.lat,
                           lon = EXCLUDED.lon",
                    )
                    .bind(station_id.as_str())
                    .bind(stop.name.as_deref().unwrap_or(""))
                    .bind(lat)
                    .bind(lon)
                    .execute(&mut **tx)
                    .await?;
                }

                let (lat, lon) = match (stop.latitude, stop.longitude) {
                    (Some(lat), Some(lon)) => (lat, lon),
                    _ => {
                        warn!("Stop {} missing coordinates, skipping", gtfs_stop_id);
                        skipped += 1;
                        continue;
                    }
                };

                // Upsert canonical stop
                sqlx::query(
                    "INSERT INTO stops (onestop_id, station_id, name, lat, lon)
                     VALUES ($1, $2, $3, $4, $5)
                     ON CONFLICT (onestop_id) DO UPDATE SET
                       station_id = EXCLUDED.station_id,
                       name = EXCLUDED.name,
                       lat = EXCLUDED.lat,
                       lon = EXCLUDED.lon",
                )
                .bind(stop_onestop_id.as_str())
                .bind(maybe_station_id.as_ref().map(|s| s.as_str()))
                .bind(stop.name.as_deref().unwrap_or(""))
                .bind(lat)
                .bind(lon)
                .execute(&mut **tx)
                .await?;

                // Record feed mapping
                sqlx::query(
                    "INSERT INTO feed_stop_ids (feed_id, gtfs_stop_id, onestop_id)
                     VALUES ($1, $2, $3)
                     ON CONFLICT (feed_id, gtfs_stop_id) DO UPDATE SET
                       onestop_id = EXCLUDED.onestop_id",
                )
                .bind(feed_id.as_i64())
                .bind(gtfs_stop_id.as_str())
                .bind(stop_onestop_id.as_str())
                .execute(&mut **tx)
                .await?;

                map.insert(gtfs_stop_id.clone(), stop_onestop_id);
            }
            Ok(None) => {
                warn!(
                    "Transitland: no match for stop {} in feed {} — skipping",
                    gtfs_stop_id, tl_feed_id
                );
                skipped += 1;
            }
            Err(e) => {
                warn!(
                    "Transitland: error resolving stop {} in feed {}: {:#} — skipping",
                    gtfs_stop_id, tl_feed_id, e
                );
                skipped += 1;
            }
        }
    }

    info!(
        "Resolved {}/{} stops via Transitland ({} skipped)",
        map.len(),
        gtfs.stops.len(),
        skipped
    );
    Ok(map)
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

/// Compute a deterministic variant_id for a given stop sequence.
/// Input: "{stop1_id},{stop2_id},..." — route and direction are intentionally
/// excluded so the same physical pattern gets the same ID regardless of
/// how the agency numbers or labels the route.
fn variant_id_for(stop_ids: &[String]) -> String {
    let input = stop_ids.join(",");
    let digest = Sha256::digest(input.as_bytes());
    hex::encode(&digest[..16])
}

/// Build and store route variants from gtfs trips.
/// Groups trips by their ordered stop sequence, assigns a deterministic variant_id,
/// marks the variant with the most trips as primary.
/// Note: variants must be inserted BEFORE trips because trips FK to route_variants.
pub(crate) async fn load_variants(
    tx: &mut Tx<'_>,
    feed_id: FeedId,
    gtfs: &Gtfs,
    route_map: &HashMap<String, RouteId>,
    stop_map: &HashMap<String, StopId>,
) -> Result<()> {
    // key: (route_id, direction_id, stop_ids_csv) → (variant_id, trip_ids, headsign)
    let mut pattern_map: HashMap<PatternKey, PatternVal> = HashMap::new();

    for (trip_id, trip) in &gtfs.trips {
        let direction_id = trip
            .direction_id
            .as_ref()
            .map(direction_to_int)
            .unwrap_or(0);

        // Use Onestop IDs when available; fall back to GTFS stop IDs
        let stop_ids: Vec<String> = trip
            .stop_times
            .iter()
            .map(|st| {
                stop_map
                    .get(&st.stop.id)
                    .map(|sid| sid.as_str().to_owned())
                    .unwrap_or_else(|| st.stop.id.clone())
            })
            .collect();

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

    for ((gtfs_route_id, direction_id, stop_ids_csv), (vid, trip_ids, headsign)) in &pattern_map {
        let stop_ids: Vec<&str> = stop_ids_csv.split(',').collect();
        let stop_count = stop_ids.len() as i64;
        let trip_count = trip_ids.len() as i64;
        let is_primary = primary_map
            .get(&(gtfs_route_id.clone(), *direction_id))
            .map(|(_, primary_vid)| primary_vid == vid)
            .unwrap_or(false);

        // Use Onestop route ID when available; fall back to GTFS route ID
        let route_id_str = route_map
            .get(gtfs_route_id)
            .map(|rid| rid.as_str().to_owned())
            .unwrap_or_else(|| gtfs_route_id.clone());

        sqlx::query(
            "INSERT INTO route_variants
             (feed_id, variant_id, route_id, direction_id, headsign, stop_count, trip_count, is_primary)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
             ON CONFLICT (feed_id, variant_id) DO UPDATE SET
               trip_count = EXCLUDED.trip_count,
               is_primary = EXCLUDED.is_primary,
               headsign   = EXCLUDED.headsign",
        )
        .bind(feed_id.as_i64())
        .bind(vid)
        .bind(&route_id_str)
        .bind(direction_id)
        .bind(headsign.as_deref())
        .bind(stop_count)
        .bind(trip_count)
        .bind(is_primary)
        .execute(&mut **tx)
        .await?;

        // Only insert route_variant_stops when stop_map is populated (Transitland mode).
        // In degraded mode stop_id would be a raw GTFS ID which violates the FK to stops(onestop_id).
        if !stop_map.is_empty() {
            for (seq, sid) in stop_ids.iter().enumerate() {
                // sid is an Onestop ID (resolved by Transitland) — only insert if it resolved
                let is_resolved = stop_map.values().any(|v| v.as_str() == *sid);
                if is_resolved {
                    sqlx::query(
                        "INSERT INTO route_variant_stops (feed_id, variant_id, stop_sequence, stop_id)
                         VALUES ($1, $2, $3, $4)
                         ON CONFLICT (feed_id, variant_id, stop_sequence) DO NOTHING",
                    )
                    .bind(feed_id.as_i64())
                    .bind(vid)
                    .bind(seq as i64)
                    .bind(sid)
                    .execute(&mut **tx)
                    .await?;
                }
            }
        }
    }

    info!(
        "Loaded {} route variants for feed {}",
        pattern_map.len(),
        feed_id
    );
    Ok(())
}

/// Load trips into the `trips` table.
/// Schema after migration 013: (feed_id, trip_id, service_id, trip_headsign, variant_id).
/// variant_id is recomputed using the same hash logic as load_variants — reading the
/// stop remapping from feed_stop_ids (populated earlier in the same transaction).
async fn load_trips(tx: &mut Tx<'_>, feed_id: FeedId, gtfs: &Gtfs) -> Result<()> {
    // Fetch stop_id remapping from feed_stop_ids (already populated by resolve_stops)
    // to produce the same variant_id hash as load_variants.
    let feed_stop_rows: Vec<(String, String)> =
        sqlx::query_as("SELECT gtfs_stop_id, onestop_id FROM feed_stop_ids WHERE feed_id = $1")
            .bind(feed_id.as_i64())
            .fetch_all(&mut **tx)
            .await?;
    let stop_remap: HashMap<String, String> = feed_stop_rows.into_iter().collect();

    type TripRow = (i64, String, String, Option<String>, String);
    let mut rows: Vec<TripRow> = Vec::new();

    for (trip_id, trip) in &gtfs.trips {
        let stop_ids: Vec<String> = trip
            .stop_times
            .iter()
            .map(|st| {
                stop_remap
                    .get(&st.stop.id)
                    .cloned()
                    .unwrap_or_else(|| st.stop.id.clone())
            })
            .collect();
        let variant_id = variant_id_for(&stop_ids);
        rows.push((
            feed_id.as_i64(),
            trip_id.clone(),
            trip.service_id.clone(),
            trip.trip_headsign.clone(),
            variant_id,
        ));
    }

    for chunk in rows.chunks(CHUNK) {
        let placeholders = pg_placeholders(chunk.len(), 5);
        let sql = format!(
            "INSERT INTO trips (feed_id, trip_id, service_id, trip_headsign, variant_id) VALUES {placeholders}
             ON CONFLICT (feed_id, trip_id) DO UPDATE SET
               service_id    = EXCLUDED.service_id,
               trip_headsign = EXCLUDED.trip_headsign,
               variant_id    = EXCLUDED.variant_id"
        );
        let mut q = sqlx::query(&sql);
        for (fid, tid, svc, head, vid) in chunk {
            q = q.bind(fid).bind(tid).bind(svc).bind(head).bind(vid);
        }
        q.execute(&mut **tx).await?;
    }
    info!("Loaded {} trips", gtfs.trips.len());
    Ok(())
}

async fn load_scheduled_stops(
    tx: &mut Tx<'_>,
    feed_id: FeedId,
    gtfs: &Gtfs,
    stop_map: &HashMap<String, StopId>,
) -> Result<()> {
    // Flatten all stop_times into (feed_id, trip_id, stop_id, seq, arrival, departure)
    // stop_id is Onestop ID when available; NULL when stop wasn't resolved
    let mut rows: Vec<(i64, String, Option<String>, i64, String, String)> = Vec::new();
    for (trip_id, trip) in &gtfs.trips {
        for st in &trip.stop_times {
            // Use Onestop ID when available; NULL when stop wasn't resolved.
            // In degraded mode (no Transitland), stop_map is empty so stop_id is always NULL —
            // this satisfies the nullable FK on scheduled_stops.stop_id → stops(onestop_id).
            let stop_id: Option<String> =
                stop_map.get(&st.stop.id).map(|sid| sid.as_str().to_owned());
            rows.push((
                feed_id.as_i64(),
                trip_id.clone(),
                stop_id,
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
             (feed_id, trip_id, stop_id, stop_sequence, arrival_time, departure_time) VALUES {placeholders}
             ON CONFLICT (feed_id, trip_id, stop_sequence) DO UPDATE SET
               stop_id        = EXCLUDED.stop_id,
               arrival_time   = EXCLUDED.arrival_time,
               departure_time = EXCLUDED.departure_time"
        );
        let mut q = sqlx::query(&sql);
        for (fid, tid, sid, seq, arr, dep) in chunk {
            q = q
                .bind(fid)
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

/// Load calendar.txt entries into the `services` table.
async fn load_services(
    tx: &mut Tx<'_>,
    feed_id: FeedId,
    agency_map: &HashMap<String, AgencyId>,
    calendar: &std::collections::HashMap<String, gtfs_structures::Calendar>,
) -> Result<()> {
    // Use the first resolved agency as the canonical agency_id for services
    // (GTFS calendar entries don't directly reference agency_id — they're referenced
    // by trips.service_id which belongs to a specific agency)
    let canonical_agency_id: Option<&str> = agency_map.values().next().map(|a| a.as_str());

    for cal in calendar.values() {
        sqlx::query(
            "INSERT INTO services
             (feed_id, service_id, agency_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, start_date, end_date)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
             ON CONFLICT (feed_id, service_id) DO UPDATE SET
               agency_id  = EXCLUDED.agency_id,
               monday     = EXCLUDED.monday,
               tuesday    = EXCLUDED.tuesday,
               wednesday  = EXCLUDED.wednesday,
               thursday   = EXCLUDED.thursday,
               friday     = EXCLUDED.friday,
               saturday   = EXCLUDED.saturday,
               sunday     = EXCLUDED.sunday,
               start_date = EXCLUDED.start_date,
               end_date   = EXCLUDED.end_date",
        )
        .bind(feed_id.as_i64())
        .bind(&cal.id)
        .bind(canonical_agency_id)
        .bind(cal.monday)
        .bind(cal.tuesday)
        .bind(cal.wednesday)
        .bind(cal.thursday)
        .bind(cal.friday)
        .bind(cal.saturday)
        .bind(cal.sunday)
        .bind(cal.start_date)
        .bind(cal.end_date)
        .execute(&mut **tx)
        .await?;
    }
    info!("Loaded {} service entries", calendar.len());
    Ok(())
}

/// Load calendar_dates.txt exceptions into the `service_exceptions` table.
pub(crate) async fn load_service_exceptions(
    tx: &mut Tx<'_>,
    feed_id: FeedId,
    calendar_dates: &std::collections::HashMap<String, Vec<gtfs_structures::CalendarDate>>,
) -> Result<()> {
    let mut count = 0usize;
    for (service_id, dates) in calendar_dates {
        for cd in dates {
            let exception_type: i16 = match cd.exception_type {
                Exception::Added => 1,
                Exception::Deleted => 2,
            };
            sqlx::query(
                "INSERT INTO service_exceptions (feed_id, service_id, date, exception_type)
                 VALUES ($1, $2, $3, $4)
                 ON CONFLICT (feed_id, service_id, date) DO NOTHING",
            )
            .bind(feed_id.as_i64())
            .bind(service_id.as_str())
            .bind(cd.date)
            .bind(exception_type)
            .execute(&mut **tx)
            .await?;
            count += 1;
        }
    }
    info!("Loaded {} service exceptions", count);
    Ok(())
}

/// Synthesize service rows from `calendar_dates.txt` for service_ids that have no
/// entry in `services` (was `calendar`). For each such service, inspects the Added dates
/// and sets the day-of-week flags based on which weekdays those dates fall on.
/// Uses `ON CONFLICT DO NOTHING` so real `calendar.txt` rows are never overwritten.
pub(crate) async fn load_services_from_dates(
    tx: &mut Tx<'_>,
    feed_id: FeedId,
    agency_map: &HashMap<String, AgencyId>,
    calendar_dates: &std::collections::HashMap<String, Vec<gtfs_structures::CalendarDate>>,
) -> Result<()> {
    let canonical_agency_id: Option<&str> = agency_map.values().next().map(|a| a.as_str());
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
            "INSERT INTO services
             (feed_id, service_id, agency_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
             ON CONFLICT (feed_id, service_id) DO NOTHING",
        )
        .bind(feed_id.as_i64())
        .bind(service_id.as_str())
        .bind(canonical_agency_id)
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
        "Synthesized {} service entries from calendar_dates",
        synthesized
    );
    Ok(())
}

async fn get_stored_version(db: &Database, feed_id: FeedId) -> Result<Option<String>> {
    let row: Option<(Option<String>,)> =
        sqlx::query_as("SELECT feed_version FROM feeds WHERE id = $1")
            .bind(feed_id.as_i64())
            .fetch_optional(&db.pool)
            .await?;
    Ok(row.and_then(|(v,)| v))
}

async fn get_last_ingested(
    db: &Database,
    feed_id: FeedId,
) -> Result<Option<chrono::DateTime<chrono::Utc>>> {
    let row: Option<(Option<chrono::DateTime<chrono::Utc>>,)> =
        sqlx::query_as("SELECT last_ingested_at FROM feeds WHERE id = $1")
            .bind(feed_id.as_i64())
            .fetch_optional(&db.pool)
            .await?;
    Ok(row.and_then(|(v,)| v))
}

async fn set_stored_version(db: &Database, feed_id: FeedId, version: &str) -> Result<()> {
    let now = chrono::Utc::now();
    sqlx::query("UPDATE feeds SET last_ingested_at = $1, feed_version = $2 WHERE id = $3")
        .bind(now)
        .bind(version)
        .bind(feed_id.as_i64())
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

    /// Insert a feed row so that FK constraints on feed_id are satisfied.
    async fn insert_feed(db: &Database, feed_id: i64) {
        sqlx::query(
            "INSERT INTO feeds (id, gtfs_static_url) VALUES ($1, 'https://example.com/test.zip')
             ON CONFLICT (id) DO NOTHING",
        )
        .bind(feed_id)
        .execute(&db.pool)
        .await
        .unwrap();
    }

    // ── load_variants tests ────────────────────────────────────────────────

    #[tokio::test]
    async fn load_variants_creates_variant_and_links_trips() {
        let td = test_utils::setup().await;
        let db = td.db;
        let feed_id = FeedId::from(1i64);
        insert_feed(&db, feed_id.as_i64()).await;

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

        let mut gtfs = Gtfs::default();
        gtfs.trips.insert("T1".to_string(), trip1);
        gtfs.trips.insert("T2".to_string(), trip2);
        gtfs.routes.insert("45".to_string(), make_route("45"));

        let stop_map: HashMap<String, StopId> = HashMap::new();
        let route_map: HashMap<String, RouteId> = HashMap::new();

        let mut tx = db.pool.begin().await.unwrap();
        load_variants(&mut tx, feed_id, &gtfs, &route_map, &stop_map)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        // One variant should exist.
        let variant_count: (i64,) =
            sqlx::query_as("SELECT COUNT(*) FROM route_variants WHERE feed_id = $1")
                .bind(feed_id.as_i64())
                .fetch_one(&db.pool)
                .await
                .unwrap();
        assert_eq!(variant_count.0, 1, "expected exactly one variant");

        // That variant should be primary with trip_count = 2.
        let (trip_count, is_primary): (i64, bool) = sqlx::query_as(
            "SELECT trip_count, is_primary FROM route_variants WHERE feed_id = $1",
        )
        .bind(feed_id.as_i64())
        .fetch_one(&db.pool)
        .await
        .unwrap();
        assert_eq!(trip_count, 2);
        assert!(is_primary);
    }

    #[tokio::test]
    async fn load_variants_marks_most_common_as_primary() {
        let td = test_utils::setup().await;
        let db = td.db;
        let feed_id = FeedId::from(1i64);
        insert_feed(&db, feed_id.as_i64()).await;

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
            gtfs.trips.insert(id, trip);
        }

        let stop_map: HashMap<String, StopId> = HashMap::new();
        let route_map: HashMap<String, RouteId> = HashMap::new();

        let mut tx = db.pool.begin().await.unwrap();
        load_variants(&mut tx, feed_id, &gtfs, &route_map, &stop_map)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        // Two variants should exist.
        let count: (i64,) =
            sqlx::query_as("SELECT COUNT(*) FROM route_variants WHERE feed_id = $1")
                .bind(feed_id.as_i64())
                .fetch_one(&db.pool)
                .await
                .unwrap();
        assert_eq!(count.0, 2);

        // The full-route variant (3 trips) should be primary.
        let primary_stop_count: (i64,) = sqlx::query_as(
            "SELECT stop_count FROM route_variants WHERE feed_id = $1 AND is_primary = TRUE",
        )
        .bind(feed_id.as_i64())
        .fetch_one(&db.pool)
        .await
        .unwrap();
        assert_eq!(
            primary_stop_count.0, 4,
            "primary variant should be the 4-stop full route"
        );
    }

    #[tokio::test]
    async fn load_services_inserts_service_day_flags() {
        let td = test_utils::setup().await;
        let db = td.db;
        let feed_id = FeedId::from(1i64);
        insert_feed(&db, feed_id.as_i64()).await;
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

        let agency_map: HashMap<String, AgencyId> = HashMap::new();
        load_services(&mut tx, feed_id, &agency_map, &calendar)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        let rows: Vec<(String, bool, bool, bool)> = sqlx::query_as(
            "SELECT service_id, monday, saturday, sunday FROM services WHERE feed_id = $1 ORDER BY service_id",
        )
        .bind(feed_id.as_i64())
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
    async fn load_services_from_dates_synthesizes_day_flags_from_added_dates() {
        let td = test_utils::setup().await;
        let db = td.db;
        let feed_id = FeedId::from(1i64);
        insert_feed(&db, feed_id.as_i64()).await;
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

        let agency_map: HashMap<String, AgencyId> = HashMap::new();
        load_services_from_dates(&mut tx, feed_id, &agency_map, &calendar_dates)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        type ServiceRow = (String, bool, bool, bool, bool, bool, bool, bool);
        let rows: Vec<ServiceRow> = sqlx::query_as(
            "SELECT service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday
             FROM services WHERE feed_id = $1 ORDER BY service_id",
        )
        .bind(feed_id.as_i64())
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
    async fn load_services_from_dates_does_not_overwrite_calendar_txt_entry() {
        let td = test_utils::setup().await;
        let db = td.db;
        let feed_id = FeedId::from(1i64);
        insert_feed(&db, feed_id.as_i64()).await;
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
        let agency_map: HashMap<String, AgencyId> = HashMap::new();
        load_services(&mut tx, feed_id, &agency_map, &calendar)
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
        load_services_from_dates(&mut tx, feed_id, &agency_map, &calendar_dates)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        let row: (bool, bool) = sqlx::query_as(
            "SELECT saturday, monday FROM services WHERE feed_id = $1 AND service_id = 'WD'",
        )
        .bind(feed_id.as_i64())
        .fetch_one(&db.pool)
        .await
        .unwrap();

        assert!(
            !row.0,
            "saturday should remain false — calendar.txt takes precedence"
        );
        assert!(row.1, "monday should still be true");
    }

    #[tokio::test]
    async fn load_service_exceptions_inserts_added_and_removed() {
        let td = test_utils::setup().await;
        let db = td.db;
        let feed_id = FeedId::from(1i64);
        insert_feed(&db, feed_id.as_i64()).await;

        // Insert service row first (FK constraint)
        sqlx::query(
            "INSERT INTO services (feed_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday)
             VALUES ($1, 'WD', true, true, true, true, true, false, false)",
        )
        .bind(feed_id.as_i64())
        .execute(&db.pool)
        .await
        .unwrap();

        let mut tx = db.pool.begin().await.unwrap();

        let mut calendar_dates: HashMap<String, Vec<gtfs_structures::CalendarDate>> =
            HashMap::new();
        calendar_dates.insert(
            "WD".to_string(),
            vec![
                gtfs_structures::CalendarDate {
                    service_id: "WD".to_string(),
                    date: NaiveDate::from_ymd_opt(2026, 1, 1).unwrap(),
                    exception_type: gtfs_structures::Exception::Added,
                },
                gtfs_structures::CalendarDate {
                    service_id: "WD".to_string(),
                    date: NaiveDate::from_ymd_opt(2026, 1, 2).unwrap(),
                    exception_type: gtfs_structures::Exception::Removed,
                },
            ],
        );

        load_service_exceptions(&mut tx, feed_id, &calendar_dates)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        let rows: Vec<(String, i16)> = sqlx::query_as(
            "SELECT date::TEXT, exception_type FROM service_exceptions WHERE feed_id = $1 ORDER BY date",
        )
        .bind(feed_id.as_i64())
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 2);
        assert_eq!(rows[0].1, 1, "Added = 1");
        assert_eq!(rows[1].1, 2, "Removed = 2");
    }
}
