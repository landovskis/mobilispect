import { inject, Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { RouteService } from '../services/route.service';

@Injectable({ providedIn: 'root' })
export class RouteBreadcrumbResolver implements Resolve<string> {
  private readonly frequencyService = inject(RouteService);

  constructor() {}

  resolve(route: ActivatedRouteSnapshot): Observable<string> {
    const routeId = route.paramMap.get('routeId');
    if (!routeId) return of('Route');

    return this.frequencyService.getRoute(routeId).pipe(
      map((route) => {
        if (route.shortName && route.longName) {
          return `${route.shortName} ${route.longName}`;
        }
        return route.shortName || route.longName || routeId;
      }),
      catchError(() => of(routeId)),
    );
  }
}
