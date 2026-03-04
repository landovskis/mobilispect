import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChangeDetectorRef } from '@angular/core';
import { of, Subject } from 'rxjs';
import { FeedImportRowComponent } from './feed-import-row.component';
import { ImportService } from '../services/import.service';
import {
  FeedImportSummary,
  ImportStatus,
  TriggerType,
  ImportProgress,
} from '../models/import.models';
import { vi } from 'vitest';

describe('FeedImportRowComponent', () => {
  let component: FeedImportRowComponent;
  let fixture: ComponentFixture<FeedImportRowComponent>;
  let mockImportService: ImportService;

  const mockFeedImport: FeedImportSummary = {
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
      estimatedTimeRemainingSeconds: 120,
    },
  };

  const mockProgress: ImportProgress = {
    progressPercentage: 75,
    totalSteps: 5,
    currentStep: 'Importing stops',
    estimatedTimeRemainingSeconds: 60,
  };

  beforeEach(async () => {
    mockImportService = {
      monitorImportProgress: vi.fn(),
    } as unknown as ImportService;

    // Default: return observable that never emits (for tests that don't care about progress)
    vi.mocked(mockImportService.monitorImportProgress).mockReturnValue(
      new Subject(),
    );

    await TestBed.configureTestingModule({
      imports: [FeedImportRowComponent],
      providers: [{ provide: ImportService, useValue: mockImportService }],
    }).compileComponents();

    fixture = TestBed.createComponent(FeedImportRowComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display feed name, status badge, and progress bar', () => {
    // Given: Component with feed import data
    component.feedImport = mockFeedImport;

    // When: Component initializes
    fixture.detectChanges();

    // Then: Feed name and status are displayed
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('BART');
    expect(compiled.querySelector('.status-badge')).toBeTruthy();
    expect(compiled.querySelector('mat-progress-bar')).toBeTruthy();
  });

  it('should emit stopImport event when stop button clicked', () => {
    // Given: Component with feed import data
    component.feedImport = mockFeedImport;
    fixture.detectChanges();

    let emittedId: string | undefined;
    component.stopImport.subscribe((id: string) => {
      emittedId = id;
    });

    // When: Stop button is clicked
    component.onStop();

    // Then: stopImport event is emitted with import ID
    expect(emittedId).toBe('import-1');
  });

  it('should update progress in real-time via subscription', () => {
    // Given: Component with feed import and progress observable
    component.feedImport = mockFeedImport;
    const progressSubject = new Subject<ImportProgress>();
    vi.mocked(mockImportService.monitorImportProgress).mockReturnValue(
      progressSubject.asObservable(),
    );

    // Spy on the actual ChangeDetectorRef
    const cdrSpy = vi
      .spyOn(component['cdr'] as unknown as ChangeDetectorRef, 'markForCheck')
      .mockImplementation(() => {});

    // When: Component initializes
    fixture.detectChanges();

    // Then: Initial progress is set
    expect(component.currentProgress).toEqual(mockFeedImport.progress);

    // When: New progress is emitted
    progressSubject.next(mockProgress);

    // Then: Current progress is updated and change detection is triggered
    expect(component.currentProgress).toEqual(mockProgress);
    expect(cdrSpy).toHaveBeenCalled();
  });

  it('should format time remaining correctly', () => {
    // Given: Component instance
    component.feedImport = mockFeedImport;
    component.currentProgress = mockProgress;

    // When: Formatting 60 seconds
    const result60 = component.formatTimeRemaining();

    // Then: Formatted as "1m 0s"
    expect(result60).toBe('1m 0s');

    // When: Formatting 125 seconds
    component.currentProgress = {
      ...mockProgress,
      estimatedTimeRemainingSeconds: 125,
    };
    const result125 = component.formatTimeRemaining();

    // Then: Formatted as "2m 5s"
    expect(result125).toBe('2m 5s');

    // When: Formatting 30 seconds
    component.currentProgress = {
      ...mockProgress,
      estimatedTimeRemainingSeconds: 30,
    };
    const result30 = component.formatTimeRemaining();

    // Then: Formatted as "30s"
    expect(result30).toBe('30s');
  });

  it('should be accessible with ARIA labels', () => {
    // Given: Component with feed import data
    component.feedImport = mockFeedImport;

    // When: Component renders
    fixture.detectChanges();

    // Then: Stop button has aria-label
    const compiled = fixture.nativeElement as HTMLElement;
    const stopButton = compiled.querySelector('button[aria-label]');
    expect(stopButton).toBeTruthy();
    expect(stopButton?.getAttribute('aria-label')).toContain(
      'Stop import for BART',
    );

    // And: Row has listitem role
    const row = compiled.querySelector('[role="listitem"]');
    expect(row).toBeTruthy();
  });

  it('should support keyboard navigation', () => {
    // Given: Component with feed import data
    component.feedImport = mockFeedImport;
    fixture.detectChanges();

    // Then: Stop button is focusable
    const compiled = fixture.nativeElement as HTMLElement;
    const stopButton = compiled.querySelector('button') as HTMLButtonElement;
    expect(stopButton).toBeTruthy();
    expect(stopButton.tabIndex).toBeGreaterThanOrEqual(0);
  });

  it('should handle error state with error message', () => {
    // Given: Failed import with error message
    const failedImport: FeedImportSummary = {
      ...mockFeedImport,
      status: ImportStatus.FAILED,
      errorMessage: 'Feed download failed',
    };
    component.feedImport = failedImport;

    // When: Component renders
    fixture.detectChanges();

    // Then: Error status is displayed
    const compiled = fixture.nativeElement as HTMLElement;
    const statusBadge = compiled.querySelector('.status-badge');
    expect(statusBadge?.classList.contains('status-failed')).toBe(true);
  });

  it('should show indeterminate progress when no progress data', () => {
    // Given: Import with no progress
    const pendingImport: FeedImportSummary = {
      ...mockFeedImport,
      status: ImportStatus.PENDING,
      progress: null,
    };
    component.feedImport = pendingImport;
    component.currentProgress = null;

    // When: Component renders
    fixture.detectChanges();

    // Then: Progress bar is in indeterminate mode
    const compiled = fixture.nativeElement as HTMLElement;
    const progressBar = compiled.querySelector('mat-progress-bar');
    expect(progressBar?.getAttribute('mode')).toBe('indeterminate');
  });

  it('should display current step when available', () => {
    // Given: Component with progress data
    component.feedImport = { ...mockFeedImport, progress: mockProgress };

    // When: Component renders
    fixture.detectChanges();

    // Then: Current step is displayed
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Importing stops');
  });

  it('should cleanup subscriptions on destroy', () => {
    // Given: Component with active subscription
    component.feedImport = mockFeedImport;
    const progressSubject = new Subject<ImportProgress>();
    vi.mocked(mockImportService.monitorImportProgress).mockReturnValue(
      progressSubject.asObservable(),
    );

    // Spy on the actual ChangeDetectorRef
    const cdrSpy = vi
      .spyOn(component['cdr'] as unknown as ChangeDetectorRef, 'markForCheck')
      .mockImplementation(() => {});

    // When: Component initializes
    fixture.detectChanges();

    // And: Component is destroyed
    component.ngOnDestroy();

    // Then: destroy$ subject is completed (subscription cleanup)
    // We can verify this by trying to emit - destroyed subjects won't trigger updates
    const initialCallCount = cdrSpy.mock.calls.length;
    progressSubject.next(mockProgress);
    expect(cdrSpy.mock.calls.length).toBe(initialCallCount);
  });

  describe('getStatusClass', () => {
    it('should return correct CSS class for each status', () => {
      component.feedImport = mockFeedImport;

      // RUNNING status
      component.feedImport.status = ImportStatus.RUNNING;
      expect(component.getStatusClass()).toBe('status-running');

      // PENDING status
      component.feedImport.status = ImportStatus.PENDING;
      expect(component.getStatusClass()).toBe('status-pending');

      // COMPLETED status
      component.feedImport.status = ImportStatus.COMPLETED;
      expect(component.getStatusClass()).toBe('status-completed');

      // FAILED status
      component.feedImport.status = ImportStatus.FAILED;
      expect(component.getStatusClass()).toBe('status-failed');

      // CANCELLED status
      component.feedImport.status = ImportStatus.CANCELLED;
      expect(component.getStatusClass()).toBe('status-cancelled');
    });
  });
});
