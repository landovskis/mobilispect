package com.mobilispect.backend.transitanalysis.domain.service

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import com.mobilispect.backend.transitanalysis.domain.repository.FrequencyRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteVariantRepository
import com.mobilispect.backend.transitanalysis.events.FeedImportCompleted
import com.mobilispect.backend.transitanalysis.events.FrequencyCalculationCompleted
import com.mobilispect.backend.transitanalysis.events.RouteVariantIdentified
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.GtfsParser
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedGtfsData
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Service for orchestrating GTFS feed imports into the transit analysis module.
 *
 * This service coordinates between:
 * - GtfsParser: Parses GTFS feed archive files
 * - VariantIdentificationService: Identifies unique route variants from trip patterns
 * - FrequencyCalculationService: Calculates headways and frequency metrics
 * - Repositories: Persists agencies, routes, variants, and frequencies
 */
interface FeedImportService {

    /**
     * Import a GTFS feed from the specified archive path.
     *
     * @param feedPath Path to the GTFS ZIP archive
     * @param feedEntity Feed metadata entity
     * @return Result containing import metrics on success, or error on failure
     */
    fun importFeed(feedPath: Path, feedEntity: FeedEntity): Result<ImportResult>

    /**
     * Import result containing metrics about the import operation.
     *
     * Used for monitoring, logging, and reporting (FR-024).
     */
    data class ImportResult(
        val agenciesProcessed: Int,
        val routesProcessed: Int,
        val variantsIdentified: Int,
        val durationMillis: Long
    )
}

@Service
class FeedImportServiceImpl(
    private val gtfsParser: GtfsParser,
    private val variantIdentificationService: VariantIdentificationService,
    private val frequencyCalculationService: FrequencyCalculationService,
    private val commonSectionDetectionService: CommonSectionDetectionService,
    private val routeRepository: RouteRepository,
    private val routeVariantRepository: RouteVariantRepository,
    private val frequencyRepository: FrequencyRepository,
    private val eventPublisher: ApplicationEventPublisher
) : FeedImportService {
    private val logger = LoggerFactory.getLogger(FeedImportServiceImpl::class.java)

    override fun importFeed(feedPath: Path, feedEntity: FeedEntity): Result<FeedImportService.ImportResult> =
        runCatching {
            val start = Instant.now()
            val parsed = gtfsParser.parse(feedPath).getOrThrow()

            val routeMap = persistRoutes(feedEntity, parsed)
            val variants = variantIdentificationService.identifyVariants(
                routeMap.values.associateWith { route ->
                    parsed.trips.filter { it.routeId == route.id.value }
                }
            )
            variants.forEach { variant ->
                routeVariantRepository.save(variant)
                eventPublisher.publishEvent(RouteVariantIdentified(variant.id, variant.route.id))
            }

            // Frequency calculation is intentionally conservative: use departure from first stop
            variants.forEach { variant ->
                val times = parsed.trips
                    .filter { it.routeId == variant.route.id.value }
                    .mapNotNull { trip ->
                        trip.stopTimes.firstOrNull()?.departureTime
                    }
                val frequency = frequencyCalculationService.calculateFrequency(
                    variant = variant,
                    serviceDate = Instant.now().atZone(ZoneOffset.UTC).toLocalDate(),
                    timePeriod = com.mobilispect.backend.transitanalysis.domain.model.TimePeriod.WEEKDAY_OFF_PEAK,
                    departureTimes = times
                )
                frequency?.let {
                    frequencyRepository.save(it)
                    eventPublisher.publishEvent(FrequencyCalculationCompleted(it.variant.id, it.serviceDate))
                }
            }

            commonSectionDetectionService.detectCommonSections(variants)

            val duration = Duration.between(start, Instant.now()).toMillis()
            val result = FeedImportService.ImportResult(
                agenciesProcessed = parsed.routes.mapNotNull { it.agencyId }.toSet().size,
                routesProcessed = parsed.routes.size,
                variantsIdentified = variants.size,
                durationMillis = duration
            )

            eventPublisher.publishEvent(
                FeedImportCompleted(
                    feedId = feedEntity.feedOnestopId,
                    routesProcessed = result.routesProcessed,
                    variantsIdentified = result.variantsIdentified,
                    durationMillis = result.durationMillis
                )
            )
            logger.info(
                "Imported GTFS feed {} -> {} routes, {} variants in {} ms",
                feedEntity.feedOnestopId,
                result.routesProcessed,
                result.variantsIdentified,
                result.durationMillis
            )
            result
        }

    private fun persistRoutes(feedEntity: FeedEntity, parsed: ParsedGtfsData): Map<String, Route> {
        val routes = parsed.routes.map { parsedRoute ->
            val routeId = RouteId(parsedRoute.routeId)
            val route = Route(
                id = routeId,
                agency = com.mobilispect.backend.transitanalysis.domain.model.Agency(),
                gtfsRouteId = parsedRoute.routeId,
                shortName = parsedRoute.shortName,
                longName = parsedRoute.longName ?: parsedRoute.shortName ?: parsedRoute.routeId,
                routeType = com.mobilispect.backend.transitanalysis.domain.model.RouteType.BUS,
                active = true
            )
            routeRepository.save(route)
        }
        return routes.associateBy { it.id.value }
    }
}
