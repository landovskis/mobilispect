import {
  FEED_MANAGEMENT_CONSTANTS,
  FeedSpecType,
  FeedStatus,
  ImportStatus,
  TriggerType,
  TypeGuards,
} from './index';

describe('TypeGuards', () => {
  it('validates metropolitan regions', () => {
    const valid = {
      regionOnestopId: 'r-1',
      name: 'Test Region',
      autoUpdateEnabled: true,
      feedCount: 3,
    };
    expect(TypeGuards.isMetropolitanRegion(valid)).toBeTrue();
    expect(
      TypeGuards.isMetropolitanRegion({ ...valid, feedCount: '3' }),
    ).toBeFalse();
  });

  it('validates feeds', () => {
    const valid = {
      feedOnestopId: 'f-1',
      regionOnestopId: 'r-1',
      name: 'Feed',
      specType: FeedSpecType.GTFS,
      status: FeedStatus.ACTIVE,
    };
    expect(TypeGuards.isFeed(valid)).toBeTrue();
    expect(TypeGuards.isFeed({ ...valid, specType: 'other' })).toBeFalse();
    expect(TypeGuards.isFeed({ ...valid, status: 'other' })).toBeFalse();
  });

  it('validates feed imports', () => {
    const valid = {
      id: 'imp-1',
      feedOnestopId: 'f-1',
      status: ImportStatus.RUNNING,
      triggerType: TriggerType.MANUAL,
    };
    expect(TypeGuards.isFeedImport(valid)).toBeTrue();
    expect(TypeGuards.isFeedImport({ ...valid, status: 'other' })).toBeFalse();
    expect(
      TypeGuards.isFeedImport({ ...valid, triggerType: 'other' }),
    ).toBeFalse();
  });

  it('validates import progress', () => {
    const valid = {
      progressPercentage: 50,
      totalSteps: 10,
      currentStep: 'Processing',
    };
    expect(TypeGuards.isImportProgress(valid)).toBeTrue();
    expect(
      TypeGuards.isImportProgress({ ...valid, totalSteps: '10' }),
    ).toBeFalse();
  });

  it('validates progress update messages', () => {
    const valid = {
      importId: 'imp-1',
      feedOnestopId: 'f-1',
      progressPercentage: 20,
      currentStep: 'Queued',
    };
    expect(TypeGuards.isProgressUpdateMessage(valid)).toBeTrue();
    expect(
      TypeGuards.isProgressUpdateMessage({ ...valid, importId: 123 }),
    ).toBeFalse();
  });

  it('validates system alert messages', () => {
    const valid = {
      type: 'warning',
      title: 'Heads up',
      message: 'Something happened',
    };
    expect(TypeGuards.isSystemAlertMessage(valid)).toBeTrue();
    expect(
      TypeGuards.isSystemAlertMessage({ ...valid, type: 'notice' }),
    ).toBeFalse();
    expect(
      TypeGuards.isSystemAlertMessage({ ...valid, title: 12 }),
    ).toBeFalse();
  });
});

describe('FEED_MANAGEMENT_CONSTANTS', () => {
  it('exposes known endpoints and UI defaults', () => {
    expect(FEED_MANAGEMENT_CONSTANTS.API_ENDPOINTS.FEEDS).toContain(
      '/api/feeds/feeds',
    );
    expect(FEED_MANAGEMENT_CONSTANTS.UI.DEFAULT_PAGE_SIZE).toBe(20);
  });
});
