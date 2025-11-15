package com.mobilispect.backend.feed.batch.discovery

import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemReader

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
    private val operatorFeedReader: OperatorFeedReader,
    private val transitLandMetadataService: TransitLandMetadataService,
    private val apiKey: TransitLandAPIKey?
) : ItemReader<FeedDiscoveryInput> {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryReader::class.java)

    // Phase tracking
    private var allOperatorsRead = false
    private var completeFeedRegionMap: FeedRegionMap? = null
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

            val feedIds = completeFeedRegionMap!!.feedIds()
            logger.info("Phase 1 complete: Discovered {} feeds across all operators", feedIds.size)

            // Split feed IDs into batches for metadata fetching
            feedIdBatches.addAll(feedIds.chunked(outputBatchSize))
            logger.info("Prepared {} batches for metadata fetching", feedIdBatches.size)
        }

        // Phase 2: Fetch metadata for ALL discovered feeds if not already done
        if (completeFeedMetadataMap == null) {
            logger.info("Phase 2: Fetching metadata for all {} discovered feeds...",
                completeFeedRegionMap!!.size)
            completeFeedMetadataMap = fetchAllMetadata(completeFeedRegionMap!!.feedIds())
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
        val batchRegionMap = completeFeedRegionMap!!.filterKeys(batchFeedIds.toSet())
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

        while (true) {
            val batch = operatorFeedReader.read() ?: break
            batchCount++

            allMappings.putAll(batch.feedToRegionMap)

            logger.debug(
                "Read operator batch {}: {} new mappings (total: {})",
                batchCount,
                batch.size,
                allMappings.size
            )
        }

        logger.info("Completed reading {} operator batches, total feeds: {}",
            batchCount, allMappings.size)

        return FeedRegionMap(allMappings)
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