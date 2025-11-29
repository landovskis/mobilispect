import { TimePeriod } from './time-period.model';
import { RouteType } from './route-type.model';

/**
 * Metropolitan region representing a geographic area served by transit agencies.
 *
 * Reference: Backend MetropolitanRegion entity at com.mobilispect.backend.feed.model.MetropolitanRegion
 */
export interface Region {
  /** Unique region identifier using Onestop ID format */
  readonly regionOnestopId: string;

  /** Display name of the metropolitan region */
  readonly name: string;

  /** Country name (ADM0 administrative boundary) */
  readonly adm0Name?: string;

  /** State/Province name (ADM1 administrative boundary) */
  readonly adm1Name?: string;

  /** Whether automatic feed updates are enabled for this region */
  readonly autoUpdateEnabled: boolean;

  /** ISO 8601 timestamp of record creation */
  readonly createdAt: string;

  /** ISO 8601 timestamp of last record update */
  readonly updatedAt: string;
}

/**
 * Transit operator providing public transportation service.
 *
 * An agency belongs to a feed and inherits region membership through the feed's
 * relationship with metropolitan regions. Agencies operate one or more routes.
 *
 * Reference: Backend Agency entity at com.mobilispect.backend.transitanalysis.domain.model.Agency
 */
export interface Agency {
  /** Unique agency identifier using Onestop ID format (o-geohash-name) */
  readonly id: string;

  /** Agency ID from GTFS agency.txt file */
  readonly gtfsAgencyId: string;

  /** Feed Onestop ID this agency belongs to */
  readonly feedOnestopId: string;

  /** Agency display name */
  readonly name: string;

  /** Agency website URL */
  readonly website?: string;

  /** Agency contact phone number */
  readonly phone?: string;

  /** ISO 8601 timestamp of last successful feed import */
  readonly lastFeedImport?: string;

  /** Whether this agency is currently active */
  readonly active: boolean;

  /** ISO 8601 timestamp of record creation */
  readonly createdAt: string;

  /** ISO 8601 timestamp of last record update */
  readonly updatedAt: string;
}

/**
 * Named transit line operated by an agency.
 *
 * A route is identified by a short name (e.g., "5") and long name (e.g., "Downtown Express").
 * Routes are categorized by GTFS route type (bus, rail, subway, etc.) and may have
 * multiple variants representing different service patterns.
 *
 * Reference: Backend Route entity at com.mobilispect.backend.transitanalysis.domain.model.Route
 */
export interface Route {
  /** Unique route identifier */
  readonly id: string;

  /** Agency ID that operates this route */
  readonly agencyId: string;

  /** Route ID from GTFS routes.txt file */
  readonly gtfsRouteId: string;

  /** Short route name (e.g., "5", "Red Line") */
  readonly shortName?: string;

  /** Long route name (e.g., "Downtown Express") */
  readonly longName: string;

  /** GTFS route type (bus, rail, subway, etc.) */
  readonly routeType: RouteType;

  /** Route color in hex format without # (e.g., "FF0000") */
  readonly color?: string;

  /** Text color for route display in hex format without # */
  readonly textColor?: string;

  /** Whether this route is currently active */
  readonly active: boolean;

  /** ISO 8601 timestamp of record creation */
  readonly createdAt: string;

  /** ISO 8601 timestamp of last record update */
  readonly updatedAt: string;
}

/**
 * Specific service pattern for a route defined by unique stop sequence.
 *
 * Each variant represents a distinct pattern of stops served by trips on a route.
 * The variant ID is a SHA-256 hash of the ordered stop pattern for uniqueness.
 * Multiple routes may share the same physical corridor but with different stop patterns.
 *
 * Reference: Backend RouteVariant entity at com.mobilispect.backend.transitanalysis.domain.model.RouteVariant
 */
export interface RouteVariant {
  /** SHA-256 hash of the stop pattern (64-character hex string) */
  readonly id: string;

  /** Route ID this variant belongs to */
  readonly routeId: string;

  /** GTFS direction_id (0 = outbound, 1 = inbound, null = unknown) */
  readonly directionId?: number;

  /** Destination headsign shown to passengers */
  readonly headsign?: string;

  /** Pipe-separated ordered stop IDs (e.g., "stop1|stop2|stop3") */
  readonly stopPattern: string;

  /** Number of stops in the pattern */
  readonly stopCount: number;

  /** ID of the first stop in the pattern */
  readonly firstStopId: string;

  /** ID of the last stop in the pattern */
  readonly lastStopId: string;

  /** Whether this variant is currently active */
  readonly active: boolean;

  /** ISO 8601 timestamp when this variant was first observed */
  readonly firstSeen: string;

  /** ISO 8601 timestamp when this variant was last observed */
  readonly lastSeen: string;

  /** ISO 8601 timestamp of record creation */
  readonly createdAt: string;

  /** ISO 8601 timestamp of last record update */
  readonly updatedAt: string;
}

/**
 * Service headway (frequency) for a route variant during a specific time period.
 *
 * Tracks how often a particular route variant runs during different times of day
 * and days of the week. Headway is the time between consecutive vehicles serving
 * the same route variant. For irregular schedules, no fixed headway exists.
 *
 * Reference: Backend Frequency entity at com.mobilispect.backend.transitanalysis.domain.model.Frequency
 */
export interface Frequency {
  /** Unique frequency identifier (UUID) */
  readonly id: string;

  /** Route variant ID this frequency applies to */
  readonly variantId: string;

  /** Date this frequency data applies to (YYYY-MM-DD format) */
  readonly serviceDate: string;

  /** Time period (peak, off-peak, weekend, etc.) */
  readonly timePeriod: TimePeriod;

  /** Average headway in minutes (null if irregular schedule) */
  readonly averageHeadway?: number;

  /** Minimum headway in minutes */
  readonly minHeadway?: number;

  /** Maximum headway in minutes */
  readonly maxHeadway?: number;

  /** Number of trips in this time period */
  readonly tripCount: number;

  /** True if no fixed pattern exists (irregular schedule) */
  readonly isIrregular: boolean;

  /** ISO 8601 timestamp when this frequency was calculated */
  readonly calculatedAt: string;

  /** ISO 8601 timestamp of record creation */
  readonly createdAt: string;
}

/**
 * Geographic segment where multiple routes/variants overlap.
 *
 * Represents a common section of track or road where multiple route variants
 * provide service along the same sequence of stops. This is useful for
 * calculating combined frequency on corridors served by multiple routes.
 *
 * Constitutional requirement: Must have at least 3 stops to be considered
 * a meaningful common section.
 *
 * Reference: Backend CommonSection entity at com.mobilispect.backend.transitanalysis.domain.model.CommonSection
 */
export interface CommonSection {
  /** Unique identifier (UUID) */
  readonly id: string;

  /** Pipe-separated ordered stop IDs (e.g., "stop1|stop2|stop3") */
  readonly stopPattern: string;

  /** Number of stops in the pattern (must be >= 3) */
  readonly stopCount: number;

  /** ID of the first stop in the pattern */
  readonly firstStopId: string;

  /** ID of the last stop in the pattern */
  readonly lastStopId: string;

  /** ISO 8601 timestamp of record creation */
  readonly createdAt: string;

  /** ISO 8601 timestamp of last record update */
  readonly updatedAt: string;
}

/**
 * Aggregated frequency statistics for a collection of route variants.
 *
 * Provides summarized frequency data for analysis across multiple variants,
 * routes, agencies, or regions. Used for dashboards, comparisons, and trend analysis.
 */
export interface FrequencyStats {
  /** Count of unique route variants included in aggregation */
  readonly variantCount: number;

  /** Average headway across all variants (minutes) */
  readonly averageHeadway: number;

  /** Minimum headway across all variants (minutes) */
  readonly minHeadway: number;

  /** Maximum headway across all variants (minutes) */
  readonly maxHeadway: number;

  /** Standard deviation of headway values (minutes) */
  readonly standardDeviation: number;

  /** Total number of trips analyzed */
  readonly totalTrips: number;

  /** Percentage of variants with irregular schedules */
  readonly irregularPercentage: number;

  /** Time period these statistics apply to */
  readonly timePeriod: TimePeriod;

  /** Date these statistics were calculated (YYYY-MM-DD format) */
  readonly statisticsDate: string;

  /** ISO 8601 timestamp when statistics were calculated */
  readonly calculatedAt: string;
}

/**
 * Request payload for querying frequency data.
 *
 * Used for filtering and retrieving frequency data from the backend API.
 */
export interface FrequencyQueryRequest {
  /** Filter by route variant ID */
  readonly variantId?: string;

  /** Filter by route ID */
  readonly routeId?: string;

  /** Filter by agency ID */
  readonly agencyId?: string;

  /** Filter by region ID */
  readonly regionId?: string;

  /** Filter by time period */
  readonly timePeriod?: TimePeriod;

  /** Filter by start date (YYYY-MM-DD format) */
  readonly startDate?: string;

  /** Filter by end date (YYYY-MM-DD format) */
  readonly endDate?: string;

  /** Maximum number of results to return */
  readonly limit?: number;

  /** Number of results to skip (for pagination) */
  readonly offset?: number;
}

/**
 * Response payload containing frequency data.
 *
 * Used for returning frequency query results from the backend API.
 */
export interface FrequencyQueryResponse {
  /** Array of frequency records matching query criteria */
  readonly frequencies: Frequency[];

  /** Total count of matching records (without pagination limit) */
  readonly totalCount: number;

  /** Current page number (zero-indexed) */
  readonly page: number;

  /** Number of records per page */
  readonly pageSize: number;

  /** Aggregated statistics for the matching records */
  readonly stats: FrequencyStats;
}
