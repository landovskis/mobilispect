import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AgencyService } from '../services/agency.service';

@Injectable({ providedIn: 'root' })
export class AgencyBreadcrumbResolver implements Resolve<string> {
  constructor(private readonly agencyService: AgencyService) {}

  resolve(route: ActivatedRouteSnapshot): Observable<string> {
    const agencyId = route.paramMap.get('agencyId');
    if (!agencyId) return of('Agencies');

    return this.agencyService.getAgency(agencyId).pipe(
      map(agency => agency.name || this.humanize(agencyId)),
      catchError(() => of(this.humanize(agencyId)))
    );
  }

  private humanize(rawId: string): string {
    const decoded = decodeURIComponent(rawId);
    const cleaned = decoded.replace(/^o-/, '').replace(/~/g, ' ');
    return cleaned
      .split(/[\s-]+/)
      .filter(Boolean)
      .map(piece => piece.charAt(0).toUpperCase() + piece.slice(1))
      .join(' ');
  }
}
