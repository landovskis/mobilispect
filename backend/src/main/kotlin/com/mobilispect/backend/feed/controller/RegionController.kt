package com.mobilispect.backend.feed.controller

import com.mobilispect.backend.api.dto.FeedDTO
import com.mobilispect.backend.api.dto.FeedSpecType as FeedSpecTypeDto
import com.mobilispect.backend.api.dto.FeedStatus as FeedStatusDto
import com.mobilispect.backend.api.dto.FeedsResponse
import com.mobilispect.backend.api.dto.MetropolitanRegionDTO
import com.mobilispect.backend.api.dto.RegionUpdateRequest
import com.mobilispect.backend.api.dto.RegionsResponse
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.repository.FeedAuthenticationRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import com.mobilispect.backend.feed.batch.FeedDiscoveryBatchService
import com.mobilispect.backend.feed.service.FeedDiscoveryResult
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
    private val feedRegionMigrationService: com.mobilispect.backend.feed.service.FeedRegionMigrationService
) {
    private val logger = LoggerFactory.getLogger(RegionController::class.java)

    @GetMapping
    @Transactional(readOnly = true)
    fun listRegions(
        @RequestParam(required = false) autoUpdateEnabled: Boolean?
    ): RegionsResponse {
        val regions = when (autoUpdateEnabled) {
            null -> regionRepository.findAll()
            else -> regionRepository.findAllByAutoUpdateEnabled(autoUpdateEnabled)
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
        val region = regionRepository.findById(regionOnestopId)
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
        val region = regionRepository.findById(regionOnestopId)
            .orElseThrow { notFound("Region", regionOnestopId) }

        request.autoUpdateEnabled?.let { auto ->
            region.autoUpdateEnabled = auto
        }
        val updated = regionRepository.save(region)
        val feeds = feedRepository.findAllByRegionRegionOnestopId(regionOnestopId)
        return updated.toDto(feeds)
    }

    @GetMapping("/{regionOnestopId}/feeds")
    @Transactional(readOnly = true)
    fun listFeedsForRegion(
        @PathVariable regionOnestopId: String,
        @RequestParam(required = false) specType: FeedSpecTypeDto?,
        @RequestParam(required = false) status: FeedStatusDto?
    ): FeedsResponse {
        val region = regionRepository.findById(regionOnestopId)
            .orElseThrow { notFound("Region", regionOnestopId) }

        var feeds = feedRepository.findAllByRegionRegionOnestopId(region.regionOnestopId)

        specType?.let { dto ->
            val spec = dto.toEntity()
            feeds = feeds.filter { it.specType == spec }
        }
        status?.let { dto ->
            val statusEntity = dto.toEntity()
            feeds = feeds.filter { it.status == statusEntity }
        }

        val feedDtos = feeds.map { toFeedDto(region.regionOnestopId, it) }

        return FeedsResponse(
            feeds = feedDtos,
            total = feedDtos.size
        )
    }

    @PostMapping("/{regionOnestopId}/discover")
    suspend fun discoverFeeds(
        @PathVariable regionOnestopId: String,
        @RequestParam(required = false, defaultValue = "GTFS") spec: FeedSpecTypeDto
    ): FeedDiscoveryResult {
        logger.info("Discovering feeds for region {} using spec {}", regionOnestopId, spec)

        // Get region name for Transit.land API query
        val region = regionRepository.findById(regionOnestopId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found: $regionOnestopId") }

        return feedDiscoveryBatchService.discover(regionOnestopId, region.name, spec.toEntity())
    }

    @PostMapping("/migrate-orphaned")
    suspend fun migrateOrphanedFeeds(): com.mobilispect.backend.feed.service.FeedRegionMigrationService.MigrationResult {
        logger.info("Starting migration of orphaned feeds...")
        return feedRegionMigrationService.migrateOrphanedFeeds()
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

    private fun com.mobilispect.backend.feed.model.MetropolitanRegion.toDto(
        feeds: List<FeedEntity>
    ): MetropolitanRegionDTO {
        val feedCount = feeds.size
        val lastCheckedAt = feeds.mapNotNull { it.lastCheckedAt }.maxOrNull()
        return MetropolitanRegionDTO(
            regionOnestopId = regionOnestopId,
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

    private fun FeedSpecType.toDto(): FeedSpecTypeDto = when (this) {
        FeedSpecType.GTFS -> FeedSpecTypeDto.GTFS
        FeedSpecType.GTFS_RT -> FeedSpecTypeDto.GTFS_RT
    }

    private fun FeedStatus.toDto(): FeedStatusDto = when (this) {
        FeedStatus.ACTIVE -> FeedStatusDto.ACTIVE
        FeedStatus.INACTIVE -> FeedStatusDto.INACTIVE
        FeedStatus.ERROR -> FeedStatusDto.ERROR
    }

    private fun FeedSpecTypeDto.toEntity(): FeedSpecType = when (this) {
        FeedSpecTypeDto.GTFS -> FeedSpecType.GTFS
        FeedSpecTypeDto.GTFS_RT -> FeedSpecType.GTFS_RT
    }

    private fun FeedStatusDto.toEntity(): FeedStatus = when (this) {
        FeedStatusDto.ACTIVE -> FeedStatus.ACTIVE
        FeedStatusDto.INACTIVE -> FeedStatus.INACTIVE
        FeedStatusDto.ERROR -> FeedStatus.ERROR
    }

    private fun notFound(entity: String, identifier: String) =
        ResponseStatusException(HttpStatus.NOT_FOUND, "$entity not found: $identifier")
}
