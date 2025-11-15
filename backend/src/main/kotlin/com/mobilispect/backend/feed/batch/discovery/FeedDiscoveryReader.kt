package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.TransitLandOperator
import com.mobilispect.backend.TransitLandOperatorResponse
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemReader
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

/**
 * Custom ItemReader that combines operator reading and metadata fetching.
 *
 * This reader orchestrates a two-phase read process ensuring all operators
 * are read before metadata fetching begins:
 *
 * Phase 1 (Read): Read ALL operators and build complete FeedRegionMap
 * Phase 2 (Fetch): Fetch metadata for ALL discovered feeds
 * Phase 3 (Return): Return batches of FeedDiscoveryInput for processing
 *
 * This ensures the reading phase completes entirely before processing begins,
 * which is critical for efficient API usage and data consistency.
 */
class FeedDiscoveryReader(
    private val webClientBuilder: WebClient.Builder,
    private val transitLandMetadataService: TransitLandMetadataService,
    private val apiKey: TransitLandAPIKey?,
    private val defaultApiKey: TransitLandAPIKey?,
    private val specType: String = "gtfs",
    private val operatorBatchSize: Int = 100
) : ItemReader<FeedDiscoveryInput> {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryReader::class.java)
    private val feedOnestopIdPattern = Regex("^f-.+\$")
    private val operatorProcessingBatchSize = operatorBatchSize.coerceAtLeast(1)

    private val operatorApiKey: TransitLandAPIKey by lazy {
        apiKey ?: defaultApiKey ?: throw IllegalStateException(
            "Transit.land API key not configured. Set app.transit-land.api-key property or pass apiKey job parameter"
        )
    }

    private val operatorClient: WebClient by lazy {
        webClientBuilder.baseUrl("https://transit.land/api/v2/rest")
            .defaultHeader("apikey", operatorApiKey.value)
            .build()
    }

    // Phase tracking
    private var allOperatorsRead = false
    private var completeFeedRegionMap: FeedRegionMap = FeedRegionMap(emptyMap())
    private var completeFeedMetadataMap: FeedMetadataMap? = null

    // For batch-wise output
    private val feedIdBatches = mutableListOf<List<String>>()
    private var currentBatchIndex = 0
    private val outputBatchSize = 100

    override fun read(): FeedDiscoveryInput? {
        // Phase 1: Read ALL operators if not already done
        if (!allOperatorsRead) {
            logger.info("Phase 1: Reading all operators to build complete feed-region map...")
            completeFeedRegionMap = readAllOperators()
            allOperatorsRead = true

            val feedIds = completeFeedRegionMap.feedIds()
            logger.info("Phase 1 complete: Discovered {} feeds across all operators", feedIds.size)

            // Split feed IDs into batches for metadata fetching
            feedIdBatches.addAll(feedIds.chunked(outputBatchSize))
            logger.info("Prepared {} batches for metadata fetching", feedIdBatches.size)
        }

        // Phase 2: Fetch metadata for ALL discovered feeds if not already done
        if (completeFeedMetadataMap == null) {
            logger.info("Phase 2: Fetching metadata for all {} discovered feeds...",
                completeFeedRegionMap.size)
            completeFeedMetadataMap = fetchAllMetadata(completeFeedRegionMap.feedIds())
            logger.info("Phase 2 complete: Fetched metadata for {} feeds",
                completeFeedMetadataMap!!.size)
        }

        // Phase 3: Return batches of combined data for processing
        if (currentBatchIndex >= feedIdBatches.size) {
            logger.info("Phase 3 complete: All batches returned for processing")
            return null
        }

        val batchFeedIds = feedIdBatches[currentBatchIndex]
        currentBatchIndex++

        // Filter the complete maps to just this batch's feed IDs
        val batchRegionMap = completeFeedRegionMap.filterKeys(batchFeedIds.toSet())
        val batchMetadataMap = completeFeedMetadataMap!!.filterKeys(batchFeedIds.toSet())

        logger.info(
            "Phase 3: Returning batch {}/{} with {} feeds for processing",
            currentBatchIndex,
            feedIdBatches.size,
            batchFeedIds.size
        )

        return FeedDiscoveryInput(
            feedRegionMap = batchRegionMap,
            feedMetadataMap = batchMetadataMap
        )
    }

    /**
     * Phase 1: Read all operators and build complete feed-region map.
     */
    private fun readAllOperators(): FeedRegionMap {
        val allMappings = mutableMapOf<String, RegionMetadata>()
        var batchCount = 0
        var currentCursor: Int? = null
        var hasMorePages = true

        while (hasMorePages) {
            val page = fetchOperators(currentCursor)
            val operators = page.operators

            if (operators.isEmpty()) {
                logger.warn("Received empty operator page from Transit.land (cursor={})", currentCursor)
                break
            }

            batchCount++
            logger.info(
                "Processing operator page {} with {} operators (hasMore={})",
                batchCount,
                operators.size,
                page.hasMorePages
            )

            val chunks = operators.chunked(operatorProcessingBatchSize)
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                val feedRegionMap = transformOperatorsToFeedRegionMap(chunk)
                allMappings.putAll(feedRegionMap.feedToRegionMap)
                logger.debug(
                    "Transformed operator chunk {}/{} in page {} -> {} feed mappings (total mappings: {})",
                    chunkIndex + 1,
                    chunks.size,
                    batchCount,
                    feedRegionMap.size,
                    allMappings.size
                )
            }

            currentCursor = page.nextCursor
            hasMorePages = page.hasMorePages

            if (!hasMorePages) {
                logger.info("No additional operator pages available after cursor={}", page.nextCursor)
            }
        }

        logger.info(
            "Completed reading {} operator pages, total feeds discovered: {}",
            batchCount,
            allMappings.size
        )

        return FeedRegionMap(allMappings)
    }

    private fun fetchOperators(afterCursor: Int?): OperatorPage {
        return try {
            var uri = "/operators.json?limit=100"
            if (afterCursor != null) {
                uri += "&after=$afterCursor"
            }

            logger.debug("Fetching operators from Transit.land: uri={}", uri)

            val response = operatorClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(TransitLandOperatorResponse::class.java)
                .block()

            if (response == null) {
                logger.warn("Received null response from Transit.land operators API")
                OperatorPage(emptyList(), null, false)
            } else {
                val nextCursor = response.meta?.after
                val hasMore = nextCursor != null

                logger.info(
                    "Fetched {} operators from Transit.land (hasMore={}, cursor={})",
                    response.operators.size,
                    hasMore,
                    nextCursor
                )

                OperatorPage(response.operators.toList(), nextCursor, hasMore)
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch operators from Transit.land", e)
            OperatorPage(emptyList(), null, false)
        }
    }

    private fun transformOperatorsToFeedRegionMap(operators: List<TransitLandOperator>): FeedRegionMap {
        val feedToRegionMap = mutableMapOf<String, RegionMetadata>()

        for (operator in operators) {
            val allFeedIds = operator.feeds
                ?.filter { it.spec?.equals(specType, ignoreCase = true) == true }
                ?.mapNotNull { it.onestop_id }
                ?: emptyList()

            if (allFeedIds.isNotEmpty()) {
                logger.debug(
                    "Operator {} has {} {} feeds: {}",
                    operator.onestop_id,
                    allFeedIds.size,
                    specType,
                    allFeedIds.take(2).joinToString(", ")
                )
            }

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

            val firstAgency = operator.agencies?.firstOrNull()
            val firstPlace = firstAgency?.places?.firstOrNull()

            val regionParts = if (firstPlace != null) {
                listOfNotNull(
                    firstPlace.city_name,
                    firstPlace.adm1_name,
                    firstPlace.adm0_name
                )
            } else {
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
            val regionIdParts = regionParts.map {
                it.lowercase()
                    .replace(Regex("[^a-z0-9-]"), "-")
                    .replace(Regex("-+"), "-")
                    .trim('-')
            }
            val regionId = "r-${regionIdParts.joinToString("-")}"

            logger.debug(
                "Creating region '{}' for operator {} with {} feeds",
                regionId,
                operator.onestop_id,
                validFeedIds.size
            )

            val regionMetadata = RegionMetadata(
                regionOnestopId = regionId,
                regionName = regionName,
                cityName = firstPlace?.city_name,
                adm1Name = firstPlace?.adm1_name,
                adm0Name = firstPlace?.adm0_name
            )

            for (feedId in validFeedIds) {
                feedToRegionMap[feedId] = regionMetadata
            }
        }

        return FeedRegionMap(feedToRegionMap)
    }

    private fun isValidFeedOnestopId(feedId: String): Boolean {
        return feedOnestopIdPattern.matches(feedId)
    }

    /**
     * Phase 2: Fetch metadata for all discovered feed IDs.
     */
    private fun fetchAllMetadata(feedIds: Collection<String>): FeedMetadataMap {
        if (feedIds.isEmpty()) {
            logger.warn("No feed IDs to fetch metadata for")
            return FeedMetadataMap(emptyMap())
        }

        val allMetadata = mutableMapOf<String, FeedMetadata>()

        // Fetch metadata in batches to avoid overwhelming the API
        val metadataBatchSize = 50
        val feedIdChunks = feedIds.chunked(metadataBatchSize)

        logger.info("Fetching metadata in {} batches of up to {} feeds each",
            feedIdChunks.size, metadataBatchSize)

        for ((index, chunk) in feedIdChunks.withIndex()) {
            try {
                logger.debug("Fetching metadata batch {}/{} ({} feeds)",
                    index + 1, feedIdChunks.size, chunk.size)

                // Fetch metadata for each feed in the chunk
                for (feedId in chunk) {
                    try {
                        val metadata = fetchSingleFeedMetadata(feedId)
                        if (metadata != null) {
                            allMetadata[feedId] = metadata
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to fetch metadata for feed {}: {}", feedId, e.message)
                    }
                }

                logger.debug("Metadata batch {}/{} complete, fetched {} feeds so far",
                    index + 1, feedIdChunks.size, allMetadata.size)

            } catch (e: Exception) {
                logger.error("Error processing metadata batch {}/{}", index + 1, feedIdChunks.size, e)
            }
        }

        logger.info("Metadata fetching complete: {}/{} feeds have metadata",
            allMetadata.size, feedIds.size)

        return FeedMetadataMap(allMetadata)
    }

    /**
     * Fetches metadata for a single feed from Transit.land API.
     */
    private fun fetchSingleFeedMetadata(feedId: String): FeedMetadata? {
        return transitLandMetadataService.fetchFeedMetadata(feedId, apiKey)
    }
}

private data class OperatorPage(
    val operators: List<TransitLandOperator>,
    val nextCursor: Int?,
    val hasMorePages: Boolean
)
