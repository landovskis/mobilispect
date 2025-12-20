import { RouteType } from '../../transit-frequency/models/route-type.model';

export interface RouteDTO {
  id: string;
  agencyId: string;
  shortName: string | null;
  longName: string;
  routeType: RouteType;
  active: boolean;
}

export interface RouteListResponse {
  content: RouteDTO[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
