package com.mobilispect.backend.region.service

import com.mobilispect.backend.region.RegionId

/**
 * Domain event published when a bulk import operation completes for all feeds in a region.
 *
 * This event signals the completion of a region-wide feed import operation, regardless of
 * individual feed success or failure. Subscribers can use this to trigger post-import processing,
 * update dashboards, or send notifications.
 *
 * Note: This event fires when the bulk operation completes, not when all individual feed imports
 * finish processing. Individual feed imports may still be running.
 *
 * @property regionId The identifier of the region whose feeds were imported
 */
data class RegionFeedsImportCompletedEvent(val regionId: RegionId)
