import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RegionSummary {
  id: string;
  name: string;
  adm0Name?: string;
  adm1Name?: string;
  agencyCount: number;
}

@Injectable({
  providedIn: 'root'
})
export class RegionService {
  private readonly baseUrl = '/api/v1/frequency';
  private readonly http = inject(HttpClient);

  listRegions(): Observable<RegionSummary[]> {
    return this.http.get<RegionSummary[]>(`${this.baseUrl}/regions`);
  }
}
