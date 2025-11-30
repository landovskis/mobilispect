import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TimePeriod } from '../models/time-period.model';

export interface FrequencyDto {
  id: string;
  variantId: string;
  serviceDate: string;
  timePeriod: TimePeriod;
  averageHeadwayMinutes?: number | null;
  minHeadwayMinutes?: number | null;
  maxHeadwayMinutes?: number | null;
  tripCount: number;
  isIrregular: boolean;
}

export interface RouteVariantDto {
  id: string;
  routeId: string;
  directionId?: number | null;
  headsign?: string | null;
  stopCount: number;
  stopPattern: string;
  firstStopId: string;
  lastStopId: string;
}

export interface RouteDto {
  id: string;
  agencyId: string;
  shortName?: string | null;
  longName: string;
  routeType: string;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class FrequencyService {
  private readonly baseUrl = '/api/v1/routes';

  constructor(private readonly http: HttpClient) {}

  getRoute(routeId: string): Observable<RouteDto> {
    return this.http.get<RouteDto>(`${this.baseUrl}/${routeId}`);
  }

  getVariants(routeId: string): Observable<RouteVariantDto[]> {
    return this.http.get<RouteVariantDto[]>(`${this.baseUrl}/${routeId}/variants`);
  }

  getFrequencies(variantId: string, date?: string): Observable<FrequencyDto[]> {
    const params: any = {};
    if (date) params.date = date;
    return this.http.get<FrequencyDto[]>(`${this.baseUrl}/variants/${variantId}/frequencies`, { params });
  }
}
