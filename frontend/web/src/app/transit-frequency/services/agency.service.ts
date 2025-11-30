import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AgencySummary } from '../models/agency-summary.model';

export interface AgencyListResponse {
  content: AgencySummary[];
  totalElements: number;
  totalPages: number;
}

@Injectable({
  providedIn: 'root'
})
export class AgencyService {
  private readonly baseUrl = '/api/v1/frequency';

  constructor(private readonly http: HttpClient) {}

  listAgencies(page: number = 0, size: number = 20, regionId?: string): Observable<AgencyListResponse> {
    const params: Record<string, any> = { page, size };
    if (regionId) params.regionId = regionId;
    return this.http.get<AgencyListResponse>(`${this.baseUrl}/agencies`, {
      params
    });
  }

  getAgency(agencyId: string): Observable<AgencySummary> {
    return this.http.get<AgencySummary>(`${this.baseUrl}/agencies/${agencyId}`);
  }
}
