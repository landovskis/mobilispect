use anyhow::Result;
use chrono::{DateTime, Utc};
use tracing::{error, info};

use mobilispect_core::config::FeedConfig;
use mobilispect_core::db::Database;
use mobilispect_core::ids::FeedId;
// GTFS-RT protobuf types — generated from the official .proto file
// Include the generated code from build.rs output
pub mod proto {
    #![allow(clippy::enum_variant_names)]
    include!(concat!(env!("OUT_DIR"), "/transit_realtime.rs"));
}

use prost::Message;

pub async fn poll_loop(db: &Database, feed: &FeedConfig, poll_interval_secs: u64) {
    let interval = std::time::Duration::from_secs(poll_interval_secs);
    let feed_id = FeedId::from(feed.id);
    loop {
        if let Err(e) = poll_once(db, feed, feed_id).await {
            error!("GTFS-RT poll error (feed_id={}): {}", feed_id, e);
        }
        tokio::time::sleep(interval).await;
    }
}

async fn poll_once(db: &Database, feed: &FeedConfig, feed_id: FeedId) -> Result<()> {
    let observed_at = Utc::now();

    // Fetch VehiclePositions (optional — skip if URL not configured)
    let vp_count = if let Some(url) = &feed.gtfs_rt_vehicle_positions_url {
        let vp_bytes = fetch_feed(url, feed.gtfs_api_key.as_deref()).await?;
        let vp_feed = proto::FeedMessage::decode(vp_bytes.as_ref())?;
        store_vehicle_positions(db, &vp_feed, observed_at, feed_id).await?
    } else {
        0
    };

    // Fetch TripUpdates (optional — skip if URL not configured)
    let tu_count = if let Some(url) = &feed.gtfs_rt_trip_updates_url {
        let tu_bytes = fetch_feed(url, feed.gtfs_api_key.as_deref()).await?;
        let tu_feed = proto::FeedMessage::decode(tu_bytes.as_ref())?;
        store_trip_updates(db, &tu_feed, observed_at, feed_id).await?
    } else {
        0
    };

    crate::pipeline::run_realtime_hooks(db, feed).await?;

    info!(
        "GTFS-RT poll complete (feed_id={}): {} vehicle positions, {} stop time events",
        feed_id, vp_count, tu_count
    );
    Ok(())
}

async fn fetch_feed(url: &str, api_key: Option<&str>) -> Result<bytes::Bytes> {
    let client = reqwest::Client::new();
    let mut req = client.get(url);
    if let Some(key) = api_key {
        req = req.header("apiKey", key);
    }
    let bytes = req.send().await?.bytes().await?;
    Ok(bytes)
}

/// Look up the Onestop ID for a GTFS stop within a feed.
/// Returns `None` if no mapping exists.
async fn resolve_stop_onestop_id(
    pool: &sqlx::PgPool,
    feed_id: FeedId,
    gtfs_stop_id: &str,
) -> Option<String> {
    sqlx::query_scalar!(
        r#"SELECT onestop_id as "onestop_id: String" FROM feed_stop_ids WHERE feed_id = $1 AND gtfs_stop_id = $2"#,
        feed_id.as_i64(),
        gtfs_stop_id
    )
    .fetch_optional(pool)
    .await
    .ok()
    .flatten()
}

/// Map a GTFS-RT VehicleStopStatus proto enum value to its SQL enum string.
fn vehicle_stop_status_str(status: i32) -> Option<String> {
    match status {
        0 => Some("IN_TRANSIT_TO".to_string()),
        1 => Some("INCOMING_AT".to_string()),
        2 => Some("STOPPED_AT".to_string()),
        _ => None,
    }
}

/// Map a GTFS-RT CongestionLevel proto enum value to its SQL enum string.
fn congestion_level_str(level: i32) -> Option<String> {
    match level {
        0 => Some("UNKNOWN_CONGESTION_LEVEL".to_string()),
        1 => Some("RUNNING_SMOOTHLY".to_string()),
        2 => Some("STOP_AND_GO".to_string()),
        3 => Some("CONGESTION".to_string()),
        4 => Some("SEVERE_CONGESTION".to_string()),
        _ => None,
    }
}

/// Map a GTFS-RT StopTimeUpdate ScheduleRelationship proto enum value to its SQL enum string.
fn schedule_relationship_str(rel: i32) -> Option<String> {
    match rel {
        0 => Some("SCHEDULED".to_string()),
        1 => Some("SKIPPED".to_string()),
        2 => Some("NO_DATA".to_string()),
        3 => Some("UNSCHEDULED".to_string()),
        _ => None,
    }
}

async fn store_vehicle_positions(
    db: &Database,
    feed: &proto::FeedMessage,
    observed_at: DateTime<Utc>,
    feed_id: FeedId,
) -> Result<usize> {
    let mut count = 0;
    for entity in &feed.entity {
        let Some(vp) = &entity.vehicle else { continue };
        let Some(pos) = &vp.position else { continue };

        let trip_id = vp.trip.as_ref().and_then(|t| t.trip_id.clone());
        let vehicle_id = vp.vehicle.as_ref().and_then(|v| v.id.clone());
        let status = vp.current_status.and_then(vehicle_stop_status_str);
        let stop_seq = vp.current_stop_sequence.map(|s| s as i64);
        let lat = pos.latitude as f64;
        let lon = pos.longitude as f64;
        let bearing = pos.bearing.map(|b| b as f64);
        let speed = pos.speed.map(|s| s as f64);
        // occupancy_status is not present in the current proto definition
        let occupancy: Option<String> = None;
        let congestion = vp.congestion_level.and_then(congestion_level_str);

        // Resolve stop Onestop ID from feed_stop_ids mapping
        let stop_onestop_id = if let Some(ref gtfs_stop_id) = vp.stop_id {
            resolve_stop_onestop_id(&db.pool, feed_id, gtfs_stop_id).await
        } else {
            None
        };

        sqlx::query!(
            "INSERT INTO vehicle_positions
             (feed_id, observed_at, trip_id, vehicle_id, latitude, longitude, bearing, speed,
              current_status, stop_sequence, stop_id, occupancy_status, congestion_level)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8,
                     $9::vehicle_stop_status, $10, $11, $12::occupancy_status_enum, $13::congestion_level_enum)",
            feed_id.as_i64(),
            observed_at,
            trip_id,
            vehicle_id,
            lat,
            lon,
            bearing,
            speed,
            status as Option<String>,
            stop_seq,
            stop_onestop_id,
            occupancy as Option<String>,
            congestion as Option<String>,
        )
        .execute(&db.pool)
        .await?;
        count += 1;
    }
    Ok(count)
}

async fn store_trip_updates(
    db: &Database,
    feed: &proto::FeedMessage,
    observed_at: DateTime<Utc>,
    feed_id: FeedId,
) -> Result<usize> {
    let mut count = 0;
    for entity in &feed.entity {
        let Some(tu) = &entity.trip_update else {
            continue;
        };
        let Some(trip_id) = &tu.trip.trip_id else {
            continue;
        };

        let schedule_rel = tu
            .trip
            .schedule_relationship
            .and_then(schedule_relationship_str);

        for stu in &tu.stop_time_update {
            let gtfs_stop_id = stu.stop_id.as_deref().unwrap_or_default();
            let stop_onestop_id = if gtfs_stop_id.is_empty() {
                None
            } else {
                resolve_stop_onestop_id(&db.pool, feed_id, gtfs_stop_id).await
            };

            let stop_seq = stu.stop_sequence.map(|s| s as i64);
            let arrival_delay = stu.arrival.as_ref().and_then(|a| a.delay).map(|d| d as i64);
            let departure_delay = stu
                .departure
                .as_ref()
                .and_then(|d| d.delay)
                .map(|d| d as i64);

            // Convert Unix timestamps to TIMESTAMPTZ
            let arrival_time: Option<DateTime<Utc>> = stu
                .arrival
                .as_ref()
                .and_then(|a| a.time)
                .and_then(|ts| DateTime::from_timestamp(ts, 0));
            let departure_time: Option<DateTime<Utc>> = stu
                .departure
                .as_ref()
                .and_then(|d| d.time)
                .and_then(|ts| DateTime::from_timestamp(ts, 0));

            // Proto defines uncertainty as float; DB column is INTEGER — truncate.
            let uncertainty: Option<i32> = stu
                .arrival
                .as_ref()
                .and_then(|a| a.uncertainty)
                .map(|u| u as i32);

            // Per-stop schedule relationship overrides trip-level if set
            let stu_schedule_rel = stu
                .schedule_relationship
                .and_then(schedule_relationship_str)
                .or_else(|| schedule_rel.clone());

            sqlx::query!(
                "INSERT INTO stop_time_events
                 (feed_id, observed_at, trip_id, stop_id, stop_sequence, arrival_delay,
                  departure_delay, arrival_time, departure_time, schedule_relationship, uncertainty)
                 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9,
                         $10::stop_time_schedule_relationship, $11)",
                feed_id.as_i64(),
                observed_at,
                trip_id,
                stop_onestop_id,
                stop_seq,
                arrival_delay,
                departure_delay,
                arrival_time,
                departure_time,
                stu_schedule_rel as Option<String>,
                uncertainty,
            )
            .execute(&db.pool)
            .await?;
            count += 1;
        }
    }
    Ok(count)
}

#[cfg(test)]
mod tests {
    use chrono::Utc;
    use mobilispect_core::db::test_utils;
    use mobilispect_core::ids::FeedId;

    #[tokio::test]
    async fn dwell_secs_computed_from_timestamps() {
        let test_db = test_utils::setup().await;
        let pool = &test_db.db.pool;
        let feed_id: i64 = 1;
        let observed_at = Utc::now();
        let arrival = chrono::DateTime::from_timestamp(1000, 0).unwrap();
        let departure = chrono::DateTime::from_timestamp(1045, 0).unwrap();

        sqlx::query!(
            "INSERT INTO stop_time_events
             (feed_id, observed_at, trip_id, stop_id,
              arrival_time, departure_time)
             VALUES ($1, $2, $3, $4, $5, $6)",
            feed_id,
            observed_at,
            "trip-1",
            None::<String>,
            arrival,
            departure,
        )
        .execute(pool)
        .await
        .unwrap();

        let row = sqlx::query!(
            "SELECT dwell_secs FROM stop_time_events WHERE trip_id = $1",
            "trip-1"
        )
        .fetch_one(pool)
        .await
        .unwrap();

        assert_eq!(row.dwell_secs, Some(45));
    }

    #[tokio::test]
    async fn dwell_secs_is_null_when_arrival_missing() {
        let test_db = test_utils::setup().await;
        let pool = &test_db.db.pool;
        let feed_id: i64 = 1;
        let observed_at = Utc::now();
        let departure = chrono::DateTime::from_timestamp(1045, 0).unwrap();

        sqlx::query!(
            "INSERT INTO stop_time_events
             (feed_id, observed_at, trip_id, stop_id,
              arrival_time, departure_time)
             VALUES ($1, $2, $3, $4, $5, $6)",
            feed_id,
            observed_at,
            "trip-2",
            None::<String>,
            None::<chrono::DateTime<Utc>>,
            departure,
        )
        .execute(pool)
        .await
        .unwrap();

        let row = sqlx::query!(
            "SELECT dwell_secs FROM stop_time_events WHERE trip_id = $1",
            "trip-2"
        )
        .fetch_one(pool)
        .await
        .unwrap();

        assert_eq!(row.dwell_secs, None);
    }

    #[tokio::test]
    async fn resolve_stop_onestop_id_returns_none_when_not_found() {
        let test_db = test_utils::setup().await;
        let pool = &test_db.db.pool;
        let feed_id = FeedId::from(99i64);

        let result = super::resolve_stop_onestop_id(pool, feed_id, "stop-nonexistent").await;
        assert_eq!(result, None);
    }
}
