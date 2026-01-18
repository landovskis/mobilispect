import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  RegionImportStatus,
  RegionImportStatusResponse,
} from '../models/import.models';
import { RegionImportStatusComponent } from './region-import-status.component';

const baseStatus: RegionImportStatusResponse = {
  regionImportId: 'imp-1',
  regionOnestopId: 'r-test',
  status: RegionImportStatus.RUNNING,
  totalFeeds: 10,
  startedCount: 4,
  completedCount: 3,
  failedCount: 1,
  skippedCount: 1,
  startedAt: '2024-01-01T00:00:00Z',
  completedAt: null,
  errorMessage: null,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

describe('RegionImportStatusComponent', () => {
  let component: RegionImportStatusComponent;
  let fixture: ComponentFixture<RegionImportStatusComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegionImportStatusComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(RegionImportStatusComponent);
    component = fixture.componentInstance;
  });

  it('renders an idle state when status is missing', () => {
    component.status = null;
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.badge');
    expect(badge.textContent).toContain('Idle');
  });

  it('shows progress percent based on completed counts', () => {
    component.status = { ...baseStatus, completedCount: 5, failedCount: 0, skippedCount: 0 };
    fixture.detectChanges();

    expect(component.progressPercent).toBe(50);
    const progressText = fixture.nativeElement.querySelector('.progress-meta');
    expect(progressText.textContent).toContain('50%');
  });

  it('renders status label and class', () => {
    component.status = { ...baseStatus, status: RegionImportStatus.PARTIAL_SUCCESS };
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.badge');
    expect(badge.textContent).toContain('Partial');
    expect(badge.classList.contains('partial')).toBeTrue();
  });
});
