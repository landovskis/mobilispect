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

    const cachedRegion = this.regionService.getCachedRegion(regionId);
    if (cachedRegion) {
      return of(RegionUtils.getDisplayName(cachedRegion));
    }

    return this.regionService.getRegion(regionId).pipe(
      map(region => RegionUtils.getDisplayName(region)),
      catchError(() => of(this.humanizeRegionId(regionId)))
    );
  }

  private humanizeRegionId(regionId: string): string {
    const slug = regionId.replace(/^r-/, '');
    const parts = slug.split('-').filter(Boolean);

    if (parts.length >= 3) {
      const city = parts.slice(0, -2).join(' ');
      const adm1 = parts[parts.length - 2];
      const country = parts[parts.length - 1];
      return [city, adm1, country].map(part => this.capitalizeWord(part)).join(', ');
    }

    return parts.map(part => this.capitalizeWord(part)).join(' ') || regionId;
  }

  private capitalizeWord(word: string): string {
    if (!word) return '';
    return word.charAt(0).toUpperCase() + word.slice(1);
  }
}
