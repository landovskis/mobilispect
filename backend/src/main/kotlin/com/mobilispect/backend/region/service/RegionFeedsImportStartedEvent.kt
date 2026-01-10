package com.mobilispect.backend.region.service

import com.mobilispect.backend.region.RegionId

/**
 * Domain event published when a bulk import operation starts for all feeds in a region.
 *
 * This event signals the beginning of a region-wide feed import operation, allowing interested
 * components to track import progress, update UI states, or trigger related workflows.
 *
 * @property regionId The identifier of the region whose feeds are being imported
 */
data class RegionFeedsImportStartedEvent(val regionId: RegionId)
