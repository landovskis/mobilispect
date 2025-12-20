package com.mobilispect.backend.transitanalysis.domain.repository

import com.mobilispect.backend.transitanalysis.domain.model.Frequency
import com.mobilispect.backend.transitanalysis.domain.model.RouteVariant
import com.mobilispect.backend.transitanalysis.domain.model.TimePeriod
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

/**
 * Repository for [Frequency] entities in the transit analysis module.
 *
 * Provides data access methods for service frequency data, including queries by
 * route variant, service date, time period, and headway metrics. All query methods
 * support pagination for efficient data retrieval in large datasets.
 *
 * Frequency records track how often a route variant operates during different times
 * of day and days of week, measured as headway (time between consecutive vehicles).
 */
@Repository
interface FrequencyRepository : JpaRepository<Frequency, UUID> {

    /**
     * Find all frequency records for a specific route variant.
     *
     * @param variant The route variant to search within
     * @param pageable Pagination parameters
     * @return Page of frequency records for the specified variant
     */
    @Query("SELECT f FROM Frequency f WHERE f.variant = :variant ORDER BY f.serviceDate DESC, f.timePeriod ASC")
    fun findByVariant(
        @Param("variant") variant: RouteVariant,
        pageable: Pageable
    ): Page<Frequency>

    /**
     * Find frequency records for a variant on a specific service date.
     *
     * @param variant The route variant to search within
     * @param serviceDate The service date to filter by
     * @param pageable Pagination parameters
     * @return Page of frequency records for the specified date
     */
    @Query("SELECT f FROM Frequency f WHERE f.variant = :variant AND f.serviceDate = :serviceDate ORDER BY f.timePeriod ASC")
    fun findByVariantAndServiceDate(
        @Param("variant") variant: RouteVariant,
        @Param("serviceDate") serviceDate: LocalDate,
        pageable: Pageable
    ): Page<Frequency>

    /**
     * Find frequency records for a variant, service date, and time period.
     *
     * This is the primary query for retrieving specific headway data for
     * a route variant at a particular time and day.
     *
     * @param variant The route variant to search within
     * @param serviceDate The service date to filter by
     * @param timePeriod The time period classification (peak, off-peak, etc.)
     * @return Frequency record if found, empty Optional otherwise
     */
    @Query("SELECT f FROM Frequency f WHERE f.variant = :variant AND f.serviceDate = :serviceDate AND f.timePeriod = :timePeriod")
    fun findByVariantAndServiceDateAndTimePeriod(
        @Param("variant") variant: RouteVariant,
        @Param("serviceDate") serviceDate: LocalDate,
        @Param("timePeriod") timePeriod: TimePeriod
    ): java.util.Optional<Frequency>

    /**
     * Find frequency records within a date range.
     *
     * Useful for trend analysis and historical comparisons.
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @param pageable Pagination parameters
     * @return Page of frequency records within the specified date range
     */
    @Query("SELECT f FROM Frequency f WHERE f.serviceDate >= :startDate AND f.serviceDate <= :endDate ORDER BY f.serviceDate DESC, f.timePeriod ASC")
    fun findByServiceDateBetween(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        pageable: Pageable
    ): Page<Frequency>

    /**
     * Find frequency records for a variant within a date range.
     *
     * Useful for analyzing service patterns over time for a specific variant.
     *
     * @param variant The route variant to search within
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @param pageable Pagination parameters
     * @return Page of frequency records for the variant within the date range
     */
    @Query("SELECT f FROM Frequency f WHERE f.variant = :variant AND f.serviceDate >= :startDate AND f.serviceDate <= :endDate ORDER BY f.serviceDate DESC, f.timePeriod ASC")
    fun findByVariantAndServiceDateBetween(
        @Param("variant") variant: RouteVariant,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        pageable: Pageable
    ): Page<Frequency>

    /**
     * Find frequency records by time period.
     *
     * @param timePeriod The time period to filter by
     * @param pageable Pagination parameters
     * @return Page of frequency records with the specified time period
     */
    @Query("SELECT f FROM Frequency f WHERE f.timePeriod = :timePeriod ORDER BY f.serviceDate DESC, f.variant.id ASC")
    fun findByTimePeriod(
        @Param("timePeriod") timePeriod: TimePeriod,
        pageable: Pageable
    ): Page<Frequency>

    /**
     * Find records with irregular schedules (no fixed headway pattern).
     *
     * @param variant The route variant to search within
     * @param pageable Pagination parameters
     * @return Page of irregular frequency records for the variant
     */
    @Query("SELECT f FROM Frequency f WHERE f.variant = :variant AND f.isIrregular = true ORDER BY f.serviceDate DESC")
    fun findIrregularByVariant(
        @Param("variant") variant: RouteVariant,
        pageable: Pageable
    ): Page<Frequency>

    /**
     * Find frequency records with minimum headway above a threshold.
     *
     * Useful for identifying frequent service corridors.
     *
     * @param minHeadway Minimum average headway in minutes (exclusive lower bound)
     * @param pageable Pagination parameters
     * @return Page of frequency records below the headway threshold
     */
    @Query("SELECT f FROM Frequency f WHERE f.averageHeadway IS NOT NULL AND f.averageHeadway < :maxHeadway ORDER BY f.averageHeadway ASC")
    fun findByAverageHeadwayLessThan(
        @Param("maxHeadway") maxHeadway: Double,
        pageable: Pageable
    ): Page<Frequency>

    /**
     * Find the most recent frequency records for a variant.
     *
     * @param variant The route variant to search within
     * @param limit Maximum number of records to return
     * @return List of the most recent frequency records
     */
    @Query("SELECT f FROM Frequency f WHERE f.variant = :variant ORDER BY f.serviceDate DESC LIMIT :limit")
    fun findRecentByVariant(
        @Param("variant") variant: RouteVariant,
        @Param("limit") limit: Int
    ): List<Frequency>

    /**
     * Count frequency records for a variant.
     *
     * @param variant The route variant to count within
     * @return Number of frequency records for the variant
     */
    @Query("SELECT COUNT(f) FROM Frequency f WHERE f.variant = :variant")
    fun countByVariant(@Param("variant") variant: RouteVariant): Long

    /**
     * Count frequency records within a date range.
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Number of frequency records within the date range
     */
    @Query("SELECT COUNT(f) FROM Frequency f WHERE f.serviceDate >= :startDate AND f.serviceDate <= :endDate")
    fun countByServiceDateBetween(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Long

    /**
     * Check if frequency data exists for a variant on a specific date and period.
     *
     * @param variant The route variant to search within
     * @param serviceDate The service date
     * @param timePeriod The time period
     * @return true if the record exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Frequency f WHERE f.variant = :variant AND f.serviceDate = :serviceDate AND f.timePeriod = :timePeriod")
    fun existsByVariantAndServiceDateAndTimePeriod(
        @Param("variant") variant: RouteVariant,
        @Param("serviceDate") serviceDate: LocalDate,
        @Param("timePeriod") timePeriod: TimePeriod
    ): Boolean
}
