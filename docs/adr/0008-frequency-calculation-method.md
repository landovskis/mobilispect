# ADR 0008: Route Frequency Calculation Methodology

**Date**: 2025-11-27
**Status**: Accepted
**Feature**: 003-transit-route-frequency

## Context

Transit frequency is a critical metric for:
- Service planning (how often does my bus come?)
- Operations analysis (is this route overstaffed/understaffed?)
- User experience (can I rely on walk-up service?)
- Equity analysis (do underserved areas get less frequent service?)

However, "frequency" is ambiguous:
- **Average Headway**: Average minutes between consecutive vehicle departures (industry standard)
- **Peak Frequency**: Departures per hour during peak times
- **Service Span**: How many hours per day is service available
- **Vehicle Capacity**: Passengers per departure
- **Actual Arrivals**: Real-time vehicle tracking (not applicable for scheduled data)

The feature must:
1. **Use Scheduled Data**: GTFS provides trip-level schedules, not real-time tracking
2. **Support Time Periods**: Service varies by time of day (peak/off-peak) and day type (weekday/weekend)
3. **Handle Variability**: Service may be irregular (some hours frequent, others sparse)
4. **Match User Expectations**: Must align with how transit planners communicate service levels
5. **Enable Comparisons**: Methodology must be consistent across agencies/routes

## Decision

**Calculate average headway (minutes between consecutive departures) by time period (AM Peak, PM Peak, Off-Peak Weekday, Weekend), derived from scheduled GTFS stop_times.txt.**

### Rationale

1. **Industry Standard**: Average headway is standard metric in transit planning and operations
   - Transit agencies publish service as "every 15 minutes" (average headway)
   - GTFS explicitly designed to support headway calculation
   - Matches how transit planners think about service levels
2. **Time Period Granularity**: Weekday peak/off-peak and weekend aligns with service planning practices
   - Agencies staff and budget differently by period
   - User experience differs dramatically (peak: 15min headway vs off-peak: 45min)
3. **Accurate Representation**: Uses actual scheduled departures from GTFS stop_times.txt
   - Not dependent on real-time vehicle data (which may be unavailable)
   - Reflects scheduled service plan, not actual performance
4. **User Expectation**: Matches how passengers experience service frequency
   - "How often does the bus come?" → average headway is intuitive answer
5. **Schedule Data Available**: All GTFS feeds include stop_times.txt with departure times
   - No external data source required
6. **Statistical Validity**: Average headway captures service variability (min/max headways also calculated)

### Implementation

```kotlin
// Value classes for type safety
@JvmInline
value class HeadwayMinutes(val value: Double) {
    init {
        require(value >= 0) { "Headway must be non-negative" }
    }
}

// Time period definitions
enum class TimePeriod(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val label: String,
    val dayType: ServiceDayType
) {
    WEEKDAY_AM_PEAK(LocalTime.of(6, 0), LocalTime.of(9, 0), "Weekday AM Peak", ServiceDayType.WEEKDAY),
    WEEKDAY_MIDDAY(LocalTime.of(9, 0), LocalTime.of(16, 0), "Weekday Midday", ServiceDayType.WEEKDAY),
    WEEKDAY_PM_PEAK(LocalTime.of(16, 0), LocalTime.of(19, 0), "Weekday PM Peak", ServiceDayType.WEEKDAY),
    WEEKDAY_EVENING(LocalTime.of(19, 0), LocalTime.of(23, 59), "Weekday Evening", ServiceDayType.WEEKDAY),
    WEEKDAY_NIGHT(LocalTime.MIDNIGHT, LocalTime.of(6, 0), "Weekday Night", ServiceDayType.WEEKDAY),
    WEEKEND_DAY(LocalTime.of(6, 0), LocalTime.of(23, 59), "Weekend Day", ServiceDayType.WEEKEND),
    WEEKEND_NIGHT(LocalTime.MIDNIGHT, LocalTime.of(6, 0), "Weekend Night", ServiceDayType.WEEKEND),
    HOLIDAY(LocalTime.MIDNIGHT, LocalTime.of(23, 59), "Holiday", ServiceDayType.HOLIDAY);

    fun contains(time: LocalTime): Boolean =
        if (startTime < endTime) {
            time >= startTime && time < endTime
        } else {
            // Handle overnight periods (e.g., Night: 00:00-06:00)
            time >= startTime || time < endTime
        }

    enum class ServiceDayType {
        WEEKDAY, WEEKEND, HOLIDAY
    }
}

// Frequency data class
data class Frequency(
    val variantId: VariantHash,
    val timePeriod: TimePeriod,
    val date: LocalDate,
    val averageHeadway: HeadwayMinutes,
    val minHeadway: HeadwayMinutes,
    val maxHeadway: HeadwayMinutes,
    val variability: HeadwayMinutes,  // max - min
    val departureCount: Int,
    val tripCount: Int,
    val calculatedAt: Instant = Instant.now(),
    val serviceCalendar: Set<DayOfWeek> = emptySet()
)

// Frequency calculation service
@Service
class FrequencyCalculationService(
    private val variantRepository: RouteVariantRepository,
    private val gtfsReader: GtfsReader
) {

    suspend fun calculateFrequencies(
        variant: RouteVariant,
        referenceDate: LocalDate = LocalDate.now()
    ): List<Frequency> {
        val trips = variantRepository.getTripsForVariant(variant.id)
        val serviceCalendars = gtfsReader.dao.getAllCalendars()

        return TimePeriod.entries
            .associateWith { period ->
                calculateFrequencyForPeriod(
                    variant = variant,
                    trips = trips,
                    timePeriod = period,
                    referenceDate = referenceDate,
                    serviceCalendars = serviceCalendars
                )
            }
            .values
            .filterNotNull()
    }

    private suspend fun calculateFrequencyForPeriod(
        variant: RouteVariant,
        trips: List<Trip>,
        timePeriod: TimePeriod,
        referenceDate: LocalDate,
        serviceCalendars: Collection<ServiceCalendar>
    ): Frequency? {
        // Filter trips by service day type and calendar
        val applicableTrips = trips.filter { trip ->
            isTripsApplicableForPeriod(trip, timePeriod, referenceDate, serviceCalendars)
        }

        if (applicableTrips.isEmpty()) {
            return null  // No service in this period
        }

        // Get stop sequence for this variant
        val firstStop = variant.stopPattern.first()

        // Extract departures at first stop for applicable trips
        val departures = applicableTrips
            .mapNotNull { trip ->
                val stopTime = gtfsReader.dao.getStopTimesForTrip(trip)
                    .find { it.stop.id == firstStop }
                stopTime?.departureTime
            }
            .sorted()

        if (departures.size < 2) {
            return null  // Need at least 2 departures to calculate headway
        }

        // Calculate headways (minutes between consecutive departures)
        val headways = departures
            .zipWithNext { a, b ->
                Duration.between(a, b).toMinutes().toDouble()
            }
            .filter { it > 0 }  // Exclude zero-length headways (duplicates)

        if (headways.isEmpty()) {
            return null  // All departures at same time
        }

        val averageHeadway = HeadwayMinutes(headways.average())
        val minHeadway = HeadwayMinutes(headways.minOrNull() ?: 0.0)
        val maxHeadway = HeadwayMinutes(headways.maxOrNull() ?: 0.0)
        val variability = HeadwayMinutes(maxHeadway.value - minHeadway.value)

        return Frequency(
            variantId = variant.id,
            timePeriod = timePeriod,
            date = referenceDate,
            averageHeadway = averageHeadway,
            minHeadway = minHeadway,
            maxHeadway = maxHeadway,
            variability = variability,
            departureCount = departures.size,
            tripCount = applicableTrips.size,
            serviceCalendar = applicableTrips
                .mapNotNull { trip ->
                    serviceCalendars
                        .find { it.serviceId == trip.serviceId }
                        ?.monday?.let { if (it) DayOfWeek.MONDAY else null }
                }
                .toSet()
        )
    }

    private fun isTripsApplicableForPeriod(
        trip: Trip,
        timePeriod: TimePeriod,
        referenceDate: LocalDate,
        serviceCalendars: Collection<ServiceCalendar>
    ): Boolean {
        // Check if trip's service is active on reference date
        val serviceCalendar = serviceCalendars.find { it.serviceId == trip.serviceId }
            ?: return false

        val dayOfWeek = referenceDate.dayOfWeek
        val isServiceActive = when (dayOfWeek) {
            DayOfWeek.MONDAY -> serviceCalendar.monday
            DayOfWeek.TUESDAY -> serviceCalendar.tuesday
            DayOfWeek.WEDNESDAY -> serviceCalendar.wednesday
            DayOfWeek.THURSDAY -> serviceCalendar.thursday
            DayOfWeek.FRIDAY -> serviceCalendar.friday
            DayOfWeek.SATURDAY -> serviceCalendar.saturday
            DayOfWeek.SUNDAY -> serviceCalendar.sunday
        }

        // Check calendar exceptions (calendar_dates.txt)
        val exceptions = gtfsReader.dao.allCalendarDates
            .filter { it.date == referenceDate && it.serviceId == trip.serviceId }

        return when {
            exceptions.any { it.exceptionType == 1 } -> true  // Service added
            exceptions.any { it.exceptionType == 2 } -> false  // Service removed
            else -> isServiceActive
        }
    }

    // Batch calculation for all variants in agency
    suspend fun calculateAllFrequencies(
        agencyId: String,
        referenceDate: LocalDate = LocalDate.now()
    ): Map<VariantHash, List<Frequency>> {
        val variants = variantRepository.findByAgencyId(agencyId)

        return variants.associate { variant ->
            variant.id to calculateFrequencies(variant, referenceDate)
        }
    }
}

// Example usage in frequency controller
@RestController
@RequestMapping("/api/frequency")
class FrequencyController(
    private val frequencyService: FrequencyCalculationService,
    private val frequencyRepository: FrequencyRepository,
    private val cacheManager: CacheManager
) {

    @GetMapping("/variant/{variantId}/{timePeriod}")
    suspend fun getFrequency(
        @PathVariable variantId: String,
        @PathVariable timePeriod: TimePeriod
    ): ResponseEntity<FrequencyDTO> {
        // Try cache first
        val cached = cacheManager.getCache("frequencies")
            ?.get("$variantId:$timePeriod")

        if (cached != null) {
            return ResponseEntity.ok(cached.get() as FrequencyDTO)
        }

        // Calculate if not cached
        val frequency = frequencyRepository.findByVariantIdAndTimePeriod(variantId, timePeriod)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(FrequencyDTO.from(frequency))
    }
}
```

### Time Period Definitions (from Specification)

| Period | Days | Hours | Use Case |
|--------|------|-------|----------|
| AM Peak | Weekday Mon-Fri | 6:00-9:00 AM | Rush hour inbound |
| Midday | Weekday Mon-Fri | 9:00 AM-4:00 PM | Off-peak daytime |
| PM Peak | Weekday Mon-Fri | 4:00-7:00 PM | Rush hour outbound |
| Evening | Weekday Mon-Fri | 7:00 PM-11:59 PM | Late evening |
| Night | Weekday Mon-Fri | 12:00 AM-6:00 AM | Overnight service |
| Weekend | Saturday-Sunday | 6:00 AM-11:59 PM | Daytime weekend |
| Holiday | Holidays (calendar_dates) | All day | Special schedule |

## Consequences

### Positive

1. **Industry Standard**: Aligns with transit agency practices and publications
2. **User Intuitive**: "Average 15-minute headway" directly answers "how often does it come?"
3. **Consistent Across Agencies**: Methodology applies uniformly (no agency-specific logic)
4. **Captures Variability**: Min/max/variability metrics show service consistency
5. **Scheduled Data Only**: No dependency on real-time vehicle tracking systems
6. **Temporal Granularity**: Time periods capture service planning reality (peak vs off-peak)
7. **Historical Tracking**: Can compare frequency changes over months/years

### Negative

1. **Scheduled vs Actual**: Uses scheduled times, not real-time adherence
   - Mitigation: Real-time analysis is separate feature; scheduled is sufficient for initial release
2. **Time Period Boundaries**: Frequency can spike at period boundaries (e.g., 8:59 vs 9:01)
   - Mitigation: Overlap periods by 15min or use 30min rolling windows in future
3. **Irregular Service**: Routes with no trips in period return null (no frequency)
   - Mitigation: Correct behavior; UI should show "No service" clearly

## Alternatives Considered

### 1. Median Headway (Rejected)

**Rationale**: Less intuitive for users than average
- Median is statistically more robust to outliers, but...
- Transit planners communicate using average (e.g., "Route 5 runs every 12 minutes")
- Users expect average, not median
- Adds complexity without user benefit

### 2. Departures Per Hour (Rejected)

**Rationale**: Less precise for infrequent service
- "4 departures/hour" = "15 minute average headway" (same information)
- But breaks down for infrequent routes (1 departure/2 hours = 0.5 dep/hr)
- Headway more natural for human understanding

### 3. Peak Frequency Only (Rejected)

**Rationale**: Misses service variability
- Some routes frequent during peak, sparse off-peak
- User experience differs dramatically by time of day
- Off-peak analysis essential for equity assessment

### 4. Real-Time Vehicle Positions (Out of Scope)

**Rationale**: Requires real-time GTFS-Realtime feeds
- Scheduled frequency is more stable for trend analysis
- Real-time adds dependencies on vehicle tracking systems
- Scoped for future feature; scheduled data sufficient for v1

### 5. Capacity-Based Frequency (Rejected)

**Rationale**: Requires vehicle capacity data
- GTFS doesn't include vehicle capacity
- Complicates frequency concept (frequency ≠ capacity)
- Better solved separately as service adequacy metric

### 6. Service Span Hours (Rejected)

**Rationale**: Different metric, doesn't measure frequency
- Service span (6am-11pm = 17 hours) is useful metadata
- But doesn't answer "how often does it come?"
- Should be separate metric, not frequency

## Related Decisions

- **ADR 0004**: OneBusAway library provides access to stop_times for frequency calculation
- **ADR 0005**: Frequency calculations grouped by VariantHash (content-based variant ID)
- **ADR 0006**: Transitland provides feeds to analyze for frequency

## Implementation Schedule

- **Phase 1**: Basic average headway calculation by time period
- **Phase 2**: Historical frequency tracking (monthly trends)
- **Phase 3**: Real-time vs scheduled adherence comparison

## Open Questions

1. **Should we calculate frequencies dynamically or pre-compute during import?**
   - Decision: Pre-compute during feed import, cache in Redis for query performance

2. **How to handle routes with extremely variable service (some hours sparse, some frequent)?**
   - Decision: Report variability metric (max-min headway) for transparency

3. **Should overnight periods (midnight-6am) be handled specially?**
   - Decision: Yes, separate overnight period definition; complex routes may have overnight service

## Notes for Implementation Team

- Pre-calculate frequencies during FeedImportTasklet (batch processing)
- Store in PostgreSQL Frequency table with indices on (variant_id, time_period, date)
- Cache results in Redis with TTL matching feed update frequency
- Expose via REST endpoint with filtering by time_period, date range
- Include min/max/variability in API response for transparency
- Monitor calculation performance; optimize if exceeds threshold per agency
