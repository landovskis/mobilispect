package com.mobilispect.backend.route.domain.service

import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.ParallelRouteGroup
import com.mobilispect.backend.route.domain.model.RouteVariantWithStops
import com.mobilispect.backend.route.domain.model.StopWithLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.springframework.stereotype.Service

/**
 * Detects groups of route variants whose corridors are geographically parallel.
 *
 * Two variants are considered parallel when the average directed Hausdorff distance between their
 * stop sets is at or below [thresholdMeters]. Variants are then clustered into connected components
 * using a Union-Find algorithm so that transitively-parallel routes end up in the same group.
 */
interface ParallelRouteDetectionService {
  /**
   * Detect groups of parallel route variants.
   *
   * @param variants Route variants enriched with stop coordinates
   * @param thresholdMeters Maximum average Hausdorff distance (metres) for two variants to be
   *   considered parallel
   * @return Groups of parallel variants; groups with fewer than two variants are omitted
   */
  fun detectParallelRoutes(
    variants: List<RouteVariantWithStops>,
    thresholdMeters: Double,
  ): List<ParallelRouteGroup>
}

@Service
class ParallelRouteDetectionServiceImpl : ParallelRouteDetectionService {

  override fun detectParallelRoutes(
    variants: List<RouteVariantWithStops>,
    thresholdMeters: Double,
  ): List<ParallelRouteGroup> {
    if (variants.size < 2) return emptyList()

    // ── 1. Find all parallel pairs ────────────────────────────────────────────
    val parallelPairs = mutableListOf<Pair<Int, Int>>()
    for (i in variants.indices) {
      for (j in i + 1 until variants.size) {
        if (areParallel(variants[i], variants[j], thresholdMeters)) {
          parallelPairs.add(i to j)
        }
      }
    }

    if (parallelPairs.isEmpty()) return emptyList()

    // ── 2. Union-Find to cluster connected components ─────────────────────────
    val parent = IntArray(variants.size) { it }

    fun find(x: Int): Int {
      if (parent[x] != x) parent[x] = find(parent[x])
      return parent[x]
    }

    fun union(x: Int, y: Int) {
      parent[find(x)] = find(y)
    }

    parallelPairs.forEach { (a, b) -> union(a, b) }

    // ── 3. Build a ParallelRouteGroup for every cluster of size ≥ 2 ──────────
    return variants.indices
      .groupBy { find(it) }
      .values
      .filter { it.size >= 2 }
      .map { indices -> buildGroup(indices.map { variants[it] }) }
  }

  // ── private helpers ───────────────────────────────────────────────────────────

  /** Returns true when the average directed Hausdorff distance is ≤ threshold. */
  private fun areParallel(
    a: RouteVariantWithStops,
    b: RouteVariantWithStops,
    threshold: Double,
  ): Boolean {
    if (a.stops.isEmpty() || b.stops.isEmpty()) return false
    return averageHausdorffDistance(a, b) <= threshold
  }

  /**
   * Symmetric average of the two directed Hausdorff distances:
   *
   * d(A→B) = mean over a∈A of min_{b∈B} dist(a,b)
   * d(B→A) = mean over b∈B of min_{a∈A} dist(b,a)
   * result  = (d(A→B) + d(B→A)) / 2
   */
  private fun averageHausdorffDistance(a: RouteVariantWithStops, b: RouteVariantWithStops): Double {
    val aToB = a.stops.map { sa -> b.stops.minOf { sb -> haversine(sa, sb) } }.average()
    val bToA = b.stops.map { sb -> a.stops.minOf { sa -> haversine(sa, sb) } }.average()
    return (aToB + bToA) / 2.0
  }

  private fun buildGroup(groupVariants: List<RouteVariantWithStops>): ParallelRouteGroup {
    val routeIds: Set<RouteId> = groupVariants.map { it.variant.routeId }.toSet()
    val variantIds: Set<String> = groupVariants.map { it.variant.id.value }.toSet()

    // Average pairwise Hausdorff distance within the group
    var totalDist = 0.0
    var pairCount = 0
    for (i in groupVariants.indices) {
      for (j in i + 1 until groupVariants.size) {
        totalDist += averageHausdorffDistance(groupVariants[i], groupVariants[j])
        pairCount++
      }
    }
    val avgDist = if (pairCount > 0) totalDist / pairCount else 0.0

    val mergedStops = mergeStopSequences(groupVariants)

    return ParallelRouteGroup(
      routeIds = routeIds,
      variantIds = variantIds,
      mergedStopSequence = mergedStops,
      averageDistanceMeters = avgDist,
    )
  }

  /**
   * Merges the stop sequences of all variants in the group into a single ordered sequence.
   *
   * Algorithm:
   * 1. Use the variant with the most stops as the backbone.
   * 2. For each additional variant, insert its stops that are not already in the backbone at the
   *    position suggested by their neighbours in the original sequence.
   */
  private fun mergeStopSequences(variants: List<RouteVariantWithStops>): List<String> {
    val base = variants.maxByOrNull { it.stops.size }!!
    val mergedIds = base.variant.stopPattern.split("|").filter { it.isNotEmpty() }.toMutableList()
    val mergedStops = base.stops.toMutableList()

    variants.filter { it != base }.forEach { other ->
      other.stops.forEachIndexed { idx, stop ->
        if (stop.stopId !in mergedIds) {
          val pos = insertionPosition(stop, mergedIds, idx, other.stops)
          mergedIds.add(pos, stop.stopId)
          mergedStops.add(pos, stop)
        }
      }
    }

    return mergedIds
  }

  /**
   * Determines where in [mergedIds] to insert [stop] from another variant.
   *
   * Prefers inserting directly after the predecessor stop (if already present in the merged list),
   * then before the successor stop, and falls back to inserting after the nearest geographic stop.
   */
  private fun insertionPosition(
    stop: StopWithLocation,
    mergedIds: MutableList<String>,
    originalIdx: Int,
    originalStops: List<StopWithLocation>,
  ): Int {
    if (mergedIds.isEmpty()) return 0

    val prevStop = originalStops.getOrNull(originalIdx - 1)
    val nextStop = originalStops.getOrNull(originalIdx + 1)

    val prevIdx = prevStop?.let { mergedIds.indexOf(it.stopId) }?.takeIf { it >= 0 }
    val nextIdx = nextStop?.let { mergedIds.indexOf(it.stopId) }?.takeIf { it >= 0 }

    return when {
      prevIdx != null -> prevIdx + 1
      nextIdx != null -> nextIdx
      else -> {
        // Fall back: insert after the geographically nearest stop
        val nearestIdx =
          mergedIds.indices.minByOrNull { i ->
            // mergedStops parallel to mergedIds; use index lookup via mergedIds
            val s = originalStops.firstOrNull { it.stopId == mergedIds[i] }
            if (s != null) haversine(stop, s) else Double.MAX_VALUE
          } ?: (mergedIds.size - 1)
        nearestIdx + 1
      }
    }
  }

  private fun haversine(a: StopWithLocation, b: StopWithLocation): Double =
    haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)

  private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c * 1000.0
  }
}
