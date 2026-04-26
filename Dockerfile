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

# Copy only the compiled binary from the builder stage.
# NOTE: migrations/ is NOT copied here. sqlx::migrate!() is a compile-time
# macro that embeds migration SQL directly into the binary.
COPY --from=builder /build/target/release/mobilispect-server /usr/local/bin/mobilispect-server
COPY --from=builder /build/target/release/mobilispect-worker /usr/local/bin/mobilispect-worker

# config.toml must be available in the working directory, or set MOBILISPECT_CONFIG.
# Secret values referenced by *_env config fields must be present in the process env.
# On Railway, reference the Postgres plugin: postgres://$PGUSER:$PGPASSWORD@$PGHOST:$PGPORT/$PGDATABASE

# The application listens on port 3000 by default (bind_address in config.toml).
EXPOSE 3000

# Default to server; override with: docker run --entrypoint /usr/local/bin/mobilispect-worker <image>
ENTRYPOINT ["/usr/local/bin/mobilispect-server"]
