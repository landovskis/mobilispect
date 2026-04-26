#!/usr/bin/env bash
set -e

if ! command -v cargo-watch &>/dev/null; then
  echo "Installing cargo-watch..."
  cargo install cargo-watch
fi

if ! docker ps --format '{{.Names}}' | grep -q '^mobilispect-pg$'; then
  echo "Starting mobilispect-pg..."
  docker run -d \
    --name mobilispect-pg \
    -e POSTGRES_USER=mobilispect \
    -e POSTGRES_PASSWORD=mobilispect \
    -e POSTGRES_DB=mobilispect \
    -p 5433:5432 \
    postgres:16
fi

export MOBILISPECT_DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect

trap 'kill 0' EXIT

cargo watch -s 'dotenvx run -- cargo run --bin mobilispect-server' &
cargo watch -s 'dotenvx run -- cargo run --bin mobilispect-worker' &

wait
