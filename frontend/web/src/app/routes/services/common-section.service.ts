import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CommonSectionDto {
  id: string;
  stopPattern: string;
  stopCount: number;
  firstStopId: string;
  lastStopId: string;
  variants: string[];
}

export interface CombinedFrequencyDto {
  commonSectionId: string;
  timePeriod: string;
  averageHeadwayMinutes?: number | null;
  tripCount: number;
  isIrregular: boolean;
  contributions?: Array<{
    routeId: string;
    averageHeadwayMinutes?: number | null;
    tripCount: number;
    isIrregular: boolean;
  }>;
}

@Injectable({ providedIn: 'root' })
export class CommonSectionService {
  private readonly baseUrl = '/api/v1/common-sections';
  private readonly http = inject(HttpClient);

  getCommonSectionsForRoute(routeId: string): Observable<CommonSectionDto[]> {
    return this.http.get<CommonSectionDto[]>(
      `${this.baseUrl}/routes/${routeId}`,
    );
  }

  getCombinedFrequency(
    sectionId: string,
    timePeriod: string,
  ): Observable<CombinedFrequencyDto | null> {
    return this.http.get<CombinedFrequencyDto | null>(
      `${this.baseUrl}/${sectionId}/frequency`,
      {
        params: { timePeriod },
      },
    );
  }
}
