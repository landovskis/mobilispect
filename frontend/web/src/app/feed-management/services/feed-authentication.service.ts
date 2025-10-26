import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  FeedAuthentication,
  FeedAuthenticationRequest,
  AuthenticationTestResult,
  AuthenticationStatistics
} from '../models/feed-authentication.model';

@Injectable({
  providedIn: 'root'
})
export class FeedAuthenticationService {
  private readonly baseUrl = `${environment.apiUrl}/api/v1/feeds`;

  constructor(private http: HttpClient) {}

  /**
   * Create or update authentication for a feed
   */
  createOrUpdateAuthentication(
    feedOnestopId: string,
    request: FeedAuthenticationRequest
  ): Observable<FeedAuthentication> {
    return this.http.put<FeedAuthentication>(
      `${this.baseUrl}/${feedOnestopId}/authentication`,
      request
    );
  }

  /**
   * Get authentication for a feed
   */
  getAuthentication(feedOnestopId: string): Observable<FeedAuthentication> {
    return this.http.get<FeedAuthentication>(
      `${this.baseUrl}/${feedOnestopId}/authentication`
    );
  }

  /**
   * Test authentication for a feed
   */
  testAuthentication(feedOnestopId: string): Observable<AuthenticationTestResult> {
    return this.http.post<AuthenticationTestResult>(
      `${this.baseUrl}/${feedOnestopId}/authentication/test`,
      {}
    );
  }

  /**
   * Delete authentication for a feed
   */
  deleteAuthentication(feedOnestopId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/${feedOnestopId}/authentication`
    );
  }

  /**
   * Reset authentication failures for a feed
   */
  resetFailures(feedOnestopId: string): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/${feedOnestopId}/authentication/reset-failures`,
      {}
    );
  }

  /**
   * Activate/deactivate authentication
   */
  setAuthenticationActive(feedOnestopId: string, active: boolean): Observable<void> {
    return this.http.patch<void>(
      `${this.baseUrl}/${feedOnestopId}/authentication/active?active=${active}`,
      {}
    );
  }

  /**
   * Get authentications requiring renewal
   */
  getAuthenticationsRequiringRenewal(): Observable<FeedAuthentication[]> {
    return this.http.get<FeedAuthentication[]>(
      `${this.baseUrl}/*/authentication/requiring-renewal`
    );
  }

  /**
   * Get expired authentications
   */
  getExpiredAuthentications(): Observable<FeedAuthentication[]> {
    return this.http.get<FeedAuthentication[]>(
      `${this.baseUrl}/*/authentication/expired`
    );
  }

  /**
   * Get locked authentications
   */
  getLockedAuthentications(): Observable<FeedAuthentication[]> {
    return this.http.get<FeedAuthentication[]>(
      `${this.baseUrl}/*/authentication/locked`
    );
  }

  /**
   * Get authentication statistics
   */
  getAuthenticationStatistics(): Observable<AuthenticationStatistics> {
    return this.http.get<AuthenticationStatistics>(
      `${this.baseUrl}/*/authentication/statistics`
    );
  }
}
