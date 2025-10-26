import { Injectable } from '@angular/core';
import { CanActivate, CanActivateChild, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { Observable, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

/**
 * Feed Management Route Guard
 *
 * Protects feed management routes with role-based access control.
 * Implements the same permission hierarchy as the backend security configuration.
 *
 * Role Hierarchy:
 * - FEED_VIEWER: Can view import progress and history
 * - FEED_OPERATOR: Can initiate/cancel imports + viewer permissions
 * - FEED_MANAGER: Can configure regions/auth + operator permissions
 */
@Injectable({
  providedIn: 'root'
})
export class FeedManagementGuard implements CanActivate, CanActivateChild {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean> | Promise<boolean> | boolean {
    return this.checkFeedManagementAccess(route);
  }

  canActivateChild(
    childRoute: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean> | Promise<boolean> | boolean {
    return this.checkFeedManagementAccess(childRoute);
  }

  private checkFeedManagementAccess(route: ActivatedRouteSnapshot): Observable<boolean> {
    return this.authService.getCurrentUser().pipe(
      map(user => {
        if (!user || !user.isAuthenticated) {
          this.router.navigate(['/login']);
          return false;
        }

        const requiredRole = this.getRequiredRole(route);
        const hasAccess = this.hasRequiredRole(user.roles, requiredRole);

        if (!hasAccess) {
          this.router.navigate(['/unauthorized'], {
            queryParams: {
              message: `Access denied. Required role: ${requiredRole}`,
              redirectUrl: route.url.join('/')
            }
          });
          return false;
        }

        return true;
      }),
      catchError(error => {
        console.error('Error checking feed management access:', error);
        this.router.navigate(['/login']);
        return of(false);
      })
    );
  }

  /**
   * Determines the required role based on the route configuration
   */
  private getRequiredRole(route: ActivatedRouteSnapshot): string {
    // Check route data for explicit role requirement
    if (route.data?.['requiredRole']) {
      return route.data['requiredRole'];
    }

    // Determine role based on route path
    const url = route.url.map(segment => segment.path).join('/');

    // Manager-only routes (configuration)
    if (url.includes('settings') ||
        url.includes('authentication') ||
        url.includes('config') ||
        route.data?.['managerOnly']) {
      return 'FEED_MANAGER';
    }

    // Operator routes (import actions)
    if (url.includes('import') ||
        url.includes('cancel') ||
        route.data?.['operatorOnly']) {
      return 'FEED_OPERATOR';
    }

    // Default to viewer role for all other feed management routes
    return 'FEED_VIEWER';
  }

  /**
   * Checks if user has the required role or higher in the hierarchy
   */
  private hasRequiredRole(userRoles: string[], requiredRole: string): boolean {
    const roleHierarchy = {
      'FEED_VIEWER': ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER'],
      'FEED_OPERATOR': ['FEED_OPERATOR', 'FEED_MANAGER'],
      'FEED_MANAGER': ['FEED_MANAGER']
    };

    const allowedRoles = roleHierarchy[requiredRole as keyof typeof roleHierarchy] || [];

    return userRoles.some(userRole =>
      allowedRoles.some(allowedRole =>
        userRole.includes(allowedRole) || userRole === allowedRole
      )
    );
  }
}

/**
 * Specific guards for different permission levels
 */
@Injectable({
  providedIn: 'root'
})
export class FeedViewerGuard implements CanActivate {
  constructor(private feedGuard: FeedManagementGuard) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
    // Override route data to require viewer role
    route.data = { ...route.data, requiredRole: 'FEED_VIEWER' };
    return this.feedGuard.canActivate(route, state) as Observable<boolean>;
  }
}

@Injectable({
  providedIn: 'root'
})
export class FeedOperatorGuard implements CanActivate {
  constructor(private feedGuard: FeedManagementGuard) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
    // Override route data to require operator role
    route.data = { ...route.data, requiredRole: 'FEED_OPERATOR' };
    return this.feedGuard.canActivate(route, state) as Observable<boolean>;
  }
}

@Injectable({
  providedIn: 'root'
})
export class FeedManagerGuard implements CanActivate {
  constructor(private feedGuard: FeedManagementGuard) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
    // Override route data to require manager role
    route.data = { ...route.data, requiredRole: 'FEED_MANAGER' };
    return this.feedGuard.canActivate(route, state) as Observable<boolean>;
  }
}