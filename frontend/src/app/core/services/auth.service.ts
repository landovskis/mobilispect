import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { map, tap, catchError } from 'rxjs/operators';

/**
 * Authentication Service for Feed Management
 *
 * Handles JWT-based authentication and role management.
 * Integrates with the backend security configuration to provide
 * client-side authentication state management.
 */

export interface User {
  id: string;
  username: string;
  email: string;
  roles: string[];
  isAuthenticated: boolean;
  permissions: UserPermissions;
}

export interface UserPermissions {
  canViewImports: boolean;
  canInitiateImports: boolean;
  canCancelImports: boolean;
  canManageAuthentication: boolean;
  canConfigureRegions: boolean;
  highestFeedRole: string | null;
}

export interface AuthToken {
  accessToken: string;
  refreshToken?: string;
  expiresAt: number;
  tokenType: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly TOKEN_KEY = 'feed_management_token';
  private readonly USER_KEY = 'feed_management_user';

  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(false);
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadStoredAuth();
  }

  /**
   * Gets the current user observable
   */
  getCurrentUser(): Observable<User | null> {
    return this.currentUser$;
  }

  /**
   * Gets the current user synchronously
   */
  getCurrentUserSync(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * Checks if user is authenticated
   */
  isAuthenticated(): Observable<boolean> {
    return this.isAuthenticated$;
  }

  /**
   * Checks if user is authenticated synchronously
   */
  isAuthenticatedSync(): boolean {
    return this.isAuthenticatedSubject.value;
  }

  /**
   * Login with username and password
   */
  login(username: string, password: string): Observable<User> {
    return this.http.post<AuthToken>('/api/auth/login', {
      username,
      password
    }).pipe(
      tap(token => this.storeToken(token)),
      map(() => this.loadUserFromToken()),
      tap(user => this.setCurrentUser(user))
    );
  }

  /**
   * Login with JWT token (e.g., from external OAuth)
   */
  loginWithToken(token: AuthToken): Observable<User> {
    this.storeToken(token);
    const user = this.loadUserFromToken();
    this.setCurrentUser(user);
    return of(user);
  }

  /**
   * Logout user
   */
  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    this.isAuthenticatedSubject.next(false);
  }

  /**
   * Refresh authentication token
   */
  refreshToken(): Observable<AuthToken> {
    const currentToken = this.getStoredToken();
    if (!currentToken?.refreshToken) {
      throw new Error('No refresh token available');
    }

    return this.http.post<AuthToken>('/api/auth/refresh', {
      refreshToken: currentToken.refreshToken
    }).pipe(
      tap(token => this.storeToken(token)),
      catchError(error => {
        this.logout();
        throw error;
      })
    );
  }

  /**
   * Gets the current JWT token
   */
  getToken(): string | null {
    const tokenData = this.getStoredToken();
    if (!tokenData || this.isTokenExpired(tokenData)) {
      return null;
    }
    return tokenData.accessToken;
  }

  /**
   * Checks if user has specific role
   */
  hasRole(role: string): boolean {
    const user = this.getCurrentUserSync();
    return user?.roles.some(userRole =>
      userRole.includes(role) || userRole === role
    ) || false;
  }

  /**
   * Checks if user has any of the specified roles
   */
  hasAnyRole(roles: string[]): boolean {
    return roles.some(role => this.hasRole(role));
  }

  /**
   * Checks if user has feed management permissions
   */
  hasViewerPermissions(): boolean {
    return this.hasAnyRole(['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']);
  }

  hasOperatorPermissions(): boolean {
    return this.hasAnyRole(['FEED_OPERATOR', 'FEED_MANAGER']);
  }

  hasManagerPermissions(): boolean {
    return this.hasRole('FEED_MANAGER');
  }

  /**
   * Gets user's highest feed management role
   */
  getHighestFeedRole(): string | null {
    if (this.hasRole('FEED_MANAGER')) return 'FEED_MANAGER';
    if (this.hasRole('FEED_OPERATOR')) return 'FEED_OPERATOR';
    if (this.hasRole('FEED_VIEWER')) return 'FEED_VIEWER';
    return null;
  }

  /**
   * Load stored authentication state
   */
  private loadStoredAuth(): void {
    try {
      const tokenData = this.getStoredToken();
      const userData = localStorage.getItem(this.USER_KEY);

      if (tokenData && !this.isTokenExpired(tokenData) && userData) {
        const user = JSON.parse(userData) as User;
        this.setCurrentUser(user);
      } else {
        this.logout();
      }
    } catch (error) {
      console.error('Error loading stored auth:', error);
      this.logout();
    }
  }

  /**
   * Load user information from JWT token
   */
  private loadUserFromToken(): User {
    const token = this.getToken();
    if (!token) {
      throw new Error('No valid token available');
    }

    try {
      const payload = this.parseJwtPayload(token);

      const roles = this.extractRoles(payload);
      const permissions = this.calculatePermissions(roles);

      const user: User = {
        id: payload.sub || '',
        username: payload.preferred_username || payload.username || payload.sub || '',
        email: payload.email || '',
        roles,
        isAuthenticated: true,
        permissions
      };

      localStorage.setItem(this.USER_KEY, JSON.stringify(user));
      return user;
    } catch (error) {
      console.error('Error parsing JWT token:', error);
      throw new Error('Invalid JWT token');
    }
  }

  /**
   * Parse JWT payload
   */
  private parseJwtPayload(token: string): any {
    const parts = token.split('.');
    if (parts.length !== 3) {
      throw new Error('Invalid JWT format');
    }

    const payload = parts[1];
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decoded);
  }

  /**
   * Extract roles from JWT payload
   */
  private extractRoles(payload: any): string[] {
    // Handle different JWT structures for roles
    const roles: string[] = [];

    // Keycloak realm roles
    if (payload.realm_access?.roles) {
      roles.push(...payload.realm_access.roles);
    }

    // Keycloak resource roles
    if (payload.resource_access) {
      Object.values(payload.resource_access).forEach((resource: any) => {
        if (resource.roles) {
          roles.push(...resource.roles);
        }
      });
    }

    // Direct roles array
    if (payload.roles && Array.isArray(payload.roles)) {
      roles.push(...payload.roles);
    }

    // Authority format (Spring Security)
    if (payload.authorities && Array.isArray(payload.authorities)) {
      roles.push(...payload.authorities);
    }

    return roles.filter((role, index, self) => self.indexOf(role) === index); // Remove duplicates
  }

  /**
   * Calculate user permissions based on roles
   */
  private calculatePermissions(roles: string[]): UserPermissions {
    const hasRole = (role: string) => roles.some(userRole =>
      userRole.includes(role) || userRole === role
    );

    return {
      canViewImports: hasRole('FEED_VIEWER') || hasRole('FEED_OPERATOR') || hasRole('FEED_MANAGER'),
      canInitiateImports: hasRole('FEED_OPERATOR') || hasRole('FEED_MANAGER'),
      canCancelImports: hasRole('FEED_OPERATOR') || hasRole('FEED_MANAGER'),
      canManageAuthentication: hasRole('FEED_MANAGER'),
      canConfigureRegions: hasRole('FEED_MANAGER'),
      highestFeedRole: this.getHighestFeedRoleFromRoles(roles)
    };
  }

  /**
   * Get highest feed role from roles array
   */
  private getHighestFeedRoleFromRoles(roles: string[]): string | null {
    const hasRole = (role: string) => roles.some(userRole =>
      userRole.includes(role) || userRole === role
    );

    if (hasRole('FEED_MANAGER')) return 'FEED_MANAGER';
    if (hasRole('FEED_OPERATOR')) return 'FEED_OPERATOR';
    if (hasRole('FEED_VIEWER')) return 'FEED_VIEWER';
    return null;
  }

  /**
   * Store authentication token
   */
  private storeToken(token: AuthToken): void {
    localStorage.setItem(this.TOKEN_KEY, JSON.stringify(token));
  }

  /**
   * Get stored token
   */
  private getStoredToken(): AuthToken | null {
    try {
      const tokenData = localStorage.getItem(this.TOKEN_KEY);
      return tokenData ? JSON.parse(tokenData) : null;
    } catch {
      return null;
    }
  }

  /**
   * Check if token is expired
   */
  private isTokenExpired(token: AuthToken): boolean {
    return Date.now() >= token.expiresAt;
  }

  /**
   * Set current user
   */
  private setCurrentUser(user: User): void {
    this.currentUserSubject.next(user);
    this.isAuthenticatedSubject.next(true);
  }
}