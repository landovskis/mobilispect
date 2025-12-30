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
  stopNames?: string[] | null;
  firstStopId: string;
  lastStopId: string;
  averageStopSpacingKm?: number | null;
  stopSpacingClassification?: 'local' | 'rapid' | 'express' | null;
}

export interface RouteDto {
  id: string;
  agencyId: string;
  shortName?: string | null;
  longName: string;
  routeType: string;
  active: boolean;
  variants: RouteVariantDto[];
}

export interface HourlyFrequencyDto {
  variantId: string;
  serviceDate: string;
  hourOfDay: number;
  averageHeadwayMinutes?: number | null;
  minHeadwayMinutes?: number | null;
  maxHeadwayMinutes?: number | null;
  tripCount: number;
  isIrregular: boolean;
}

export interface RouteHourlyFrequencyDto {
  routeId: string;
  serviceDate: string;
  hourOfDay: number;
  averageHeadwayMinutes?: number | null;
  minHeadwayMinutes?: number | null;
  maxHeadwayMinutes?: number | null;
  tripCount: number;
  variantCount: number;
  isIrregular: boolean;
}

@Injectable({ providedIn: 'root' })
export class FrequencyService {
  private readonly baseUrl = '/api/v1/routes';

  constructor(private readonly http: HttpClient) {}

  getRoute(routeId: string): Observable<RouteDto> {
    return this.http.get<RouteDto>(`${this.baseUrl}/${routeId}`);
  }

  getFrequencies(variantId: string, date?: string): Observable<FrequencyDto[]> {
    const params: any = {};
    if (date) params.date = date;
    return this.http.get<FrequencyDto[]>(`${this.baseUrl}/variants/${variantId}/frequencies`, { params });
  }

  getRouteHourlyFrequencies(routeId: string, date: string): Observable<RouteHourlyFrequencyDto[]> {
    return this.http.get<RouteHourlyFrequencyDto[]>(
      `${this.baseUrl}/${routeId}/hourly-frequencies`,
      { params: { date } }
    );
  }

  getVariantHourlyFrequencies(variantId: string, date: string): Observable<HourlyFrequencyDto[]> {
    return this.http.get<HourlyFrequencyDto[]>(
      `${this.baseUrl}/variants/${variantId}/hourly-frequencies`,
      { params: { date } }
    );
  }
}
