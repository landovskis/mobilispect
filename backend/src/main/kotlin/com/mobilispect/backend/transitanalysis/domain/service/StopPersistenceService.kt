package com.mobilispect.backend.transitanalysis.domain.service

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.transitanalysis.data.entity.RouteVariantStopEntity
import com.mobilispect.backend.transitanalysis.data.entity.RouteVariantStopId
import com.mobilispect.backend.transitanalysis.data.repository.RouteVariantStopJpaRepository
import com.mobilispect.backend.transitanalysis.domain.model.RouteVariant
import com.mobilispect.backend.transitanalysis.domain.model.Stop
import com.mobilispect.backend.transitanalysis.domain.repository.StopRepository
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedStop
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Service for persisting GTFS stops and linking them to route variants.
 *
 * Responsibilities:
 * - Convert ParsedStop data from GTFS into Stop domain models
 * - Generate Transitland Onestop IDs for stops
 * - Handle stop deduplication (update existing vs insert new)
 * - Create junction table entries linking variants to stops
 */
interface StopPersistenceService {

    /**
     * Persist stops from GTFS parsed data.
     *
     * For each stop:
     * 1. Generate Onestop ID
     * 2. Check if stop already exists (by Onestop ID)
     * 3. Update existing or create new
     * 4. Track first_seen and last_seen timestamps
     *
     * @param feedEntity Feed entity this stop belongs to
     * @param parsedStops List of stops parsed from GTFS
     * @return Map of GTFS stop_id to Stop domain model for linking to variants
     */
    fun persistStops(feedEntity: FeedEntity, parsedStops: List<ParsedStop>): Map<String, Stop>

    /**
     * Link stops to a route variant via junction table.
     *
     * Creates route_variant_stops entries preserving stop sequence order.
     *
     * @param variant Route variant to link stops to
     * @param gtfsStopIds List of GTFS stop IDs in sequence order
     * @param stopMap Map of GTFS stop_id to Stop (from persistStops)
     */
    fun linkStopsToVariant(variant: RouteVariant, gtfsStopIds: List<String>, stopMap: Map<String, Stop>)
}

/**
 * Default implementation of StopPersistenceService.
 */
@Service
class StopPersistenceServiceImpl(
    private val stopRepository: StopRepository,
    private val routeVariantStopRepository: RouteVariantStopJpaRepository,
    private val onestopIdGenerator: OnestopIdGenerator
) : StopPersistenceService {

    private val logger = LoggerFactory.getLogger(StopPersistenceServiceImpl::class.java)

    @Transactional
    override fun persistStops(feedEntity: FeedEntity, parsedStops: List<ParsedStop>): Map<String, Stop> {
        val now = Instant.now()
        val feedId = FeedId(feedEntity.feedOnestopId)

        val stops = parsedStops.mapNotNull { parsedStop ->
            // Skip stops without required fields
            if (parsedStop.name.isNullOrBlank() || parsedStop.latitude == null || parsedStop.longitude == null) {
                logger.warn("Skipping stop ${parsedStop.stopId} with missing required fields")
                return@mapNotNull null
            }

            // Generate Onestop ID
            val stopOnestopId = onestopIdGenerator.generateStopId(
                feedId = feedId,
                gtfsStopId = parsedStop.stopId,
                name = parsedStop.name,
                lat = parsedStop.latitude,
                lon = parsedStop.longitude
            )

            // Check if stop already exists
            val existingStop = stopRepository.findById(stopOnestopId)

            val stop = if (existingStop != null) {
                // Update existing stop
                existingStop.copy(
                    name = parsedStop.name,
                    latitude = parsedStop.latitude,
                    longitude = parsedStop.longitude,
                    stopCode = parsedStop.stopCode,
                    stopDesc = parsedStop.stopDesc,
                    zoneId = parsedStop.zoneId,
                    stopUrl = parsedStop.stopUrl,
                    locationType = parsedStop.locationType,
                    parentStation = parsedStop.parentStation,
                    lastSeen = now,
                    updatedAt = now
                )
            } else {
                // Create new stop
                Stop(
                    stopOnestopId = stopOnestopId,
                    feedId = feedId,
                    gtfsStopId = parsedStop.stopId,
                    name = parsedStop.name,
                    latitude = parsedStop.latitude,
                    longitude = parsedStop.longitude,
                    stopCode = parsedStop.stopCode,
                    stopDesc = parsedStop.stopDesc,
                    zoneId = parsedStop.zoneId,
                    stopUrl = parsedStop.stopUrl,
                    locationType = parsedStop.locationType,
                    parentStation = parsedStop.parentStation,
                    active = true,
                    firstSeen = now,
                    lastSeen = now,
                    createdAt = now,
                    updatedAt = now
                )
            }

            // Persist stop
            val savedStop = stopRepository.save(stop)
            parsedStop.stopId to savedStop
        }.toMap()

        logger.info("Persisted ${stops.size} stops for feed ${feedEntity.feedOnestopId}")
        return stops
    }

    @Transactional
    override fun linkStopsToVariant(variant: RouteVariant, gtfsStopIds: List<String>, stopMap: Map<String, Stop>) {
        // Delete existing stop links for this variant (in case of re-import)
        routeVariantStopRepository.deleteByVariantId(variant.id.value)

        // Create new junction table entries
        gtfsStopIds.forEachIndexed { sequence, gtfsStopId ->
            val stop = stopMap[gtfsStopId]
            if (stop != null) {
                val stopEntity = com.mobilispect.backend.transitanalysis.data.entity.StopEntity(
                    stopOnestopId = stop.stopOnestopId.value,
                    feed = com.mobilispect.backend.feed.data.entity.FeedEntity(
                        feedOnestopId = stop.feedId.value,
                        name = "",
                        specType = com.mobilispect.backend.feed.model.FeedSpecType.GTFS,
                        downloadUrl = "",
                        status = com.mobilispect.backend.feed.model.FeedStatus.ACTIVE
                    ),
                    gtfsStopId = stop.gtfsStopId,
                    name = stop.name,
                    latitude = stop.latitude,
                    longitude = stop.longitude,
                    firstSeen = stop.firstSeen,
                    lastSeen = stop.lastSeen
                )

                val variantEntity = com.mobilispect.backend.transitanalysis.data.entity.RouteVariantEntity(
                    id = variant.id.value,
                    route = com.mobilispect.backend.transitanalysis.data.entity.RouteEntity(
                        id = variant.routeId.value,
                        agency = com.mobilispect.backend.agency.data.entity.AgencyEntity(
                            agencyOnestopId = "",
                            feed = com.mobilispect.backend.feed.data.entity.FeedEntity(
                                feedOnestopId = "",
                                name = "",
                                specType = com.mobilispect.backend.feed.model.FeedSpecType.GTFS,
                                downloadUrl = "",
                                status = com.mobilispect.backend.feed.model.FeedStatus.ACTIVE
                            ),
                            gtfsAgencyId = "",
                            name = ""
                        ),
                        gtfsRouteId = "",
                        shortName = null,
                        longName = "",
                        routeType = "BUS"
                    ),
                    directionId = variant.directionId,
                    headsign = variant.headsign,
                    stopPattern = variant.stopPattern,
                    stopCount = variant.stopCount,
                    firstStopId = variant.firstStopId,
                    lastStopId = variant.lastStopId,
                    firstSeen = variant.firstSeen,
                    lastSeen = variant.lastSeen
                )

                val junctionEntity = RouteVariantStopEntity(
                    id = RouteVariantStopId(variant.id.value, sequence),
                    variant = variantEntity,
                    stop = stopEntity
                )

                routeVariantStopRepository.save(junctionEntity)
            } else {
                logger.warn("Stop ${gtfsStopId} not found in stop map for variant ${variant.id}")
            }
        }

        logger.debug("Linked ${gtfsStopIds.size} stops to variant ${variant.id}")
    }
}
