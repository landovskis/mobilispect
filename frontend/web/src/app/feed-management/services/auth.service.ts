import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { map, catchError, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

/**
 * User model for authentication
 */
export interface User {
  id: string;
  username: string;
  email?: string;
  roles: string[];
  permissions: string[];
  isAuthenticated: boolean;
}

/**
 * JWT Token payload interface
 */
interface JwtPayload {
  sub: string;
  preferred_username?: string;
  username?: string;
  email?: string;
  roles?: string[];
  permissions?: string[];
  exp: number;
  iat: number;
}

/**
 * Authentication service for Feed Management System
 *
 * Handles JWT token management, user authentication, and role-based authorization.
 * Integrates with backend OAuth2/JWT authentication system.
 *
 * Constitutional Compliance:
 * - Security: JWT token validation and secure storage
 * - Performance: Cached user state with reactive updates
 * - Observability: Authentication events logging
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly TOKEN_KEY = 'auth_token';
  private readonly USER_KEY = 'current_user';
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {
    this.initializeAuthState();
  }

  /**
   * Initialize authentication state from stored token
   */
  private initializeAuthState(): void {
    const token = this.getStoredToken();
    if (token && this.isTokenValid(token)) {
      const user = this.parseUserFromToken(token);
      if (user) {
        this.currentUserSubject.next(user);
      }
    } else {
      this.clearAuthState();
    }
  }

  /**
   * Authenticate user with username/password
   */
  login(username: string, password: string): Observable<User> {
    return this.http.post<{ access_token: string; user: any }>(`${environment.apiUrl}/auth/login`, {
      username,
      password
    }).pipe(
      tap(response => {
        this.storeToken(response.access_token);
        const user = this.parseUserFromToken(response.access_token);
        if (user) {
          this.currentUserSubject.next(user);
          this.storeUser(user);
        }
      }),
      map(response => this.parseUserFromToken(response.access_token)!),
      catchError(error => {
        console.error('Login failed:', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Logout current user
   */
  logout(): void {
    this.clearAuthState();
    this.currentUserSubject.next(null);
  }

  /**
   * Get current authenticated user
   */
  getCurrentUser(): Observable<User | null> {
    return this.currentUser$;
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    const token = this.getStoredToken();
    return token !== null && this.isTokenValid(token);
  }

  /**
   * Check if user has any of the specified roles
   */
  hasRole(roles: string[]): boolean {
    const user = this.currentUserSubject.value;
    if (!user || !user.isAuthenticated) {
      return false;
    }

    return roles.some(role => user.roles.includes(role));
  }

  /**
   * Check if user has specific permission
   */
  hasPermission(permission: string): boolean {
    const user = this.currentUserSubject.value;
    if (!user || !user.isAuthenticated) {
      return false;
    }

    return user.permissions.includes(permission);
  }

  /**
   * Get user's highest feed management role
   */
  getHighestFeedRole(): string | null {
    const user = this.currentUserSubject.value;
    if (!user || !user.isAuthenticated) {
      return null;
    }

    if (user.roles.includes('FEED_MANAGER')) return 'FEED_MANAGER';
    if (user.roles.includes('FEED_OPERATOR')) return 'FEED_OPERATOR';
    if (user.roles.includes('FEED_VIEWER')) return 'FEED_VIEWER';

    return null;
  }

  /**
   * Check if user can view imports
   */
  canViewImports(): boolean {
    return this.hasRole(['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']);
  }

  /**
   * Check if user can initiate imports
   */
  canInitiateImports(): boolean {
    return this.hasRole(['FEED_OPERATOR', 'FEED_MANAGER']);
  }

  /**
   * Check if user can cancel imports
   */
  canCancelImports(): boolean {
    return this.hasRole(['FEED_OPERATOR', 'FEED_MANAGER']);
  }

  /**
   * Check if user can manage feed authentication
   */
  canManageAuthentication(): boolean {
    return this.hasRole(['FEED_MANAGER']);
  }

  /**
   * Check if user can configure regions
   */
  canConfigureRegions(): boolean {
    return this.hasRole(['FEED_MANAGER']);
  }

  /**
   * Get authorization header for API calls
   */
  getAuthorizationHeader(): string | null {
    const token = this.getStoredToken();
    return token ? `Bearer ${token}` : null;
  }

  /**
   * Refresh authentication token
   */
  refreshToken(): Observable<string> {
    return this.http.post<{ access_token: string }>(`${environment.apiUrl}/auth/refresh`, {}).pipe(
      tap(response => {
        this.storeToken(response.access_token);
        const user = this.parseUserFromToken(response.access_token);
        if (user) {
          this.currentUserSubject.next(user);
          this.storeUser(user);
        }
      }),
      map(response => response.access_token),
      catchError(error => {
        console.error('Token refresh failed:', error);
        this.logout();
        return throwError(() => error);
      })
    );
  }

  // Private helper methods

  private getStoredToken(): string | null {
    if (typeof window !== 'undefined') {
      return localStorage.getItem(this.TOKEN_KEY);
    }
    return null;
  }

  private storeToken(token: string): void {
    if (typeof window !== 'undefined') {
      localStorage.setItem(this.TOKEN_KEY, token);
    }
  }

  private storeUser(user: User): void {
    if (typeof window !== 'undefined') {
      localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    }
  }

  private clearAuthState(): void {
    if (typeof window !== 'undefined') {
      localStorage.removeItem(this.TOKEN_KEY);
      localStorage.removeItem(this.USER_KEY);
    }
  }

  private isTokenValid(token: string): boolean {
    try {
      const payload = this.parseJwtPayload(token);
      const now = Math.floor(Date.now() / 1000);
      return payload.exp > now;
    } catch {
      return false;
    }
  }

  private parseJwtPayload(token: string): JwtPayload {
    const parts = token.split('.');
    if (parts.length !== 3) {
      throw new Error('Invalid JWT format');
    }

    const payload = parts[1];
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decoded);
  }

  private parseUserFromToken(token: string): User | null {
    try {
      const payload = this.parseJwtPayload(token);

      return {
        id: payload.sub,
        username: payload.preferred_username || payload.username || payload.sub,
        email: payload.email,
        roles: payload.roles || [],
        permissions: payload.permissions || [],
        isAuthenticated: true
      };
    } catch (error) {
      console.error('Failed to parse user from token:', error);
      return null;
    }
  }
}
