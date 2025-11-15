package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.TransitLandOperator
import com.mobilispect.backend.TransitLandOperatorResponse
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

/**
 * Spring Batch ItemReader that reads operators from the Transit.land API
 * and transforms them into a map of feed IDs to region metadata.
 *
 * This reader:
 * 1. Fetches all operators from Transit.land (with pagination support)
 * 2. Extracts feed IDs from each operator
 * 3. Maps each feed to its geographic region (derived from operator location)
 * 4. Returns a FeedRegionMap containing batches of feed-to-region mappings
 *
 * The reader supports pagination through Transit.land's cursor-based API,
 * and only processes GTFS feeds by default. Multiple operators are aggregated
 * into batches to improve processing efficiency.
 *
 * @property apiKey Transit.land API key for authentication
 * @property specType Feed specification type to filter (default: "gtfs")
 * @property batchSize Number of operators to process per batch (default: 100)
 */
@Component
@StepScope
class OperatorFeedReader(
    private val webClient: WebClient.Builder,
    @Value("#{jobParameters['apiKey'] ?: @environment.getProperty('app.transit-land.api-key')}")
    private val apiKey: String?,
    @Value("#{jobParameters['specType'] ?: 'gtfs'}") private val specType: String = "gtfs",
    @Value("#{jobParameters['batchSize'] ?: 100}") private val batchSize: Int = 100
) : ItemReader<FeedRegionMap> {

    private val logger = LoggerFactory.getLogger(OperatorFeedReader::class.java)

    // Minimal validation for Transit.land feed onestop IDs
    // Transit.land feeds have highly variable formats including:
    // - Standard ASCII: f-9q9-bart, f-dr5r-nyct
    // - Multiple tildes: f-dr5r-path~nj~us
    // - Non-ASCII characters: f-xn4n-島田市 (Japanese)
    // - Accented characters: f-u3z-klaipėdoskeleivinistransportas (Lithuanian)
    // We only check that it starts with 'f-' and is not empty
    private val feedOnestopIdPattern = Regex("^f-.+$")

    private val client: WebClient by lazy {
        val key = apiKey ?: throw IllegalStateException(
            "Transit.land API key not configured. Set app.transit-land.api-key property or pass apiKey job parameter"
        )
        webClient.baseUrl("https://transit.land/api/v2/rest")
            .defaultHeader("apikey", key)
            .build()
    }

    private var operatorsList: List<TransitLandOperator> = emptyList()
    private var currentIndex = 0
    private var currentCursor: Int? = null
    private var hasMorePages = true
    private var initialized = false

    override fun read(): FeedRegionMap? {
        // Initialize on first call
        if (!initialized) {
            operatorsList = fetchOperators()
            initialized = true
            logger.info("Initialized OperatorFeedReader with {} operators in first batch", operatorsList.size)
        }

        // Check if we need to fetch more operators
        if (currentIndex >= operatorsList.size) {
            if (hasMorePages) {
                operatorsList = fetchOperators()
                currentIndex = 0
                if (operatorsList.isEmpty()) {
                    return null
                }
                logger.info("Fetched next batch of {} operators", operatorsList.size)
            } else {
                return null
            }
        }

        // Process batch of operators
        val endIndex = minOf(currentIndex + batchSize, operatorsList.size)
        val batchOperators = operatorsList.subList(currentIndex, endIndex)
        currentIndex = endIndex

        logger.debug(
            "Processing operator batch: {}-{} of {}",
            currentIndex - batchOperators.size + 1,
            currentIndex,
            operatorsList.size
        )

        // Transform operators to feed-region map
        val feedRegionMap = transformOperatorsToFeedRegionMap(batchOperators)

        if (feedRegionMap.isEmpty()) {
            logger.warn("No valid feed mappings found in batch of {} operators", batchOperators.size)
            // Continue to next batch instead of returning null
            return if (currentIndex < operatorsList.size || hasMorePages) {
                read() // Recursive call to get next batch
            } else {
                null
            }
        }

        logger.info(
            "Read {} feed-to-region mappings from {} operators",
            feedRegionMap.size,
            batchOperators.size
        )

        return feedRegionMap
    }

    /**
     * Fetches operators from Transit.land API with pagination support.
     */
    private fun fetchOperators(): List<TransitLandOperator> {
        try {
            var uri = "/operators.json?limit=100"
            if (currentCursor != null) {
                uri += "&after=$currentCursor"
            }

            logger.debug("Fetching operators from Transit.land: uri={}", uri)

            val response = client.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(TransitLandOperatorResponse::class.java)
                .block()

            if (response == null) {
                logger.warn("Received null response from Transit.land operators API")
                hasMorePages = false
                return emptyList()
            }

            // Update pagination state
            currentCursor = response.meta?.after
            hasMorePages = response.meta?.after != null

            logger.info(
                "Fetched {} operators from Transit.land (hasMore={}, cursor={})",
                response.operators.size,
                hasMorePages,
                currentCursor
            )

            return response.operators.toList()
        } catch (e: Exception) {
            logger.error("Failed to fetch operators from Transit.land", e)
            hasMorePages = false
            return emptyList()
        }
    }

    /**
     * Transforms a batch of Transit.land operators into a FeedRegionMap.
     *
     * For each operator:
     * 1. Extracts feed IDs filtered by spec type
     * 2. Derives region information from operator's agency location
     * 3. Maps each feed ID to its region metadata
     *
     * Multiple operators may contribute feeds to the same or different regions.
     */
    private fun transformOperatorsToFeedRegionMap(operators: List<TransitLandOperator>): FeedRegionMap {
        val feedToRegionMap = mutableMapOf<String, RegionMetadata>()

        for (operator in operators) {
            // Extract feed IDs from operator, filtering by:
            // 1. Spec type (case-insensitive)
            // 2. Valid onestop ID format
            val allFeedIds = operator.feeds
                ?.filter { it.spec?.equals(specType, ignoreCase = true) == true }
                ?.mapNotNull { it.onestop_id }
                ?: emptyList()

            // Log operators with feeds for debugging
            if (allFeedIds.isNotEmpty()) {
                logger.debug(
                    "Operator {} has {} {} feeds: {}",
                    operator.onestop_id,
                    allFeedIds.size,
                    specType,
                    allFeedIds.take(2).joinToString(", ")
                )
            }

            // Filter to only valid onestop IDs and log any invalid ones
            val validFeedIds = mutableListOf<String>()
            val invalidFeedIds = mutableListOf<String>()

            for (feedId in allFeedIds) {
                if (isValidFeedOnestopId(feedId)) {
                    validFeedIds.add(feedId)
                } else {
                    invalidFeedIds.add(feedId)
                }
            }

            if (invalidFeedIds.isNotEmpty()) {
                logger.warn(
                    "Operator {} has {} invalid feed onestop IDs (skipping): {}",
                    operator.onestop_id ?: "unknown",
                    invalidFeedIds.size,
                    invalidFeedIds.take(5).joinToString(", ") + if (invalidFeedIds.size > 5) "..." else ""
                )
            }

            if (validFeedIds.isEmpty()) {
                continue
            }

            val feedIds = validFeedIds

            // Extract region information from first agency
            // In Transit.land, operators can serve multiple regions, but we'll use
            // the first agency's location as the primary region
            val firstAgency = operator.agencies?.firstOrNull()
            val firstPlace = firstAgency?.places?.firstOrNull()

            // Build region identifier from place information
            // Format: city_name, adm1_name, adm0_name (e.g., "San Francisco, CA, USA")
            val regionParts = if (firstPlace != null) {
                listOfNotNull(
                    firstPlace.city_name,
                    firstPlace.adm1_name,
                    firstPlace.adm0_name
                )
            } else {
                // Fallback: use operator name or onestop_id if no place data available
                logger.warn(
                    "Operator {} has {} feeds but NO place data - using operator info as fallback. Feeds: {}",
                    operator.onestop_id ?: "unknown",
                    validFeedIds.size,
                    validFeedIds.take(3).joinToString(", ")
                )
                listOfNotNull(operator.name ?: operator.onestop_id)
            }

            if (regionParts.isEmpty()) {
                logger.warn(
                    "Operator {} has no valid place components or name - feeds will be SKIPPED: {}",
                    operator.onestop_id ?: "unknown",
                    validFeedIds.joinToString(", ")
                )
                continue
            }

            // Log place data we're using
            if (firstPlace != null) {
                logger.info(
                    "Operator {} location: city='{}', state='{}', country='{}'",
                    operator.onestop_id,
                    firstPlace.city_name ?: "(none)",
                    firstPlace.adm1_name ?: "(none)",
                    firstPlace.adm0_name ?: "(none)"
                )
            } else {
                logger.info(
                    "Operator {} using fallback region name: '{}'",
                    operator.onestop_id,
                    regionParts.joinToString(", ")
                )
            }

            val regionName = regionParts.joinToString(", ")

            // Create a single region ID for all feeds from this operator
            // Use geographic hierarchy: r-<country>-<state/province>-<city>
            // This ensures one region per unique (adm0, adm1, city) triple
            val regionIdParts = regionParts.map {
                it.lowercase()
                    .replace(Regex("[^a-z0-9-]"), "-")
                    .replace(Regex("-+"), "-")
                    .trim('-')
            }

            // Construct geographic region ID
            // Examples: r-canada-quebec-montreal, r-usa-california-san-francisco
            val regionId = "r-${regionIdParts.joinToString("-")}"

            logger.debug(
                "Creating region '{}' for operator {} with {} feeds",
                regionId,
                operator.onestop_id,
                feedIds.size
            )

            val regionMetadata = RegionMetadata(
                regionOnestopId = regionId,
                regionName = regionName,
                cityName = firstPlace?.city_name,
                adm1Name = firstPlace?.adm1_name,
                adm0Name = firstPlace?.adm0_name
            )

            // Map all feeds from this operator to the same region
            for (feedId in feedIds) {
                feedToRegionMap[feedId] = regionMetadata
            }
        }

        return FeedRegionMap(feedToRegionMap)
    }

    /**
     * Validates whether a feed ID has the minimal required format.
     *
     * Minimal validation:
     * - Must start with 'f-'
     * - Must have at least one character after 'f-'
     *
     * Examples of valid IDs:
     * - Standard: f-9q9-bart, f-dr5r-nyct
     * - With tildes: f-dr5r-path~nj~us
     * - Non-ASCII: f-xn4n-島田市
     * - Accented: f-u3z-klaipėdoskeleivinistransportas
     *
     * Examples of invalid IDs: bart, f-, F-9q9-bart (uppercase F)
     *
     * @param feedId The feed onestop ID to validate
     * @return true if the ID is valid, false otherwise
     */
    private fun isValidFeedOnestopId(feedId: String): Boolean {
        return feedOnestopIdPattern.matches(feedId)
    }
}
