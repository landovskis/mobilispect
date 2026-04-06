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

# Create a non-root user for the process.
RUN groupadd --system mobilispect \
    && useradd --system --gid mobilispect --no-create-home mobilispect

# Create the data directory for the SQLite database file.
RUN mkdir -p /data && chown mobilispect:mobilispect /data

# Copy only the compiled binary from the builder stage.
# NOTE: migrations/ is NOT copied here. sqlx::migrate!() is a compile-time
# macro that embeds migration SQL directly into the binary.
COPY --from=builder /build/target/release/mobilispect /usr/local/bin/mobilispect

# /data is used for the SQLite database file. Mount a Railway volume at /data
# via the Railway dashboard — do not use the VOLUME directive (banned by Railway).

# Default to an absolute path inside the /data volume so the SQLite file
# survives container restarts when a Railway volume is mounted there.
ENV DATABASE_URL=sqlite:///data/mobilispect.db

# The application listens on port 3000 by default (BIND_ADDRESS=0.0.0.0:3000).
EXPOSE 3000

USER mobilispect

ENTRYPOINT ["/usr/local/bin/mobilispect"]
