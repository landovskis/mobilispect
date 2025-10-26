import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { map, catchError, of } from 'rxjs';

/**
 * Guard for routes requiring Feed Manager permissions
 *
 * Checks if the current user has FEED_MANAGER role.
 * Redirects to login if not authenticated, or access denied if insufficient permissions.
 *
 * Usage: { path: 'configuration', canActivate: [feedManagerGuard] }
 */
export const feedManagerGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.getCurrentUser().pipe(
    map(user => {
      if (!user) {
        router.navigate(['/login'], {
          queryParams: { returnUrl: router.routerState.snapshot.url }
        });
        return false;
      }

      if (authService.hasRole(['FEED_MANAGER'])) {
        return true;
      }

      router.navigate(['/access-denied']);
      return false;
    }),
    catchError(error => {
      console.error('Authentication error in feedManagerGuard:', error);
      router.navigate(['/login']);
      return of(false);
    })
  );
};
