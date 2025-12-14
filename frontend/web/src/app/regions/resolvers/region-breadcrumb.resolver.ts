import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { RegionService } from '../../feeds/services/region.service';
import { RegionUtils } from '../../feeds/models/region.models';

@Injectable({ providedIn: 'root' })
export class RegionBreadcrumbResolver implements Resolve<string> {
  constructor(private readonly regionService: RegionService) {}

  resolve(route: ActivatedRouteSnapshot): Observable<string> {
    const regionId = route.paramMap.get('regionId');
    if (!regionId) {
      return of('Region');
    }

    return this.regionService.getRegion(regionId).pipe(
      map(region => RegionUtils.getDisplayName(region)),
      catchError(() => of(regionId))
    );
  }
}
