import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AgencyDTO, AgencySummary } from '../models/agency.model';

export interface AgencyListResponse {
  content: AgencyDTO[];
  totalElements: number;
  totalPages: number;
}

export interface AgencySummaryListResponse {
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
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    const url = regionId
      ? `${this.baseUrl}/regions/${regionId}/agencies`
      : `${this.baseUrl}/agencies`;
    return this.http.get<AgencyListResponse>(url, { params });
  }

  getAgency(agencyId: string): Observable<AgencySummary> {
    return this.http.get<AgencySummary>(`${this.baseUrl}/agencies/${agencyId}`);
  }
}
