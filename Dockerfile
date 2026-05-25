# =============================================================================
# Stage 1 — Builder
# =============================================================================
FROM rust:slim AS builder

# Install build-time dependencies:
#   - pkg-config: needed by openssl-sys to locate libssl
#   - libssl-dev: OpenSSL headers/static libs for reqwest TLS support
#   - protobuf-compiler: prost-build calls protoc during build.rs execution
RUN apt-get update && apt-get install -y --no-install-recommends \
    pkg-config \
    libssl-dev \
    protobuf-compiler \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# sqlx uses compile-time query verification; SQLX_OFFLINE=true uses the cached
# query metadata in .sqlx/ instead of requiring a live database connection.
ENV SQLX_OFFLINE=true

# Copy config separately from source so config changes don't invalidate the Rust build cache.
COPY config.toml ./
COPY . .

RUN cargo build --release

# =============================================================================
# Stage 2 — Runtime
# =============================================================================
FROM debian:bookworm-slim AS runtime

# Install runtime dependencies:
#   - ca-certificates: trust anchors for TLS (reqwest HTTPS calls to GTFS APIs)
#   - libssl3: OpenSSL shared library required by the dynamically-linked binary
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libssl3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy compiled binaries.
# NOTE: migrations/ is NOT copied here. sqlx::migrate!() is a compile-time
# macro that embeds migration SQL directly into the binary.
COPY --from=builder /build/target/release/mobilispect-server /usr/local/bin/mobilispect-server
COPY --from=builder /build/target/release/mobilispect-worker /usr/local/bin/mobilispect-worker

# Write config.toml inline. All values here are non-secret; secrets (database URL,
# API keys) are resolved at startup via the *_env fields from process environment variables.
RUN printf '%s\n' \
    'database_url_env = "MOBILISPECT_DATABASE_URL"' \
    'poll_interval_secs = 30' \
    'bind_address = "0.0.0.0:3000"' \
    'on_time_early_threshold_secs = -60' \
    'on_time_late_threshold_secs = 300' \
    'retention_days = 30' \
    '' \
    '[region]' \
    'name = "Montreal"' \
    'timezone = "America/Toronto"' \
    '' \
    '[[region.agencies]]' \
    'id = 0' \
    'name = "STM"' \
    'gtfs_static_url = "https://www.stm.info/sites/default/files/gtfs/gtfs_stm.zip"' \
    'gtfs_rt_vehicle_positions_url = "https://api.stm.info/pub/od/gtfs-rt/ic/v2/vehiclePositions"' \
    'gtfs_rt_trip_updates_url = "https://api.stm.info/pub/od/gtfs-rt/ic/v2/tripUpdates"' \
    'gtfs_api_key_env = "STM_GTFS_RT_API_KEY"' \
    'agency_utc_offset = "-04:00"' \
    > /app/config.toml

# The application listens on port 3000 by default (bind_address in config.toml).
EXPOSE 3000

# Default to server. Railway overrides CMD per service via the startCommand setting.
CMD ["/usr/local/bin/mobilispect-server"]
