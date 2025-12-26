package com.mobilispect.backend.region.controller

import com.mobilispect.backend.api.dto.FeedDTO
import com.mobilispect.backend.api.dto.FeedSpecType
import com.mobilispect.backend.api.dto.FeedStatus
import com.mobilispect.backend.api.dto.FeedsResponse
import com.mobilispect.backend.api.dto.MetropolitanRegionDTO
import com.mobilispect.backend.api.dto.RegionUpdateRequest
import com.mobilispect.backend.api.dto.RegionsResponse
import com.mobilispect.backend.feed.batch.discovery.FeedDiscoveryBatchService
import com.mobilispect.backend.feed.batch.discovery.FeedDiscoveryJobResult
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.region.domain.MetropolitanRegion
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.feed.repository.FeedAuthenticationRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/feeds/regions")
class RegionController(
    private val regionRepository: MetropolitanRegionRepository,
    private val feedRepository: FeedRepository,
    private val feedAuthenticationRepository: FeedAuthenticationRepository,
    private val feedDiscoveryBatchService: FeedDiscoveryBatchService,
) {
    private val logger = LoggerFactory.getLogger(RegionController::class.java)

    @GetMapping
    @Transactional(readOnly = true)
    fun listRegions(
        @RequestParam(required = false) autoUpdateEnabled: Boolean?,
        @RequestParam(required = false) hasImportedFeeds: Boolean?
    ): RegionsResponse {
        val regions = when {
            // Both filters applied
            autoUpdateEnabled != null && hasImportedFeeds == true ->
                regionRepository.findAllByAutoUpdateEnabledWithCompletedImports(autoUpdateEnabled)

            // Only hasImportedFeeds filter
            hasImportedFeeds == true && autoUpdateEnabled == null ->
                regionRepository.findAllWithCompletedImports()

            // Only autoUpdateEnabled filter (existing behavior)
            autoUpdateEnabled != null && hasImportedFeeds != true ->
                regionRepository.findAllByAutoUpdateEnabled(autoUpdateEnabled)

            // No filters (existing behavior)
            else -> regionRepository.findAll()
        }

        val regionDtos = regions.map { region ->
            val feeds = feedRepository.findAllByRegionRegionOnestopId(region.regionOnestopId)
            region.toDto(feeds)
        }

        return RegionsResponse(
            regions = regionDtos,
            total = regionDtos.size
        )
    }

    @GetMapping("/{regionOnestopId}")
    @Transactional(readOnly = true)
    fun getRegion(@PathVariable regionOnestopId: String): MetropolitanRegionDTO {
        val region = regionRepository.findByRegionOnestopId(RegionId(regionOnestopId))
            .orElseThrow { notFound("Region", regionOnestopId) }
        val feeds = feedRepository.findAllByRegionRegionOnestopId(region.regionOnestopId)
        return region.toDto(feeds)
    }

    @PatchMapping("/{regionOnestopId}")
    @Transactional
    fun updateRegion(
        @PathVariable regionOnestopId: String,
        @RequestBody request: RegionUpdateRequest
    ): MetropolitanRegionDTO {
        val region = regionRepository.findByRegionOnestopId(RegionId(regionOnestopId))
            .orElseThrow { notFound("Region", regionOnestopId) }

        request.autoUpdateEnabled?.let { auto ->
            region.autoUpdateEnabled = auto
        }
        val updated = regionRepository.save(region)
        val feeds = feedRepository.findAllByRegionRegionOnestopId(RegionId(regionOnestopId))
        return updated.toDto(feeds)
    }

    @GetMapping("/{regionOnestopId}/feeds")
    @Transactional(readOnly = true)
    fun listFeedsForRegion(
        @PathVariable regionOnestopId: String,
        @RequestParam(required = false) specType: FeedSpecType?,
        @RequestParam(required = false) status: FeedStatus?
    ): FeedsResponse {
        val region = regionRepository.findByRegionOnestopId(RegionId(regionOnestopId))
            .orElseThrow { notFound("Region", regionOnestopId) }

        var feeds = feedRepository.findAllByRegionRegionOnestopId(region.regionOnestopId)

        // Filter out GTFS-RT feeds (currently disabled)
        feeds = feeds.filter { it.specType != com.mobilispect.backend.feed.model.FeedSpecType.GTFS_RT }

        specType?.let { dto ->
            val spec = dto.toEntity()
            feeds = feeds.filter { it.specType == spec }
        }
        status?.let { dto ->
            val statusEntity = dto.toEntity()
            feeds = feeds.filter { it.status == statusEntity }
        }

        val feedDtos = feeds.map { toFeedDto(region.regionOnestopId.value, it) }

        return FeedsResponse(
            feeds = feedDtos,
            total = feedDtos.size
        )
    }

    @PostMapping("/{regionOnestopId}/discover")
    suspend fun discoverFeeds(
        @PathVariable regionOnestopId: String,
        @RequestParam(required = false, defaultValue = "GTFS") spec: FeedSpecType
    ): FeedDiscoveryJobResult {
        // GTFS-RT feeds are currently disabled
        if (spec == FeedSpecType.GTFS_RT) {
            logger.warn("GTFS-RT feed discovery is currently disabled")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "GTFS-RT feed discovery is currently disabled")
        }

        logger.info("Discovering feeds for region {} using spec {}", regionOnestopId, spec)

        // Get region name for Transit.land API query
        val region = regionRepository.findByRegionOnestopId(RegionId(regionOnestopId))
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found: $regionOnestopId") }

        return feedDiscoveryBatchService.discoverForRegion(regionOnestopId, spec.toEntity())
    }

    private fun toFeedDto(regionOnestopId: String, feed: FeedEntity): FeedDTO {
        val hasAuthentication = feedAuthenticationRepository.findById(feed.feedOnestopId).isPresent
        return FeedDTO(
            feedOnestopId = feed.feedOnestopId,
            regionOnestopId = regionOnestopId,
            name = feed.name,
            specType = feed.specType.toDto(),
            downloadUrl = feed.downloadUrl,
            currentVersionSha1 = feed.currentVersionSha1,
            lastCheckedAt = feed.lastCheckedAt,
            lastUpdatedAt = feed.lastUpdatedAt,
            status = feed.status.toDto(),
            hasAuthentication = hasAuthentication,
            createdAt = feed.createdAt,
            updatedAt = feed.updatedAt
        )
    }

    private fun MetropolitanRegion.toDto(
        feeds: List<FeedEntity>
    ): MetropolitanRegionDTO {
        val feedCount = feeds.size
        val lastCheckedAt = feeds.mapNotNull { it.lastCheckedAt }.maxOrNull()
        return MetropolitanRegionDTO(
            regionOnestopId = regionOnestopId.value,
            name = name,
            adm0Name = adm0Name,
            adm1Name = adm1Name,
            autoUpdateEnabled = autoUpdateEnabled,
            feedCount = feedCount,
            lastCheckAt = lastCheckedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun com.mobilispect.backend.feed.model.FeedSpecType.toDto(): FeedSpecType = when (this) {
        com.mobilispect.backend.feed.model.FeedSpecType.GTFS -> FeedSpecType.GTFS
        com.mobilispect.backend.feed.model.FeedSpecType.GTFS_RT -> FeedSpecType.GTFS_RT
    }

    private fun com.mobilispect.backend.feed.model.FeedStatus.toDto(): FeedStatus = when (this) {
        com.mobilispect.backend.feed.model.FeedStatus.ACTIVE -> FeedStatus.ACTIVE
        com.mobilispect.backend.feed.model.FeedStatus.INACTIVE -> FeedStatus.INACTIVE
        com.mobilispect.backend.feed.model.FeedStatus.ERROR -> FeedStatus.ERROR
    }

    private fun FeedSpecType.toEntity(): com.mobilispect.backend.feed.model.FeedSpecType = when (this) {
        FeedSpecType.GTFS -> com.mobilispect.backend.feed.model.FeedSpecType.GTFS
        FeedSpecType.GTFS_RT -> com.mobilispect.backend.feed.model.FeedSpecType.GTFS_RT
    }

    private fun FeedStatus.toEntity(): com.mobilispect.backend.feed.model.FeedStatus = when (this) {
        FeedStatus.ACTIVE -> com.mobilispect.backend.feed.model.FeedStatus.ACTIVE
        FeedStatus.INACTIVE -> com.mobilispect.backend.feed.model.FeedStatus.INACTIVE
        FeedStatus.ERROR -> com.mobilispect.backend.feed.model.FeedStatus.ERROR
    }

    private fun notFound(entity: String, identifier: String) =
        ResponseStatusException(HttpStatus.NOT_FOUND, "$entity not found: $identifier")
}
