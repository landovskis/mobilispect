package com.mobilispect.backend.transitanalysis.domain.service

import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedShapePoint
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedStop
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedTrip
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class StopSpacingCalculationService {
    private val logger = LoggerFactory.getLogger(StopSpacingCalculationService::class.java)

    fun calculateAverageSpacingKm(
        trip: ParsedTrip,
        stopsById: Map<String, ParsedStop>,
        shapesById: Map<String, List<ParsedShapePoint>>
    ): Double? {
        val stopTimes = trip.stopTimes.sortedBy { it.stopSequence }
        if (stopTimes.size < 2) {
            logger.info(
                "Stop spacing unavailable for trip {} (shapeId={}, reason=insufficient-stops)",
                trip.tripId,
                trip.shapeId
            )
            return null
        }

        val shapeDistances = stopTimes.mapNotNull { it.shapeDistTraveledKm }
        if (shapeDistances.size == stopTimes.size) {
            val average = averageFromDistances(shapeDistances)
            logResult(trip, stopTimes.size, "stop_times", average)
            return average
        }

        val shapeId = trip.shapeId
        if (shapeId == null) {
            logResult(trip, stopTimes.size, "unavailable", null)
            return null
        }
        val shapePoints = shapesById[shapeId].orEmpty().sortedBy { it.sequence }
        if (shapePoints.isEmpty()) {
            logResult(trip, stopTimes.size, "unavailable", null)
            return null
        }

        val cumulativeKm = buildCumulativeDistances(shapePoints)
        val stopDistances = mutableListOf<Double>()
        for (stopTime in stopTimes) {
            val stop = stopsById[stopTime.stopId]
            val lat = stop?.latitude
            val lon = stop?.longitude
            if (lat == null || lon == null) {
                logResult(trip, stopTimes.size, "unavailable", null)
                return null
            }
            stopDistances.add(nearestShapeDistanceKm(lat, lon, shapePoints, cumulativeKm))
        }

        val average = averageFromDistances(stopDistances)
        logResult(trip, stopTimes.size, "shapes", average)
        return average
    }

    private fun averageFromDistances(distances: List<Double>): Double? {
        if (distances.size < 2) return null
        val deltas = distances.zipWithNext().mapNotNull { (prev, next) ->
            val delta = next - prev
            delta.takeIf { it > 0 }
        }
        if (deltas.size != distances.size - 1) return null
        return deltas.average()
    }

    private fun buildCumulativeDistances(points: List<ParsedShapePoint>): List<Double> {
        val cumulative = mutableListOf(0.0)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val next = points[i]
            val distance = haversineKm(prev.latitude, prev.longitude, next.latitude, next.longitude)
            cumulative.add(cumulative.last() + distance)
        }
        return cumulative
    }

    private fun nearestShapeDistanceKm(
        lat: Double,
        lon: Double,
        points: List<ParsedShapePoint>,
        cumulative: List<Double>
    ): Double {
        var minDistance = Double.MAX_VALUE
        var bestDistanceAlongShape = cumulative[0]

        // Check each segment between consecutive shape points
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            // Project stop onto this segment
            val projection = projectPointOntoSegment(lat, lon, p1, p2)
            val distanceToSegment = haversineKm(lat, lon, projection.lat, projection.lon)

            if (distanceToSegment < minDistance) {
                minDistance = distanceToSegment
                // Calculate distance along shape to the projection point
                val distanceP1ToProjection = haversineKm(p1.latitude, p1.longitude, projection.lat, projection.lon)
                bestDistanceAlongShape = cumulative[i] + distanceP1ToProjection
            }
        }

        return bestDistanceAlongShape
    }

    /**
     * Projects a point (stop) perpendicular onto a line segment defined by two shape points.
     * Returns the closest point on the segment to the given point.
     */
    private fun projectPointOntoSegment(
        pointLat: Double,
        pointLon: Double,
        segmentStart: ParsedShapePoint,
        segmentEnd: ParsedShapePoint
    ): LatLon {
        // Use local Cartesian approximation for short distances
        // Convert to meters from segment start point
        val midLat = (segmentStart.latitude + segmentEnd.latitude) / 2
        val cosLat = cos(Math.toRadians(midLat))

        // Convert lat/lon to local meters
        val metersPerDegreeLat = 111320.0
        val metersPerDegreeLon = 111320.0 * cosLat

        // Segment start point (origin)
        val ax = 0.0
        val ay = 0.0

        // Segment end point in meters from start
        val bx = (segmentEnd.longitude - segmentStart.longitude) * metersPerDegreeLon
        val by = (segmentEnd.latitude - segmentStart.latitude) * metersPerDegreeLat

        // Point to project in meters from start
        val px = (pointLon - segmentStart.longitude) * metersPerDegreeLon
        val py = (pointLat - segmentStart.latitude) * metersPerDegreeLat

        // Vector from A to B
        val abx = bx - ax
        val aby = by - ay

        // Vector from A to P
        val apx = px - ax
        val apy = py - ay

        // Calculate parameter t: how far along AB is the projection
        val abLengthSquared = abx * abx + aby * aby

        val t = if (abLengthSquared == 0.0) {
            // Degenerate segment (start and end are the same)
            0.0
        } else {
            // Dot product of AP and AB, divided by length squared of AB
            ((apx * abx + apy * aby) / abLengthSquared).coerceIn(0.0, 1.0)
        }

        // Calculate projection point in meters
        val projX = ax + t * abx
        val projY = ay + t * aby

        // Convert back to lat/lon
        val projLon = segmentStart.longitude + projX / metersPerDegreeLon
        val projLat = segmentStart.latitude + projY / metersPerDegreeLat

        return LatLon(projLat, projLon)
    }

    private data class LatLon(val lat: Double, val lon: Double)

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * radiusKm * asin(sqrt(a))
    }

    private fun logResult(trip: ParsedTrip, stopCount: Int, source: String, average: Double?) {
        logger.info(
            "Stop spacing result: tripId={}, shapeId={}, stops={}, source={}, averageKm={}",
            trip.tripId,
            trip.shapeId,
            stopCount,
            source,
            average
        )
    }
}
