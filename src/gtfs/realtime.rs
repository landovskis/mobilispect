use anyhow::Result;
use chrono::Utc;
use tracing::{error, info};

use crate::config::AgencyConfig;
use crate::db::Database;

// GTFS-RT protobuf types — generated from the official .proto file
// Include the generated code from build.rs output
pub mod proto {
    include!(concat!(env!("OUT_DIR"), "/transit_realtime.rs"));
}

use prost::Message;

pub async fn poll_loop(db: &Database, agency: &AgencyConfig, poll_interval_secs: u64) {
    let interval = std::time::Duration::from_secs(poll_interval_secs);
    loop {
        if let Err(e) = poll_once(db, agency).await {
            error!("GTFS-RT poll error ({}): {}", agency.slug, e);
        }
        tokio::time::sleep(interval).await;
    }
}

async fn poll_once(db: &Database, agency: &AgencyConfig) -> Result<()> {
    let now = Utc::now().to_rfc3339();

    // Fetch VehiclePositions
    let vp_bytes = fetch_feed(&agency.gtfs_rt_vehicle_positions_url, agency.gtfs_api_key.as_deref()).await?;
    let vp_feed = proto::FeedMessage::decode(vp_bytes.as_ref())?;
    let vp_count = store_vehicle_positions(db, &vp_feed, &now).await?;

    // Fetch TripUpdates
    let tu_bytes = fetch_feed(&agency.gtfs_rt_trip_updates_url, agency.gtfs_api_key.as_deref()).await?;
    let tu_feed = proto::FeedMessage::decode(tu_bytes.as_ref())?;
    let tu_count = store_trip_updates(db, &tu_feed, &now).await?;

    info!(
        "GTFS-RT poll complete: {} vehicle positions, {} stop time events",
        vp_count, tu_count
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

async fn store_vehicle_positions(
    db: &Database,
    feed: &proto::FeedMessage,
    observed_at: &str,
) -> Result<usize> {
    let mut count = 0;
    for entity in &feed.entity {
        let Some(vp) = &entity.vehicle else { continue };
        let Some(pos) = &vp.position else { continue };

        let trip_id = vp.trip.as_ref().and_then(|t| t.trip_id.clone());
        let vehicle_id = vp.vehicle.as_ref().and_then(|v| v.id.clone());
        let status = vp.current_status.map(|s| s.to_string());
        let stop_seq = vp.current_stop_sequence.map(|s| s as i64);
        let lat = pos.latitude as f64;
        let lon = pos.longitude as f64;
        let bearing = pos.bearing.map(|b| b as f64);
        let speed = pos.speed.map(|s| s as f64);

        sqlx::query!(
            "INSERT INTO vehicle_positions
             (observed_at, trip_id, vehicle_id, latitude, longitude, bearing, speed, current_status, stop_sequence)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            observed_at,
            trip_id,
            vehicle_id,
            lat,
            lon,
            bearing,
            speed,
            status,
            stop_seq,
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
    observed_at: &str,
) -> Result<usize> {
    let mut count = 0;
    for entity in &feed.entity {
        let Some(tu) = &entity.trip_update else { continue };
        let Some(trip_id) = &tu.trip.trip_id else { continue };

        for stu in &tu.stop_time_update {
            let stop_id = stu.stop_id.clone().unwrap_or_default();
            let stop_seq = stu.stop_sequence.map(|s| s as i64);
            // STM provides absolute times (Unix seconds), not delays
            let arrival_delay = stu.arrival.as_ref().and_then(|a| a.delay).map(|d| d as i64);
            let departure_delay = stu.departure.as_ref().and_then(|d| d.delay).map(|d| d as i64);
            let arrival_time_unix = stu.arrival.as_ref().and_then(|a| a.time).map(|t| t as i64);
            let departure_time_unix = stu.departure.as_ref().and_then(|d| d.time).map(|t| t as i64);

            sqlx::query!(
                "INSERT INTO stop_time_events
                 (observed_at, trip_id, stop_id, stop_sequence, arrival_delay, departure_delay,
                  arrival_time_unix, departure_time_unix)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                observed_at,
                trip_id,
                stop_id,
                stop_seq,
                arrival_delay,
                departure_delay,
                arrival_time_unix,
                departure_time_unix,
            )
            .execute(&db.pool)
            .await?;
            count += 1;
        }
    }
    Ok(count)
}
