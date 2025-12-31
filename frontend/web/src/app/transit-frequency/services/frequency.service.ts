import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
export interface RouteVariantDto {
  id: string;
  routeId: string;
  directionId?: number | null;
  headsign?: string | null;
  stopCount: number;
  stopPattern: string;
  stopNames?: string[] | null;
  firstStopId: string;
  lastStopId: string;
  averageStopSpacingMeters?: number | null;
  stopSpacingMeters?: number[] | null;
  stopSpacingClassification?:
    | 'local'
    | 'rapid'
    | 'region-local'
    | 'region-rapid'
    | 'region-express'
    | 'express'
    | null;
}

export interface RouteHourlyStatsDto {
  serviceDate: string;
  directionId?: number | null;
  dayType?: 'WEEKDAY' | 'SATURDAY' | 'SUNDAY' | 'HOLIDAY';
  hourOfDay: number;
  tripCount: number;
  averageSpeedKph?: number | null;
}

export interface RouteDto {
  id: string;
  agencyId: string;
  shortName?: string | null;
  longName: string;
  routeType: string;
  active: boolean;
  variants: RouteVariantDto[];
  hourlyStats: RouteHourlyStatsDto[];
}

@Injectable({ providedIn: 'root' })
export class FrequencyService {
  private readonly baseUrl = '/api/v1/routes';

  constructor(private readonly http: HttpClient) {}

  getRoute(routeId: string): Observable<RouteDto> {
    return this.http.get<RouteDto>(`${this.baseUrl}/${routeId}`);
  }
}
