# =============================================================================
# Stage 1 — Builder
# =============================================================================
FROM rust:1.83-slim AS builder

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

# Copy dependency manifests first so the cargo dependency layer is cached
# independently of source changes.
COPY Cargo.toml Cargo.lock ./

# Create a stub main so `cargo build --release` can compile all deps without
# the real source tree. The stub is replaced in the next COPY step.
RUN mkdir -p src && echo 'fn main() {}' > src/main.rs \
    && cargo build --release \
    && rm -rf src

# Copy the full source tree (including proto/ for build.rs).
COPY build.rs ./
COPY proto/ proto/
COPY src/ src/
COPY migrations/ migrations/

# Touch main.rs so Cargo knows it changed and rebuilds the final binary.
RUN touch src/main.rs && cargo build --release

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

# Persistent volume for the SQLite database file.
VOLUME ["/data"]

# Default to an absolute path inside the /data volume so the SQLite file
# survives container restarts when /data is mounted as a named volume.
ENV DATABASE_URL=sqlite:///data/mobilispect.db

# The application listens on port 3000 by default (BIND_ADDRESS=0.0.0.0:3000).
EXPOSE 3000

USER mobilispect

ENTRYPOINT ["/usr/local/bin/mobilispect"]
