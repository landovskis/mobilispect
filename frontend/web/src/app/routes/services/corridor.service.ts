import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CorridorDto {
  id: string;
  stopPattern: string;
  stopCount: number;
  firstStopId: string;
  lastStopId: string;
  routes: CorridorRouteDto[];
}

export interface CorridorRouteDto {
  routeId: string;
  shortName?: string | null;
  longName: string;
}

@Injectable({ providedIn: 'root' })
export class CorridorService {
  private readonly baseUrl = `${environment.apiUrl}/v1/regions`;
  private readonly http = inject(HttpClient);

  getCorridorsForRegion(regionId: string): Observable<CorridorDto[]> {
    return this.http.get<CorridorDto[]>(
      `${this.baseUrl}/${regionId}/corridors`,
    );
  }
}
