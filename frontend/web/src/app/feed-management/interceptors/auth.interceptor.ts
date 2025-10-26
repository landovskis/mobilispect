import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, switchMap, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

/**
 * HTTP Interceptor for JWT authentication
 *
 * Automatically adds Authorization header to API requests and handles
 * token refresh on 401 responses.
 *
 * Constitutional Compliance:
 * - Security: Automatic JWT token attachment and refresh
 * - Performance: Efficient token management
 * - Observability: Authentication error logging
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // Skip auth for login/register endpoints
  if (req.url.includes('/auth/login') || req.url.includes('/auth/register')) {
    return next(req);
  }

  // Add authorization header if authenticated
  const authHeader = authService.getAuthorizationHeader();
  if (authHeader) {
    req = req.clone({
      setHeaders: {
        Authorization: authHeader
      }
    });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Handle 401 Unauthorized - attempt token refresh
      if (error.status === 401 && authService.isAuthenticated()) {
        console.log('Token expired, attempting refresh...');

        return authService.refreshToken().pipe(
          switchMap(newToken => {
            // Retry the original request with new token
            const retryReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${newToken}`
              }
            });
            return next(retryReq);
          }),
          catchError(refreshError => {
            console.error('Token refresh failed, redirecting to login');
            authService.logout();
            return throwError(() => refreshError);
          })
        );
      }

      // Handle 403 Forbidden - insufficient permissions
      if (error.status === 403) {
        console.error('Access denied - insufficient permissions');
      }

      return throwError(() => error);
    })
  );
};
