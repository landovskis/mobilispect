# Research: Average Stop Spacing

## Decision 1: Source for along-route distance

- **Decision**: Use `stop_times.shape_dist_traveled` when present; otherwise
  compute along-route distance from `shapes.txt` points; if no shape data is
  available, mark spacing as not available.
- **Rationale**: `shape_dist_traveled` provides canonical along-route distances
  per stop. When missing, shapes still allow distance along the route geometry.
  Avoids misleading straight-line spacing while honoring the along-route
  requirement.
- **Alternatives considered**: Straight-line (Haversine) distances between stops
  only; rejected because it conflicts with the along-route clarification and
  can misclassify curvy routes.

## Decision 2: Aggregation per variant

- **Decision**: Compute average spacing as the mean of consecutive stop-to-stop
  distances along the variant's stop sequence.
- **Rationale**: Matches the definition of spacing between stops and aligns with
  route-variant stop patterns already tracked.
- **Alternatives considered**: Total route length divided by stop count;
  rejected because it hides uneven spacing and can skew short patterns.

## Decision 3: Storage and exposure

- **Decision**: Persist average stop spacing (km) on the route variant and
  derive classification in the API response.
- **Rationale**: Persisted spacing avoids recomputation on every query;
  classification remains deterministic and can be recalculated if thresholds
  change.
- **Alternatives considered**: Persist both spacing and classification; rejected
  to avoid redundant state.
