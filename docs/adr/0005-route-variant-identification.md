# ADR 0005: Route Variant Identification Strategy

**Date**: 2025-11-27
**Status**: Accepted
**Feature**: 003-transit-route-frequency

## Context

Transit routes often have multiple service variants - different routing patterns serving the same agency and route number. Examples:
- Route 5 "Express" vs Route 5 "Local" (different stop sequences)
- Route 12 weekday vs weekend (overlapping but distinct patterns)
- Route 7 AM peak variant (deadheading to/from layover)

The feature must:
1. **Identify variants uniquely and consistently** across feed versions (same variant should have same identifier even when feed is re-imported)
2. **Detect variants efficiently** when analyzing large route networks (100+ routes with multiple variants each)
3. **Support frequency analysis** by grouping trips with identical stop patterns
4. **Enable historical tracking** without ID drift (variant IDs should be stable over time)

Current challenge: Transit agencies often don't provide consistent variant identifiers in GTFS trip_headsign or route_type fields. We must derive variant identity from the actual stop pattern data.

## Decision

**Use SHA-256 hash of ordered stop sequences as the primary route variant identifier.**

### Rationale for Hash-Based Approach

1. **Content-Based Identity**: Hash reflects actual stop pattern, ensuring same pattern = same identifier regardless of feed version
2. **Stability**: Hash remains constant when stop sequence unchanged; enables historical tracking without ID management
3. **Uniqueness**: SHA-256 collision resistance (1 in 2^128) makes false variant identification virtually impossible
4. **Deterministic**: Same stop sequence always produces identical hash; no randomness or initialization state
5. **Computational Efficiency**:
   - Hash generation is O(n) complexity (one pass through stops)
   - Hash comparison is O(1) (simple string comparison)
   - Scales efficiently for 1000+ variants
6. **Clarity**: Content-based identifier makes intent obvious ("this variant has THIS stop pattern")

### Implementation

```kotlin
// Value class for type safety
@JvmInline
value class VariantHash(val value: String) {
    companion object {
        fun generate(stopIds: List<String>): VariantHash {
            val concatenated = stopIds.joinToString(separator = "|")
            val bytes = concatenated.toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(bytes)
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            return VariantHash(hexString)
        }
    }
}

data class RouteVariant(
    val id: VariantHash,
    val routeId: String,
    val agencyId: String,
    val stopPattern: List<String>,  // Ordered stop IDs
    val direction: Int,              // 0=inbound, 1=outbound
    val headsignExamples: Set<String> = emptySet()
)

// Usage: group trips by variant
val variantMap = trips
    .groupBy { trip ->
        val stopSequence = trip.stopTimes
            .sortedBy { it.stopSequence }
            .map { it.stop.id }
        val direction = trip.directionId ?: 0
        VariantHash.generate(stopSequence + direction.toString())
    }
```

### Edge Case Handling

1. **Circular Routes (Same Start/End)**
   - Normalize to canonical starting point (lowest stop ID) before hashing
   - Prevents Route 1 starting at Stop A vs Stop B from creating different hashes

2. **Bidirectional Routes**
   - Include direction indicator (0/1) in hash input
   - Route 5 Inbound ≠ Route 5 Outbound (different direction values)

3. **Minimal Differences (1-2 stops)**
   - Still create separate hashes (correct behavior)
   - Flag similar variants (Levenshtein distance < 10% stops) for review
   - Use secondary similarity matrix for analytics

## Consequences

### Positive

1. **Stable Identifiers**: Variant ID doesn't change when feed reimported (same stops = same hash)
2. **Immutable History**: Can track frequency changes for specific variant over time
3. **Prevents False Duplicates**: If variant is reordered or modified, automatically gets new hash
4. **No Database Dependency**: Hash generation is stateless; no sequence/ID generator needed
5. **Supports Research**: Allows analysts to correlate historical frequency data across feed versions

### Negative

1. **Opaque IDs**: Hash value (SHA-256) is not human-readable (64-char hex string)
   - Mitigation: Always display with route+headsign context in UI
2. **Hash Sensitivity**: Single stop reorder creates completely different hash
   - Mitigation: This is actually correct behavior; variant truly changed
3. **No Order Stability**: If two stops have identical IDs (edge case), order-dependent
   - Mitigation: Unlikely with valid GTFS; validate upstream

## Alternatives Considered

### 1. Agency-Provided Trip IDs (Rejected)

**Rationale**: Inconsistent across feed versions
- Some agencies change internal trip IDs when feed is re-exported
- No correlation possible between "trip_123" in Nov feed vs "trip_456" in Dec feed
- Different feed formats may use different ID schemes
- Transitland doesn't provide stable trip identifiers

### 2. Sequential Integer IDs (Rejected)

**Rationale**: Unstable when new variants are inserted
- If new variant A is discovered, all subsequent variants shift IDs
- Historical frequency data becomes unanchored to new ID scheme
- Query "give me frequency for variant 42" returns different variant after reimport

### 3. Stop Pattern Comparison (O(n²) Complexity) (Rejected)

**Rationale**: Performance unacceptable at scale
- Comparing every variant pair: O(variants²) = O(v²)
- 1000 variants would require 500K comparisons
- Each comparison is O(n*m) = O(stops²) if doing detailed diff
- Hash approach is O(variants) = O(v) for lookup

### 4. Geographic Hash (GPS Coordinates) (Rejected)

**Rationale**: Unreliable and complex
- GPS coordinates subject to rounding errors and drift
- Doesn't reflect actual stop sequence logic
- Hard to explain to operators/planners

## Related Decisions

- **ADR 0004**: Uses OneBusAway library to extract stop sequences from GTFS trips
- **ADR 0006**: Common section detection relies on comparing VariantHash instances
- **ADR 0007**: Frequency calculations are keyed by VariantHash

## Open Questions

None. Decision is complete and ready for implementation.
