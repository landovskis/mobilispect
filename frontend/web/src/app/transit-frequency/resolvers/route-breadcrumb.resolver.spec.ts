import { RouteBreadcrumbResolver } from './route-breadcrumb.resolver';
import { FrequencyService, RouteDto } from '../services/frequency.service';
import { firstValueFrom, of, throwError } from 'rxjs';
import { ActivatedRouteSnapshot } from '@angular/router';

describe('RouteBreadcrumbResolver', () => {
    let resolver: RouteBreadcrumbResolver;
    let frequencyServiceSpy: jasmine.SpyObj<FrequencyService>;

    const mockRoute: RouteDto = {
        id: 'r-123',
        agencyId: 'a-123',
        shortName: '15',
        longName: 'Sainte-Catherine',
        routeType: 'BUS',
        active: true
    };

    beforeEach(() => {
        frequencyServiceSpy = jasmine.createSpyObj('FrequencyService', ['getRoute']);
        resolver = new RouteBreadcrumbResolver(frequencyServiceSpy);
    });

    const createSnapshot = (routeId: string | null): ActivatedRouteSnapshot => {
        return {
            paramMap: {
                get: (key: string) => (key === 'routeId' ? routeId : null)
            }
        } as unknown as ActivatedRouteSnapshot;
    };

    it('returns generic label when route id is missing', async () => {
        const snapshot = createSnapshot(null);

        const label = await firstValueFrom(resolver.resolve(snapshot));

        expect(label).toBe('Route');
        expect(frequencyServiceSpy.getRoute).not.toHaveBeenCalled();
    });

    it('formats label with short and long name when both exist', async () => {
        frequencyServiceSpy.getRoute.and.returnValue(of(mockRoute));
        const snapshot = createSnapshot(mockRoute.id);

        const label = await firstValueFrom(resolver.resolve(snapshot));

        expect(label).toBe('15 Sainte-Catherine');
        expect(frequencyServiceSpy.getRoute).toHaveBeenCalledWith(mockRoute.id);
    });

    it('uses short name when long name is missing', async () => {
        const route = { ...mockRoute, longName: '' };
        frequencyServiceSpy.getRoute.and.returnValue(of(route));
        const snapshot = createSnapshot(mockRoute.id);

        const label = await firstValueFrom(resolver.resolve(snapshot));

        expect(label).toBe('15');
    });

    it('uses long name when short name is missing', async () => {
        const route = { ...mockRoute, shortName: null };
        frequencyServiceSpy.getRoute.and.returnValue(of(route));
        const snapshot = createSnapshot(mockRoute.id);

        const label = await firstValueFrom(resolver.resolve(snapshot));

        expect(label).toBe('Sainte-Catherine');
    });

    it('falls back to route id on api error', async () => {
        frequencyServiceSpy.getRoute.and.returnValue(throwError(() => new Error('boom')));
        const snapshot = createSnapshot(mockRoute.id);

        const label = await firstValueFrom(resolver.resolve(snapshot));

        expect(label).toBe(mockRoute.id);
    });
});
