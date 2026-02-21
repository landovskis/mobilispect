import { Injectable, inject } from '@angular/core';
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
  stopSpacingsMeters?: number[] | null;
  firstStopId: string;
  lastStopId: string;
  firstDepartureTime?: string | null;
  lastDepartureTime?: string | null;
  scheduleTripCount?: number | null;
  classification?: string | null;
  averageStopSpacingMeters?: number | null;
  clockFaceIntervalMinutes?: number | null;
}

export interface RouteDto {
  id: string;
  agencyId: string;
  shortName?: string | null;
  longName: string;
  routeType: string;
  active: boolean;
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

export interface RouteCommonSectionDto {
  id: string;
  routeId: string;
  directionId?: number | null;
  stopPattern: string;
  stopNames: string[];
  stopCount: number;
  firstStopId: string;
  lastStopId: string;
  variantCount: number;
}

@Injectable({ providedIn: 'root' })
export class FrequencyService {
  private readonly baseUrl = '/api/v1/routes';
  private readonly http = inject(HttpClient);

  getRoute(routeId: string): Observable<RouteDto> {
    return this.http.get<RouteDto>(`${this.baseUrl}/${routeId}`);
  }

  getVariants(routeId: string): Observable<RouteVariantDto[]> {
    return this.http.get<RouteVariantDto[]>(
      `${this.baseUrl}/${routeId}/variants`,
    );
  }

  getFrequencies(variantId: string, date?: string): Observable<FrequencyDto[]> {
    const params: Record<string, string> = {};
    if (date) params['date'] = date;
    return this.http.get<FrequencyDto[]>(
      `${this.baseUrl}/variants/${variantId}/frequencies`,
      { params },
    );
  }

  getRouteHourlyFrequencies(
    routeId: string,
    date: string,
  ): Observable<RouteHourlyFrequencyDto[]> {
    return this.http.get<RouteHourlyFrequencyDto[]>(
      `${this.baseUrl}/${routeId}/hourly-frequencies`,
      { params: { date } },
    );
  }

  getVariantHourlyFrequencies(
    variantId: string,
    date: string,
  ): Observable<HourlyFrequencyDto[]> {
    return this.http.get<HourlyFrequencyDto[]>(
      `${this.baseUrl}/variants/${variantId}/hourly-frequencies`,
      { params: { date } },
    );
  }

  getCompleteSchedule(variantId: string): Observable<string[]> {
    return this.http.get<string[]>(
      `${this.baseUrl}/variants/${variantId}/schedule`,
    );
  }

  getCommonSections(routeId: string): Observable<RouteCommonSectionDto[]> {
    return this.http.get<RouteCommonSectionDto[]>(
      `${this.baseUrl}/${routeId}/common-sections`,
    );
  }
}
