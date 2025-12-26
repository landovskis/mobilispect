package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.TransitLandOperator
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.infastructure.transit_land.FeedMetadataResult
import com.mobilispect.backend.infastructure.transit_land.OperatorsResult
import com.mobilispect.backend.infastructure.transit_land.TransitLandAPI
import com.mobilispect.backend.transit_land.PagingParameters
import org.slf4j.LoggerFactory
import org.springframework.batch.infrastructure.item.ItemReader
import java.util.concurrent.CompletableFuture

/**
 * Type-safe state machine for FeedDiscoveryReader.
 *
 * Uses sealed classes to encode the reader's phase in the type system,
 * ensuring compile-time safety for state transitions and data availability.
 *
 * State transitions:
 * - Initial → OperatorsRead: After reading all operators from Transit.land
 * - OperatorsRead → MetadataFetched: After fetching metadata for all feeds
 * - MetadataFetched → Yielding: While returning batches to Spring Batch
 * - Yielding → Complete: After all batches have been returned
 */
sealed class ReaderState {
    /**
     * Initial state before any processing has begun.
     */
    data object Initial : ReaderState()

    /**
     * State after all operators have been read.
     * Contains the complete feed-region mapping and prepared batches.
     */
    data class OperatorsRead(
        val feedRegionMap: FeedRegionMap,
        val batches: List<List<FeedId>>
    ) : ReaderState()

    /**
     * State after metadata has been fetched for all feeds.
     * Ready to begin yielding batches to Spring Batch.
     */
    data class MetadataFetched(
        val feedRegionMap: FeedRegionMap,
        val feedMetadataMap: FeedMetadataMap,
        val batches: List<List<FeedId>>
    ) : ReaderState()

    /**
     * State while yielding batches to Spring Batch.
     * Tracks current position in the batch list.
     */
    data class Yielding(
        val feedRegionMap: FeedRegionMap,
        val feedMetadataMap: FeedMetadataMap,
        val batches: List<List<FeedId>>,
        val nextBatchIndex: Int
    ) : ReaderState()

    /**
     * Terminal state after all batches have been yielded.
     */
    data object Complete : ReaderState()
}

/**
 * Custom ItemReader that combines operator reading and metadata fetching.
 *
 * This reader orchestrates a multi-phase read process ensuring all operators
 * are read before metadata fetching begins:
 *
 * Phase 1 (Read): Read ALL operators and build complete FeedRegionMap
 * Phase 2 (Fetch): Fetch metadata for ALL discovered feeds
 * Phase 3 (Return): Return batches of FeedDiscoveryInput for processing
 *
 * This ensures the reading phase completes entirely before processing begins,
 * which is critical for efficient API usage and data consistency.
 *
 * Rate limiting and concurrency control are centralized in TransitLandClient.
 *
 * Uses a type-safe state machine (sealed classes) to track progress through
 * phases, ensuring compile-time safety for state transitions.
 */
class FeedDiscoveryReader(
    private val transitLandClient: TransitLandAPI,
    private val apiKey: TransitLandAPIKey?,
    private val defaultApiKey: TransitLandAPIKey?,
    private val specType: String = "gtfs",
    operatorBatchSize: Int = 100
) : ItemReader<FeedDiscoveryInput> {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryReader::class.java)
    private val feedOnestopIdPattern = Regex("^f-.+$")
    private val operatorProcessingBatchSize = operatorBatchSize.coerceAtLeast(1)
    private val outputBatchSize = 100

    private val operatorApiKey: String by lazy {
        (apiKey ?: defaultApiKey)?.value ?: throw IllegalStateException(
            "Transit.land API key not configured. Set app.transit-land.api-key property or pass apiKey job parameter"
        )
    }

    // Type-safe state machine - current state encodes what data is available
    private var state: ReaderState = ReaderState.Initial

    override fun read(): FeedDiscoveryInput? {
        return when (val currentState = state) {
            is ReaderState.Initial -> {
                // Phase 1: Read ALL operators
                logger.info("═══ Phase 1: Operator Discovery ═══")
                val feedRegionMap = readAllOperators()
                val feedIds = feedRegionMap.feedIds()
                val batches = feedIds.chunked(outputBatchSize)
                logger.info("  → Prepared {} batches for metadata fetching\n", batches.size)

                // Transition to OperatorsRead state
                state = ReaderState.OperatorsRead(feedRegionMap, batches)
                read() // Recurse to process next state
            }

            is ReaderState.OperatorsRead -> {
                // Phase 2: Fetch metadata for ALL discovered feeds
                logger.info("═══ Phase 2: Metadata Fetch ═══")
                val feedMetadataMap = fetchAllMetadata(currentState.feedRegionMap)
                logger.info("")

                // Transition to MetadataFetched state
                state = ReaderState.MetadataFetched(
                    feedRegionMap = currentState.feedRegionMap,
                    feedMetadataMap = feedMetadataMap,
                    batches = currentState.batches
                )
                read() // Recurse to process next state
            }

            is ReaderState.MetadataFetched -> {
                // Transition to Yielding state (starting at batch 0)
                if (currentState.batches.isEmpty()) {
                    logger.info("═══ Phase 3: Processing Complete ═══")
                    logger.info("  → No batches to process\n")
                    state = ReaderState.Complete
                    null
                } else {
                    logger.info("═══ Phase 3: Batch Processing ═══")
                    state = ReaderState.Yielding(
                        feedRegionMap = currentState.feedRegionMap,
                        feedMetadataMap = currentState.feedMetadataMap,
                        batches = currentState.batches,
                        nextBatchIndex = 0
                    )
                    read() // Recurse to yield first batch
                }
            }

            is ReaderState.Yielding -> {
                // Phase 3: Return batches of combined data for processing
                if (currentState.nextBatchIndex >= currentState.batches.size) {
                    logger.info("═══ Phase 3: Processing Complete ═══")
                    logger.info("  → All {} batches sent for processing\n", currentState.batches.size)
                    state = ReaderState.Complete
                    null
                } else {
                    val batchFeedIds = currentState.batches[currentState.nextBatchIndex]
                    val batchIndex = currentState.nextBatchIndex + 1

                    // Advance to next batch for next call
                    state = currentState.copy(nextBatchIndex = currentState.nextBatchIndex + 1)

                    // Filter the complete maps to just this batch's feed IDs
                    val batchRegionMap = currentState.feedRegionMap.filterKeys(batchFeedIds.toSet())
                    val batchMetadataMap = currentState.feedMetadataMap.filterKeys(batchFeedIds.toSet())

                    logger.debug(
                        "  → Sending batch {}/{} ({} feeds) for processing",
                        batchIndex,
                        currentState.batches.size,
                        batchFeedIds.size
                    )

                    FeedDiscoveryInput(
                        feedRegionMap = batchRegionMap,
                        feedMetadataMap = batchMetadataMap
                    )
                }
            }

            is ReaderState.Complete -> {
                // Terminal state - no more items to read
                null
            }
        }
    }

    /**
     * Phase 1: Read all operators and build complete feed-region map.
     */
    private fun readAllOperators(): FeedRegionMap {
        val allMappings = mutableMapOf<FeedId, RegionMetadata>()
        var batchCount = 0
        var currentCursor: Int? = null

        var currentFuture: CompletableFuture<OperatorPage>? = fetchOperatorsAsync(null)

        while (currentFuture != null) {
            val page = currentFuture.join()
            val operators = page.operators

            if (operators.isEmpty()) {
                logger.warn("Received empty operator page from Transit.land (cursor={})", currentCursor)
                if (page.hasMorePages) {
                    currentCursor = page.nextCursor
                    currentFuture = fetchOperatorsAsync(currentCursor)
                    continue
                } else {
                    break
                }
            }

            val nextFuture = if (page.hasMorePages) {
                fetchOperatorsAsync(page.nextCursor)
            } else {
                null
            }

            batchCount++
            logger.info("  Page {}: Processing {} operators", batchCount, operators.size)

            val chunks = operators.chunked(operatorProcessingBatchSize)
            val pageFeedsBeforeCount = allMappings.size
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                val feedRegionMap = transformOperatorsToFeedRegionMap(chunk)
                allMappings.putAll(feedRegionMap.feedToRegionMap)
                logger.debug(
                    "    → Chunk {}/{}: {} feed mappings (page total: {})",
                    chunkIndex + 1,
                    chunks.size,
                    feedRegionMap.size,
                    allMappings.size - pageFeedsBeforeCount
                )
            }

            val pageFeedsAdded = allMappings.size - pageFeedsBeforeCount
            if (pageFeedsAdded > 0) {
                logger.info("    ✓ Page {}: Discovered {} feeds (total: {})", batchCount, pageFeedsAdded, allMappings.size)
            }

            currentCursor = page.nextCursor
            currentFuture = nextFuture

            if (currentFuture == null) {
                logger.info("  → No more pages (final cursor: {})", currentCursor ?: "none")
            }
        }

        logger.info("✓ Phase 1 complete: Discovered {} feeds across {} operator pages", allMappings.size, batchCount)

        return FeedRegionMap(allMappings)
    }

    private fun fetchOperators(afterCursor: Int?): OperatorPage {
        return try {
            logger.debug("Fetching operators from Transit.land: cursor={}", afterCursor)

            val paging = PagingParameters(limit = 100, after = afterCursor)
            val result = transitLandClient.operators(operatorApiKey, paging)

            result.fold(
                onSuccess = { operatorsResult: OperatorsResult ->
                    val hasMore = operatorsResult.after != null
                    logger.info(
                        "Fetched {} operators from Transit.land (hasMore={}, cursor={})",
                        operatorsResult.operators.size,
                        hasMore,
                        operatorsResult.after
                    )
                    OperatorPage(operatorsResult.operators.toList(), operatorsResult.after, hasMore)
                },
                onFailure = { error ->
                    logger.error("Failed to fetch operators from Transit.land: {}", error.message)
                    OperatorPage(emptyList(), null, false)
                }
            )
        } catch (e: Exception) {
            logger.error("Failed to fetch operators from Transit.land", e)
            OperatorPage(emptyList(), null, false)
        }
    }

    private fun fetchOperatorsAsync(afterCursor: Int?): CompletableFuture<OperatorPage> {
        return CompletableFuture.supplyAsync { fetchOperators(afterCursor) }
    }

    private fun transformOperatorsToFeedRegionMap(operators: List<TransitLandOperator>): FeedRegionMap {
        val feedToRegionMap = mutableMapOf<FeedId, RegionMetadata>()

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

            val validFeedIds = mutableListOf<FeedId>()
            val invalidFeedIds = mutableListOf<String>()

            for (feedId in allFeedIds) {
                if (isValidFeedOnestopId(feedId)) {
                    validFeedIds.add(FeedId(feedId))
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
            val place = firstAgency?.places?.firstOrNull { it.city_name != null } ?: firstAgency?.places?.firstOrNull()

            val regionParts = if (place != null) {
                listOfNotNull(
                    place.city_name,
                    place.adm1_name,
                    place.adm0_name
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

            val regionName = regionParts.joinToString(", ")

            // Log Montreal-area operators prominently for debugging
            val isMontreal = regionParts.any { it.contains("Montréal", ignoreCase = true) || it.contains("Montreal", ignoreCase = true) }
            if (isMontreal) {
                logger.info(
                    "      🍁 MONTREAL: {} → {} ({} feeds)",
                    operator.short_name ?: operator.name ?: "Unknown",
                    regionName,
                    validFeedIds.size
                )
            } else {
                logger.debug(
                    "      → {}: {} ({} feeds)",
                    regionName,
                    operator.short_name ?: operator.name ?: "Unknown",
                    validFeedIds.size
                )
            }
            val sanitizedParts = regionParts.map {
                it.lowercase()
                    .replace(Regex("[^a-z0-9-]"), "-")
                    .replace(Regex("-+"), "-")
                    .trim('-')
            }.filter { it.isNotBlank() }

            if (sanitizedParts.isEmpty()) {
                logger.warn(
                    "Operator {} produced no sanitized region parts after filtering - skipping",
                    operator.onestop_id ?: "unknown"
                )
                continue
            }

            val regionIdParts = if (sanitizedParts.size >= 2) {
                sanitizedParts
            } else {
                sanitizedParts + listOf("global")
            }

            val regionId = "r-${regionIdParts.joinToString("-")}"

            logger.debug(
                "Creating region '{}' for operator {} with {} feeds",
                regionId,
                operator.onestop_id,
                validFeedIds.size
            )

            // Use operator short_name if available, otherwise full name
            val operatorDisplayName = operator.short_name ?: operator.name

            val regionMetadata = RegionMetadata(
                regionOnestopId = regionId,
                regionName = regionName,
                cityName = place?.city_name,
                adm1Name = place?.adm1_name,
                adm0Name = place?.adm0_name,
                operatorName = operatorDisplayName
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
     * Uses the centralized TransitLandClient which handles rate limiting and concurrency.
     */
    private fun fetchAllMetadata(feedRegionMap: FeedRegionMap): FeedMetadataMap {
        val feedIds = feedRegionMap.feedIds()

        if (feedIds.isEmpty()) {
            logger.warn("No feed IDs to fetch metadata for")
            return FeedMetadataMap(emptyMap())
        }

        val allMetadata = mutableMapOf<FeedId, FeedMetadata>()

        // Fetch metadata in batches to avoid overwhelming the API
        val metadataBatchSize = 50
        val feedIdChunks = feedIds.chunked(metadataBatchSize)

        logger.info("Phase 2: Fetching metadata for {} feeds in {} batches", feedIds.size, feedIdChunks.size)

        for ((index, chunk) in feedIdChunks.withIndex()) {
            try {
                val batchStartCount = allMetadata.size

                val futures = chunk.map { feedId ->
                    CompletableFuture.supplyAsync {
                        var regionMetadata: RegionMetadata? = null
                        try {
                            regionMetadata = feedRegionMap[feedId]
                            val metadata = fetchSingleFeedMetadata(feedId, regionMetadata?.operatorName)
                            feedId to metadata
                        } catch (e: Exception) {
                            // Highlight Montreal feed failures
                            val isMontreal = feedId.value.startsWith("f-f25") ||
                                            regionMetadata?.regionName?.contains("Montreal", ignoreCase = true) == true ||
                                            regionMetadata?.regionName?.contains("Montréal", ignoreCase = true) == true ||
                                            regionMetadata?.operatorName?.contains("STM") == true ||
                                            regionMetadata?.operatorName?.contains("STL") == true ||
                                            regionMetadata?.operatorName?.contains("RTL") == true ||
                                            regionMetadata?.operatorName?.contains("EXO") == true

                            if (isMontreal) {
                                logger.warn("    ✗ 🍁 FAILED Montreal feed: {} ({}) - {}",
                                    feedId.value,
                                    regionMetadata?.operatorName ?: "unknown",
                                    e.message)
                            } else {
                                logger.debug("    ✗ Failed: {} - {}", feedId.value, e.message)
                            }
                            feedId to null
                        }
                    }
                }

                futures.forEach { future ->
                    val (feedId, metadata) = future.join()
                    if (metadata != null) {
                        allMetadata[feedId] = metadata
                    }
                }

                val batchSuccessCount = allMetadata.size - batchStartCount
                val batchFailCount = chunk.size - batchSuccessCount
                if (batchFailCount > 0) {
                    logger.info("    ✓ Batch {}/{}: {} succeeded, {} failed (total: {})",
                        index + 1, feedIdChunks.size, batchSuccessCount, batchFailCount, allMetadata.size)
                } else {
                    logger.info("    ✓ Batch {}/{}: {} succeeded (total: {})",
                        index + 1, feedIdChunks.size, batchSuccessCount, allMetadata.size)
                }

            } catch (e: Exception) {
                logger.error("  ✗ Batch {}/{} error: {}", index + 1, feedIdChunks.size, e.message)
            }
        }

        val failedCount = feedIds.size - allMetadata.size
        if (failedCount > 0) {
            logger.info("✓ Phase 2 complete: Fetched metadata for {}/{} feeds ({} failed)",
                allMetadata.size, feedIds.size, failedCount)
        } else {
            logger.info("✓ Phase 2 complete: Fetched metadata for all {} feeds", allMetadata.size)
        }

        return FeedMetadataMap(allMetadata)
    }

    /**
     * Fetches metadata for a single feed using the centralized TransitLandClient.
     * Rate limiting and concurrency control are handled by the client.
     */
    private fun fetchSingleFeedMetadata(feedId: FeedId, operatorName: String?): FeedMetadata? {
        if (!isValidFeedOnestopId(feedId.value)) {
            logger.warn("Skipping metadata fetch for invalid feed onestop ID: {}", feedId.value)
            return null
        }

        logger.debug("Fetching feed metadata from Transit.land: feedId={}", feedId.value)

        val result = transitLandClient.feedMetadata(operatorApiKey, feedId.value)

        return result.fold(
            onSuccess = { apiResult: FeedMetadataResult ->
                // Use Transit.land feed name if available, otherwise fallback to operator name
                val feedName = apiResult.name ?: operatorName ?: "Unknown"
                if (apiResult.name == null && operatorName != null) {
                    logger.debug(
                        "Feed {} has no name from Transit.land API, using operator name: '{}'",
                        feedId.value,
                        operatorName
                    )
                }

                val metadata = FeedMetadata(
                    feedOnestopId = FeedId.from(apiResult.feedOnestopId) ?: feedId,
                    name = feedName,
                    downloadUrl = apiResult.downloadUrl,
                    specType = apiResult.spec,
                    versionSha1 = apiResult.versionSha1,
                    earliestCalendarDate = apiResult.earliestCalendarDate,
                    latestCalendarDate = apiResult.latestCalendarDate,
                    staticFeedUrl = apiResult.staticFeedUrl,
                    realtimeFeedUrl = apiResult.realtimeFeedUrl,
                    authorizationType = apiResult.authorizationType,
                    authorizationInfoUrl = apiResult.authorizationInfoUrl
                )

                // Highlight Montreal feeds
                val isMontreal = feedId.value.startsWith("f-f25") ||
                                feedName.contains("Montreal", ignoreCase = true) ||
                                feedName.contains("Montréal", ignoreCase = true) ||
                                operatorName?.contains("Montreal", ignoreCase = true) == true ||
                                operatorName?.contains("Montréal", ignoreCase = true) == true ||
                                operatorName?.contains("STM") == true ||
                                operatorName?.contains("STL") == true ||
                                operatorName?.contains("RTL") == true ||
                                operatorName?.contains("EXO") == true

                if (isMontreal) {
                    logger.info("        🍁 Fetched: {} ({})", feedName, operatorName ?: "unknown")
                }

                metadata
            },
            onFailure = { error ->
                logger.error("Failed to fetch metadata for feed: {} - {}", feedId.value, error.message)
                null
            }
        )
    }
}

private data class OperatorPage(
    val operators: List<TransitLandOperator>,
    val nextCursor: Int?,
    val hasMorePages: Boolean
)
