import { inject, Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AgencySummary } from '../../transit-frequency/models/agency.model';
import { AgencyListResponse } from '../../transit-frequency/services/agency.service';
import { RouteListResponse } from '../models/route.model';

@Injectable({
  providedIn: 'root',
})
export class AgencyService {
  private readonly apiUrl = `${environment.apiUrl}`;
  private readonly http = inject(HttpClient);

  listAgencies(
    page: number = 0,
    size: number = 20,
    regionId?: string,
  ): Observable<AgencyListResponse> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    const url = regionId
      ? `${this.apiUrl}/regions/${regionId}/agencies`
      : `${this.apiUrl}/agencies`;
    return this.http.get<AgencyListResponse>(url, { params });
  }

  getAgency(agencyId: string): Observable<AgencySummary> {
    return this.http.get<AgencySummary>(`${this.apiUrl}/agencies/${agencyId}`);
  }

  listRoutesByAgency(
    agencyId: string,
    page: number = 0,
    size: number = 500,
  ): Observable<RouteListResponse> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<RouteListResponse>(
      `${this.apiUrl}/agencies/${agencyId}/routes`,
      { params },
    );
  }
}
