import {
  FeedImport,
  ImportStatus,
  ImportUtils,
  ProgressUtils,
  TriggerType,
} from './import.models';

describe('ImportUtils', () => {
  const baseImport: FeedImport = {
    id: 'imp-1',
    feedOnestopId: 'f-abc-test',
    administratorId: null,
    administratorUsername: null,
    triggerType: TriggerType.MANUAL,
    status: ImportStatus.PENDING,
    versionSha1: null,
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    fileSizeBytes: 1024,
    errorMessage: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  beforeEach(() => {
    jasmine.clock().install();
  });

  afterEach(() => {
    jasmine.clock().uninstall();
  });

  it('detects active and completed states', () => {
    expect(ImportUtils.isActive(baseImport)).toBeTrue();
    expect(ImportUtils.isCompleted(baseImport)).toBeFalse();

    const completed: FeedImport = {
      ...baseImport,
      status: ImportStatus.COMPLETED,
    };
    expect(ImportUtils.isActive(completed)).toBeFalse();
    expect(ImportUtils.isCompleted(completed)).toBeTrue();
  });

  it('formats status metadata', () => {
    expect(
      ImportUtils.isSuccessful({
        ...baseImport,
        status: ImportStatus.COMPLETED,
      }),
    ).toBeTrue();
    expect(
      ImportUtils.isCancellable({
        ...baseImport,
        status: ImportStatus.RUNNING,
      }),
    ).toBeTrue();
    expect(
      ImportUtils.isCancellable({ ...baseImport, status: ImportStatus.FAILED }),
    ).toBeFalse();

    expect(ImportUtils.getStatusDisplayName(ImportStatus.PENDING)).toBe(
      'Pending',
    );
    expect(ImportUtils.getStatusDisplayName(ImportStatus.RUNNING)).toBe(
      'Running',
    );
    expect(ImportUtils.getStatusDisplayName(ImportStatus.FAILED)).toBe(
      'Failed',
    );
    expect(ImportUtils.getStatusDisplayName(ImportStatus.CANCELLED)).toBe(
      'Cancelled',
    );
    expect(ImportUtils.getStatusDisplayName('other' as ImportStatus)).toBe(
      'other',
    );

    expect(ImportUtils.getStatusColorClass(ImportStatus.PENDING)).toBe(
      'chip-neutral',
    );
    expect(ImportUtils.getStatusColorClass(ImportStatus.RUNNING)).toBe(
      'chip-warning',
    );
    expect(ImportUtils.getStatusColorClass(ImportStatus.FAILED)).toBe(
      'chip-error',
    );
    expect(ImportUtils.getStatusColorClass(ImportStatus.CANCELLED)).toBe(
      'chip-neutral',
    );
    expect(ImportUtils.getStatusColorClass('other' as ImportStatus)).toBe(
      'chip-neutral',
    );

    expect(ImportUtils.getTriggerTypeDisplayName(TriggerType.AUTOMATIC)).toBe(
      'Automatic',
    );
    expect(ImportUtils.getTriggerTypeDisplayName('other' as TriggerType)).toBe(
      'other',
    );
  });

  it('formats file size and duration', () => {
    expect(ImportUtils.formatFileSize(0)).toBe('Unknown');
    expect(ImportUtils.formatFileSize(null)).toBe('Unknown');
    expect(ImportUtils.formatFileSize(1024)).toBe('1.0 KB');
    expect(ImportUtils.formatFileSize(1024 * 1024)).toBe('1.0 MB');
    expect(ImportUtils.formatFileSize(1024 * 1024 * 2.5)).toBe('2.5 MB');

    const finished: FeedImport = {
      ...baseImport,
      startedAt: '2024-01-01T00:00:00Z',
      completedAt: '2024-01-01T00:01:05Z',
    };

    expect(ImportUtils.getDuration(finished)).toBe('1m 5s');

    const now = new Date('2024-01-01T00:02:00Z');
    jasmine.clock().mockDate(now);
    const running: FeedImport = { ...baseImport, completedAt: null };

    expect(ImportUtils.getDuration(running)).toBe('2m 0s');

    const shortRun: FeedImport = {
      ...baseImport,
      startedAt: '2024-01-01T00:00:00Z',
      completedAt: '2024-01-01T00:00:30Z',
    };
    expect(ImportUtils.getDuration(shortRun)).toBe('30s');

    const longRun: FeedImport = {
      ...baseImport,
      startedAt: '2024-01-01T00:00:00Z',
      completedAt: '2024-01-01T02:10:00Z',
    };
    expect(ImportUtils.getDuration(longRun)).toBe('2h 10m');

    expect(
      ImportUtils.getDuration({ ...baseImport, startedAt: null }),
    ).toBeNull();
  });

  it('formats estimated remaining time and relative timestamps', () => {
    expect(ImportUtils.formatEstimatedTimeRemaining(0)).toBe('Unknown');
    expect(ImportUtils.formatEstimatedTimeRemaining(59)).toBe('59s');
    expect(ImportUtils.formatEstimatedTimeRemaining(61)).toBe('1m 1s');
    expect(ImportUtils.formatEstimatedTimeRemaining(3605)).toBe('1h 0m');

    const now = new Date('2024-01-01T12:00:00Z');
    jasmine.clock().mockDate(now);

    const timestamp = new Date('2024-01-01T10:30:00Z').toISOString();
    expect(ImportUtils.formatRelativeTime(timestamp)).toBe('1h ago');
    expect(
      ImportUtils.formatRelativeTime(
        new Date('2024-01-01T11:59:30Z').toISOString(),
      ),
    ).toBe('Just now');
    expect(
      ImportUtils.formatRelativeTime(
        new Date('2024-01-01T11:30:00Z').toISOString(),
      ),
    ).toBe('30m ago');
    expect(
      ImportUtils.formatRelativeTime(
        new Date('2023-12-30T12:00:00Z').toISOString(),
      ),
    ).toBe('2d ago');
  });
});

describe('ProgressUtils', () => {
  it('clamps progress values and handles failures', () => {
    expect(ProgressUtils.getProgressPercentage(null)).toBe(0);
    expect(
      ProgressUtils.getProgressPercentage({
        progressPercentage: -1,
        totalSteps: 100,
        currentStep: 'FAILED',
        estimatedTimeRemainingSeconds: null,
      }),
    ).toBe(0);

    expect(
      ProgressUtils.isProgressFailed({
        progressPercentage: -1,
        totalSteps: 100,
        currentStep: 'FAILED',
        estimatedTimeRemainingSeconds: null,
      }),
    ).toBeTrue();

    expect(
      ProgressUtils.getProgressPercentage({
        progressPercentage: 150,
        totalSteps: 100,
        currentStep: 'DONE',
        estimatedTimeRemainingSeconds: null,
      }),
    ).toBe(100);
  });

  it('returns color classes for progress thresholds', () => {
    expect(ProgressUtils.getProgressColorClass(null)).toBe('bg-gray-200');
    expect(
      ProgressUtils.getProgressColorClass({
        progressPercentage: -1,
        totalSteps: 100,
        currentStep: 'FAILED',
        estimatedTimeRemainingSeconds: null,
      }),
    ).toBe('bg-red-500');
    expect(
      ProgressUtils.getProgressColorClass({
        progressPercentage: 100,
        totalSteps: 100,
        currentStep: 'DONE',
        estimatedTimeRemainingSeconds: null,
      }),
    ).toBe('bg-green-500');
    expect(
      ProgressUtils.getProgressColorClass({
        progressPercentage: 80,
        totalSteps: 100,
        currentStep: 'RUNNING',
        estimatedTimeRemainingSeconds: null,
      }),
    ).toBe('bg-blue-500');
    expect(
      ProgressUtils.getProgressColorClass({
        progressPercentage: 55,
        totalSteps: 100,
        currentStep: 'RUNNING',
        estimatedTimeRemainingSeconds: null,
      }),
    ).toBe('bg-yellow-500');
    expect(
      ProgressUtils.getProgressColorClass({
        progressPercentage: 10,
        totalSteps: 100,
        currentStep: 'RUNNING',
        estimatedTimeRemainingSeconds: null,
      }),
    ).toBe('bg-gray-400');
  });
});
