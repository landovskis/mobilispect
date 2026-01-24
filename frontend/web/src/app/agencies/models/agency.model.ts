export interface AgencyDTO {
  id: string;
  name: string;
  feedOnestopId: string;
  regionIds: string[];
  routeCount: number;
  activeRouteCount: number;
  routesByType: Record<string, number>;
}

export interface AgencySummary {
  id: string;
  name: string;
  routeCount: number;
  averageHeadwayMinutes?: number | null;
  minHeadwayMinutes?: number | null;
  maxHeadwayMinutes?: number | null;
}
