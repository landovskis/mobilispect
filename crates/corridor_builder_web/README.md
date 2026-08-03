# Corridor Builder (WASM shell)

A Yew/Trunk WASM frontend for building street corridor "remixes" scoped to a
metro region. See
`docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md` for
the full design.

This crate is intentionally excluded from the root Cargo workspace (it
targets `wasm32-unknown-unknown` and is built with `trunk`, not `cargo
build`). `mobilispect-server` serves its build output at `/builder` via
`ServeDir::new("crates/corridor_builder_web/dist")`.

## Building

```bash
rustup target add wasm32-unknown-unknown
cargo install trunk --locked
trunk build            # from within this directory (crates/corridor_builder_web/)
```

`trunk build --release` produces the optimized assets in `dist/` that
`mobilispect-server` serves. The `Dockerfile`'s builder stage runs this same
sequence to produce the assets shipped in the runtime image.

## Manual bounding-box setup (no admin UI yet)

The metro-region picker only lists regions that have a bounding box set
(`min_lat`, `min_lon`, `max_lat`, `max_lon` all non-null on the `regions`
row). First-launch setup (`POST /setup`) creates a region row without a
bounding box, and there is currently no admin UI to set one — see the design
spec's "Out of Scope" section for why. Until that UI exists, populating a
region's bounding box is a manual, one-time operator step: run a direct SQL
`UPDATE` against the region's row.

```sql
UPDATE regions SET min_lat = <south>, min_lon = <west>,
                    max_lat = <north>, max_lon = <east>
WHERE id = <region id>;
```

A region will not appear in the corridor builder's metro-region picker until
this has been done.
