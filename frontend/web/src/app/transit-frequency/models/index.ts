/**
 * Transit Frequency Module Models
 *
 * Central export point for all transit frequency data models, enums, and types.
 * Use barrel exports for clean imports throughout the application.
 *
 * Example usage:
 * import { Frequency, TimePeriod, RouteType } from '@app/transit-frequency/models';
 */

// Time Period enum and utilities
export {
  TimePeriod,
  TimePeriodLabels,
  getTimePeriodLabel,
  getAllTimePeriods
} from './time-period.model';

// Route Type enum and utilities
export {
  RouteType,
  RouteTypeGtfsValues,
  GtfsValuesToRouteType,
  RouteTypeLabels,
  RouteTypeIcons,
  getRouteTypeLabel,
  getRouteTypeIcon,
  getRouteTypeGtfsValue,
  getRouteTypeFromGtfsValue,
  getAllRouteTypes
} from './route-type.model';

// Transit Frequency interfaces and types
export type {
  Region,
  Agency,
  Route,
  RouteVariant,
  Frequency,
  CommonSection,
  FrequencyStats,
  FrequencyQueryRequest,
  FrequencyQueryResponse
} from './transit-frequency.model';
