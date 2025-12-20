import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { RegionDetailComponent } from './region-detail.component';
import { RegionService } from '../../feeds/services/region.service';
import { MetropolitanRegionDetail } from '../../feeds/models/region.models';
import { AgencyDTO } from '../../transit-frequency/models/agency.model';
import {AgencyService} from "../../agencies/services/agency.service";

describe('RegionDetailComponent', () => {
  let component: RegionDetailComponent;
  let fixture: ComponentFixture<RegionDetailComponent>;
  let regionServiceSpy: jasmine.SpyObj<RegionService>;
  let agencyServiceSpy: jasmine.SpyObj<AgencyService>;
  let activatedRouteSpy: jasmine.SpyObj<ActivatedRoute>;

  const mockRegion: MetropolitanRegionDetail = {
    regionOnestopId: 'r-test-region',
    name: 'Test Region',
    adm0Name: 'Canada',
    adm1Name: 'Quebec',
    autoUpdateEnabled: true,
    feedCount: 5,
    lastCheckAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    feeds: []
  };

  const mockAgencies: AgencyDTO[] = [
    {
      id: 'agency-1',
      name: 'Transit Agency 1',
      feedOnestopId: 'f-1',
      regionIds: ['r-test-region'],
      routeCount: 10,
      activeRouteCount: 8,
      routesByType: { BUS: 10 }
    },
    {
      id: 'agency-2',
      name: 'Transit Agency 2',
      feedOnestopId: 'f-2',
      regionIds: ['r-test-region'],
      routeCount: 8,
      activeRouteCount: 6,
      routesByType: { RAIL: 8 }
    }
  ];

  beforeEach(async () => {
    const regionSpy = jasmine.createSpyObj('RegionService', ['getRegion']);
    const agencySpy = jasmine.createSpyObj('AgencyService', ['listAgencies']);
    const routeSpy = jasmine.createSpyObj('ActivatedRoute', [], {
      snapshot: {
        paramMap: {
          get: (key: string) => key === 'regionId' ? 'r-test-region' : null
        }
      }
    });

    await TestBed.configureTestingModule({
      imports: [RegionDetailComponent],
      providers: [
        { provide: RegionService, useValue: regionSpy },
        { provide: AgencyService, useValue: agencySpy },
        { provide: ActivatedRoute, useValue: routeSpy }
      ]
    }).compileComponents();

    regionServiceSpy = TestBed.inject(RegionService) as jasmine.SpyObj<RegionService>;
    agencyServiceSpy = TestBed.inject(AgencyService) as jasmine.SpyObj<AgencyService>;
    activatedRouteSpy = TestBed.inject(ActivatedRoute) as jasmine.SpyObj<ActivatedRoute>;

    fixture = TestBed.createComponent(RegionDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load region data and agencies on init', () => {
    regionServiceSpy.getRegion.and.returnValue(of(mockRegion));
    agencyServiceSpy.listAgencies.and.returnValue(of({
      content: mockAgencies,
      totalElements: 2,
      totalPages: 1
    }));

    fixture.detectChanges();

    expect(regionServiceSpy.getRegion).toHaveBeenCalledWith('r-test-region');
    expect(agencyServiceSpy.listAgencies).toHaveBeenCalledWith(0, 100, 'r-test-region');
  });

  it('should display region information', () => {
    regionServiceSpy.getRegion.and.returnValue(of(mockRegion));
    agencyServiceSpy.listAgencies.and.returnValue(of({
      content: mockAgencies,
      totalElements: 2,
      totalPages: 1
    }));

    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Test Region');
    expect(compiled.textContent).toContain('2'); // totalAgencies
    expect(compiled.textContent).toContain('Transit Agencies');
    expect(compiled.textContent).toContain('Active Routes');
  });

  it('should display agencies list when agencies are available', () => {
    regionServiceSpy.getRegion.and.returnValue(of(mockRegion));
    agencyServiceSpy.listAgencies.and.returnValue(of({
      content: mockAgencies,
      totalElements: 2,
      totalPages: 1
    }));

    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Agencies');
    expect(compiled.textContent).toContain('Transit agencies serving this region');
    const agencyCards = compiled.querySelectorAll('app-agency-card');
    expect(agencyCards.length).toBe(2);
  });

  it('should display message when no agencies are available', () => {
    regionServiceSpy.getRegion.and.returnValue(of(mockRegion));
    agencyServiceSpy.listAgencies.and.returnValue(of({
      content: [],
      totalElements: 0,
      totalPages: 0
    }));

    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('No agencies found for this region');
  });

  it('should not call services when regionId is missing', () => {
    const routeWithoutId = jasmine.createSpyObj('ActivatedRoute', [], {
      snapshot: {
        paramMap: {
          get: () => null
        }
      }
    });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [RegionDetailComponent],
      providers: [
        { provide: RegionService, useValue: regionServiceSpy },
        { provide: AgencyService, useValue: agencyServiceSpy },
        { provide: ActivatedRoute, useValue: routeWithoutId }
      ]
    });

    fixture = TestBed.createComponent(RegionDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(regionServiceSpy.getRegion).not.toHaveBeenCalled();
    expect(agencyServiceSpy.listAgencies).not.toHaveBeenCalled();
  });
});
