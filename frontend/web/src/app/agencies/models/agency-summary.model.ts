export interface AgencySummary {
  id: string;
  name: string;
  routeCount: number;
  averageHeadwayMinutes?: number | null;
  minHeadwayMinutes?: number | null;
  maxHeadwayMinutes?: number | null;
}
