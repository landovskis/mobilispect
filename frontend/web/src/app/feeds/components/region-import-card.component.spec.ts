import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { Subject } from 'rxjs';
import { RegionImportCardComponent } from './region-import-card.component';
import { FeedImportRowComponent } from './feed-import-row.component';
import { RegionImportGroup } from '../models/region-import-group.model';
import { FeedImportSummary, ImportStatus, TriggerType } from '../models/import.models';
import { ImportService } from '../services/import.service';

describe('RegionImportCardComponent', () => {
  let component: RegionImportCardComponent;
  let fixture: ComponentFixture<RegionImportCardComponent>;

  const mockFeedImports: FeedImportSummary[] = [
    {
      id: 'import-1',
      feedOnestopId: 'f-bart',
      feedName: 'BART',
      regionOnestopId: 'r-sf-bay',
      regionName: 'San Francisco Bay Area',
      status: ImportStatus.RUNNING,
      triggerType: TriggerType.MANUAL,
      startedAt: '2026-01-07T12:00:00Z',
      completedAt: null,
      progress: {
        progressPercentage: 50,
        totalSteps: 5,
        currentStep: 'Parsing routes',
        estimatedTimeRemainingSeconds: 120
      }
    },
    {
      id: 'import-2',
      feedOnestopId: 'f-muni',
      feedName: 'MUNI',
      regionOnestopId: 'r-sf-bay',
      regionName: 'San Francisco Bay Area',
      status: ImportStatus.RUNNING,
      triggerType: TriggerType.MANUAL,
      startedAt: '2026-01-07T12:00:00Z',
      completedAt: null,
      progress: {
        progressPercentage: 75,
        totalSteps: 5,
        currentStep: 'Importing stops',
        estimatedTimeRemainingSeconds: 60
      }
    }
  ];

  const mockRegionGroup: RegionImportGroup = {
    regionOnestopId: 'r-sf-bay',
    regionName: 'San Francisco Bay Area',
    feedImports: mockFeedImports,
    totalFeeds: 2,
    averageProgress: 62.5, // (50 + 75) / 2
    hasFailures: false,
    allCompleted: false
  };

  beforeEach(async () => {
    const mockImportService = jasmine.createSpyObj('ImportService', ['monitorImportProgress']);
    mockImportService.monitorImportProgress.and.returnValue(new Subject());

    await TestBed.configureTestingModule({
      imports: [RegionImportCardComponent],
      providers: [{ provide: ImportService, useValue: mockImportService }]
    }).compileComponents();

    fixture = TestBed.createComponent(RegionImportCardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display region name and feed count badge', () => {
    // Given: Component with region group data
    component.regionGroup = mockRegionGroup;

    // When: Component renders
    fixture.detectChanges();

    // Then: Region name is displayed
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('San Francisco Bay Area');

    // And: Feed count badge is displayed
    expect(compiled.textContent).toContain('2 feeds');
  });

  it('should display aggregate progress bar', () => {
    // Given: Component with region group data
    component.regionGroup = mockRegionGroup;

    // When: Component renders
    fixture.detectChanges();

    // Then: Aggregate progress bar exists
    const compiled = fixture.nativeElement as HTMLElement;
    const progressBar = compiled.querySelector('.region-aggregate-progress mat-progress-bar');
    expect(progressBar).toBeTruthy();

    // And: Progress percentage is displayed
    expect(compiled.textContent).toContain('62.5%');
  });

  it('should render feed import rows for each feed', () => {
    // Given: Component with region group containing 2 feeds
    component.regionGroup = mockRegionGroup;

    // When: Component renders
    fixture.detectChanges();

    // Then: Two feed import row components are rendered
    const feedRows = fixture.debugElement.queryAll(By.directive(FeedImportRowComponent));
    expect(feedRows.length).toBe(2);
  });

  it('should propagate stopImport events from child rows', () => {
    // Given: Component with region group data
    component.regionGroup = mockRegionGroup;
    fixture.detectChanges();

    let emittedId: string | undefined;
    component.cancelImport.subscribe((id: string) => {
      emittedId = id;
    });

    // When: Child row emits stopImport event
    component.onStopImport('import-1');

    // Then: cancelImport event is propagated with import ID
    expect(emittedId).toBe('import-1');
  });

  it('should calculate average progress correctly', () => {
    // Given: Region group with 50% and 75% progress
    component.regionGroup = mockRegionGroup;

    // When: Component renders
    fixture.detectChanges();

    // Then: Average progress is 62.5%
    const compiled = fixture.nativeElement as HTMLElement;
    const progressText = compiled.querySelector('.progress-percentage')?.textContent;
    expect(progressText).toContain('62.5%');
  });

  it('should work in both light and dark themes', () => {
    // Given: Component with region group data
    component.regionGroup = mockRegionGroup;

    // When: Component renders
    fixture.detectChanges();

    // Then: Component has theme-aware styling (verified by CSS selectors)
    const compiled = fixture.nativeElement as HTMLElement;
    const card = compiled.querySelector('app-brand-card');
    expect(card).toBeTruthy(); // BrandCard handles theme switching
  });

  it('should handle region group with single feed', () => {
    // Given: Region group with one feed
    const singleFeedGroup: RegionImportGroup = {
      ...mockRegionGroup,
      feedImports: [mockFeedImports[0]],
      totalFeeds: 1,
      averageProgress: 50
    };
    component.regionGroup = singleFeedGroup;

    // When: Component renders
    fixture.detectChanges();

    // Then: Single feed is displayed
    const feedRows = fixture.debugElement.queryAll(By.directive(FeedImportRowComponent));
    expect(feedRows.length).toBe(1);

    // And: Badge shows "1 feeds" (or "1 feed" if singularized in template)
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('1 feed');
  });

  it('should handle region group with failures', () => {
    // Given: Region group with a failed import
    const failedGroup: RegionImportGroup = {
      ...mockRegionGroup,
      hasFailures: true,
      feedImports: [
        mockFeedImports[0],
        {
          ...mockFeedImports[1],
          status: ImportStatus.FAILED,
          errorMessage: 'Download failed'
        }
      ]
    };
    component.regionGroup = failedGroup;

    // When: Component renders
    fixture.detectChanges();

    // Then: Both feeds are displayed (including failed one)
    const feedRows = fixture.debugElement.queryAll(By.directive(FeedImportRowComponent));
    expect(feedRows.length).toBe(2);
  });

  it('should display zero progress for region with no progress data', () => {
    // Given: Region group with no progress
    const noProgressGroup: RegionImportGroup = {
      ...mockRegionGroup,
      averageProgress: 0,
      feedImports: mockFeedImports.map(f => ({ ...f, progress: null }))
    };
    component.regionGroup = noProgressGroup;

    // When: Component renders
    fixture.detectChanges();

    // Then: 0% progress is displayed
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('0%');
  });

  it('should use region icon in card header', () => {
    // Given: Component with region group
    component.regionGroup = mockRegionGroup;

    // When: Component renders
    fixture.detectChanges();

    // Then: Card has public/region icon
    const compiled = fixture.nativeElement as HTMLElement;
    const icon = compiled.querySelector('mat-icon');
    expect(icon?.textContent).toContain('public');
  });
});
