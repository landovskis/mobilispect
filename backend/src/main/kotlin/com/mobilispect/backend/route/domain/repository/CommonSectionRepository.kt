package com.mobilispect.backend.route.domain.repository

import com.mobilispect.backend.route.domain.model.CommonSection
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Repository for [CommonSection] entities in the transit analysis module.
 *
 * Provides data access methods for geographic segments where multiple routes overlap.
 * Common sections are identified by stop patterns and support queries by stop IDs,
 * stop count, and temporal windows. All query methods support pagination for
 * efficient data retrieval in large datasets.
 *
 * A common section represents a corridor segment served by multiple route variants,
 * useful for calculating combined frequency and capacity on shared segments.
 * Constitutional requirement: Must have at least 3 stops to be meaningful.
 */
@Repository
interface CommonSectionRepository : JpaRepository<CommonSection, UUID> {

    /**
     * Find common sections by first stop ID.
     *
     * Useful for finding corridors starting from a specific stop.
     *
     * @param firstStopId The ID of the first stop
     * @param pageable Pagination parameters
     * @return Page of common sections starting with the specified stop
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.firstStopId = :firstStopId ORDER BY cs.stopCount DESC, cs.lastStopId ASC")
    fun findByFirstStopId(
        @Param("firstStopId") firstStopId: String,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find common sections by last stop ID.
     *
     * Useful for finding corridors terminating at a specific stop.
     *
     * @param lastStopId The ID of the last stop
     * @param pageable Pagination parameters
     * @return Page of common sections ending with the specified stop
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.lastStopId = :lastStopId ORDER BY cs.stopCount DESC, cs.firstStopId ASC")
    fun findByLastStopId(
        @Param("lastStopId") lastStopId: String,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find common sections that start or end with specified stop IDs.
     *
     * Useful for finding all corridors connected to a specific stop.
     *
     * @param firstStopId The ID of the first stop
     * @param lastStopId The ID of the last stop
     * @param pageable Pagination parameters
     * @return Page of common sections starting or ending with either stop
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.firstStopId = :firstStopId OR cs.lastStopId = :lastStopId ORDER BY cs.stopCount DESC")
    fun findByFirstStopIdOrLastStopId(
        @Param("firstStopId") firstStopId: String,
        @Param("lastStopId") lastStopId: String,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find common sections by exact first and last stop IDs.
     *
     * Useful for identifying a specific corridor.
     *
     * @param firstStopId The ID of the first stop
     * @param lastStopId The ID of the last stop
     * @param pageable Pagination parameters
     * @return Page of common sections with the specified endpoints
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.firstStopId = :firstStopId AND cs.lastStopId = :lastStopId ORDER BY cs.stopCount DESC")
    fun findByFirstStopIdAndLastStopId(
        @Param("firstStopId") firstStopId: String,
        @Param("lastStopId") lastStopId: String,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find common sections with exactly the specified number of stops.
     *
     * @param stopCount The exact number of stops
     * @param pageable Pagination parameters
     * @return Page of common sections with the specified stop count
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.stopCount = :stopCount ORDER BY cs.firstStopId ASC, cs.lastStopId ASC")
    fun findByStopCount(
        @Param("stopCount") stopCount: Int,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find common sections with at least the specified number of stops.
     *
     * Useful for finding longer corridors that serve many stops.
     * Constitutional requirement: At least 3 stops, but this can query for longer sections.
     *
     * @param minStopCount Minimum number of stops (inclusive)
     * @param pageable Pagination parameters
     * @return Page of common sections with at least the specified number of stops
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.stopCount >= :minStopCount ORDER BY cs.stopCount DESC, cs.firstStopId ASC")
    fun findByStopCountGreaterThanEqual(
        @Param("minStopCount") minStopCount: Int,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find common sections with at most the specified number of stops.
     *
     * Useful for finding shorter corridors.
     *
     * @param maxStopCount Maximum number of stops (inclusive)
     * @param pageable Pagination parameters
     * @return Page of common sections with at most the specified number of stops
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.stopCount <= :maxStopCount ORDER BY cs.stopCount DESC, cs.firstStopId ASC")
    fun findByStopCountLessThanEqual(
        @Param("maxStopCount") maxStopCount: Int,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find common sections within a stop count range.
     *
     * @param minStopCount Minimum number of stops (inclusive)
     * @param maxStopCount Maximum number of stops (inclusive)
     * @param pageable Pagination parameters
     * @return Page of common sections within the specified stop count range
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.stopCount >= :minStopCount AND cs.stopCount <= :maxStopCount ORDER BY cs.stopCount DESC, cs.firstStopId ASC")
    fun findByStopCountBetween(
        @Param("minStopCount") minStopCount: Int,
        @Param("maxStopCount") maxStopCount: Int,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find common sections created within a time window.
     *
     * Useful for tracking newly identified corridors.
     *
     * @param after Start of time window (inclusive)
     * @param before End of time window (inclusive)
     * @param pageable Pagination parameters
     * @return Page of common sections created within the time window
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.createdAt >= :after AND cs.createdAt <= :before ORDER BY cs.createdAt DESC")
    fun findByCreatedAtBetween(
        @Param("after") after: Instant,
        @Param("before") before: Instant,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find common sections updated after a specific timestamp.
     *
     * Useful for incremental processing and change detection.
     *
     * @param since Timestamp to filter by (inclusive)
     * @param pageable Pagination parameters
     * @return Page of common sections updated after the specified time
     */
    @Query("SELECT cs FROM CommonSection cs WHERE cs.updatedAt >= :since ORDER BY cs.updatedAt DESC")
    fun findByUpdatedAtAfter(
        @Param("since") since: Instant,
        pageable: Pageable
    ): Page<CommonSection>

    /**
     * Find all common sections ordered by length (stop count) descending.
     *
     * Useful for identifying major transit corridors.
     *
     * @param pageable Pagination parameters
     * @return Page of common sections ordered by length
     */
    @Query("SELECT cs FROM CommonSection cs ORDER BY cs.stopCount DESC, cs.firstStopId ASC")
    fun findAllOrderByStopCountDesc(pageable: Pageable): Page<CommonSection>

    /**
     * Count common sections starting with a specific stop.
     *
     * @param firstStopId The ID of the first stop
     * @return Number of common sections starting with the specified stop
     */
    @Query("SELECT COUNT(cs) FROM CommonSection cs WHERE cs.firstStopId = :firstStopId")
    fun countByFirstStopId(@Param("firstStopId") firstStopId: String): Long

    /**
     * Count common sections ending with a specific stop.
     *
     * @param lastStopId The ID of the last stop
     * @return Number of common sections ending with the specified stop
     */
    @Query("SELECT COUNT(cs) FROM CommonSection cs WHERE cs.lastStopId = :lastStopId")
    fun countByLastStopId(@Param("lastStopId") lastStopId: String): Long

    /**
     * Count common sections with at least the specified number of stops.
     *
     * @param minStopCount Minimum number of stops
     * @return Number of common sections with at least that many stops
     */
    @Query("SELECT COUNT(cs) FROM CommonSection cs WHERE cs.stopCount >= :minStopCount")
    fun countByStopCountGreaterThanEqual(@Param("minStopCount") minStopCount: Int): Long

    /**
     * Check if a common section exists with the specified stop pattern.
     *
     * @param stopPattern The pipe-separated stop pattern
     * @return true if a common section with this pattern exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END FROM CommonSection cs WHERE cs.stopPattern = :stopPattern")
    fun existsByStopPattern(@Param("stopPattern") stopPattern: String): Boolean

    /**
     * Check if a common section exists with specific first and last stop IDs.
     *
     * @param firstStopId The ID of the first stop
     * @param lastStopId The ID of the last stop
     * @return true if such a common section exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END FROM CommonSection cs WHERE cs.firstStopId = :firstStopId AND cs.lastStopId = :lastStopId")
    fun existsByFirstStopIdAndLastStopId(
        @Param("firstStopId") firstStopId: String,
        @Param("lastStopId") lastStopId: String
    ): Boolean

    /**
     * Get the maximum stop count among all common sections.
     *
     * Useful for understanding the longest corridors in the system.
     *
     * @return Maximum stop count, or 0 if no common sections exist
     */
    @Query("SELECT COALESCE(MAX(cs.stopCount), 0) FROM CommonSection cs")
    fun getMaxStopCount(): Int

    /**
     * Get the average stop count among all common sections.
     *
     * @return Average stop count
     */
    @Query("SELECT AVG(CAST(cs.stopCount AS double)) FROM CommonSection cs")
    fun getAverageStopCount(): Double
}
