package com.mobilispect.backend.feed.controller

import com.mobilispect.backend.feed.ActiveImportsResponse
import com.mobilispect.backend.feed.service.FeedImportQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for feed import operations.
 *
 * Provides endpoints for querying and managing feed imports.
 */
@RestController
@RequestMapping("/api/feeds")
class FeedImportController(private val feedImportQueryService: FeedImportQueryService) {
  /**
   * Get all active (PENDING or RUNNING) feed imports.
   *
   * Returns feed imports with enriched information including feed names and region data.
   *
   * @return ActiveImportsResponse containing list of active imports and total count
   */
  @GetMapping("/imports/active")
  fun getActiveImports(): ActiveImportsResponse {
    val imports = feedImportQueryService.getActiveImports()
    return ActiveImportsResponse(imports = imports, total = imports.size)
  }
}
