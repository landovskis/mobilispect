import { AgencyFeedGroup, FeedGroupingUtils } from './agency-feed-group.model';
import { Feed, FeedSpecType, FeedStatus } from './region.models';

describe('FeedGroupingUtils', () => {
  const buildFeed = (overrides: Partial<Feed> = {}): Feed => ({
    feedOnestopId: 'f-9q5-bart',
    regionOnestopId: 'r-test',
    name: 'Bay Area Rapid Transit GTFS',
    specType: FeedSpecType.GTFS,
    downloadUrl: 'https://example.com/gtfs.zip',
    currentVersionSha1: null,
    lastCheckedAt: null,
    lastUpdatedAt: '2024-06-01T00:00:00Z',
    status: FeedStatus.ACTIVE,
    hasAuthentication: false,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  });

  it('extracts agency ids and names', () => {
    expect(FeedGroupingUtils.extractAgencyId('f-9q5-bart')).toBe('bart');
    expect(FeedGroupingUtils.extractAgencyId('f-dr5-nyct~rt')).toBe('nyct');
    expect(FeedGroupingUtils.extractAgencyId('invalid')).toBe('invalid');

    expect(FeedGroupingUtils.extractAgencyName('Metro Transit GTFS')).toBe(
      'Metro Transit',
    );
    expect(FeedGroupingUtils.extractAgencyName('Metro Transit - RT')).toBe(
      'Metro Transit',
    );
    expect(FeedGroupingUtils.extractAgencyName('Metro Transit Realtime')).toBe(
      'Metro Transit Realtime',
    );
  });

  it('groups feeds by agency and computes metadata', () => {
    const feeds = [
      buildFeed({
        feedOnestopId: 'f-9q5-bart',
        name: 'BART GTFS',
        specType: FeedSpecType.GTFS,
        hasAuthentication: true,
        lastUpdatedAt: '2024-06-02T00:00:00Z',
      }),
      buildFeed({
        feedOnestopId: 'f-9q5-bart~rt',
        name: 'BART Realtime',
        specType: FeedSpecType.GTFS_RT,
        status: FeedStatus.INACTIVE,
        lastUpdatedAt: '2024-05-30T00:00:00Z',
      }),
      buildFeed({
        feedOnestopId: 'f-9q8-actransit',
        name: 'AC Transit GTFS',
        specType: FeedSpecType.GTFS,
        status: FeedStatus.ACTIVE,
      }),
    ];

    const groups = FeedGroupingUtils.groupFeedsByAgency(feeds);
    expect(groups.length).toBe(2);

    const bartGroup = groups.find((group) => group.agencyId === 'bart');
    expect(bartGroup).toBeDefined();
    expect(bartGroup!.feedsByType.gtfs).toBe(1);
    expect(bartGroup!.feedsByType.gtfsRt).toBe(1);
    expect(bartGroup!.hasAuthentication).toBeTrue();
    expect(bartGroup!.hasActiveFeeds).toBeTrue();
    expect(bartGroup!.primaryFeed.specType).toBe(FeedSpecType.GTFS);
    expect(bartGroup!.lastUpdatedAt).toBe('2024-06-02T00:00:00Z');
  });

  it('sorts groups alphabetically and maps feed type metadata', () => {
    const buildGroup = (agencyName: string): AgencyFeedGroup => ({
      agencyName,
      agencyId: agencyName.toLowerCase(),
      feeds: [],
      primaryFeed: buildFeed(),
      hasActiveFeeds: false,
      hasAuthentication: false,
      lastUpdatedAt: null,
      feedsByType: { gtfs: 0, gtfsRt: 0 },
    });
    const groups = FeedGroupingUtils.sortAgencyGroups([
      buildGroup('Zeta Transit'),
      buildGroup('Alpha Transit'),
    ]);

    expect(groups[0].agencyName).toBe('Alpha Transit');

    expect(FeedGroupingUtils.getFeedTypeLabel(FeedSpecType.GTFS)).toBe(
      'Static',
    );
    expect(FeedGroupingUtils.getFeedTypeLabel('UNKNOWN' as FeedSpecType)).toBe(
      'UNKNOWN',
    );
    expect(FeedGroupingUtils.getFeedTypeIcon(FeedSpecType.GTFS_RT)).toBe(
      'real_time_tracking',
    );
    expect(FeedGroupingUtils.getFeedTypeIcon('UNKNOWN' as FeedSpecType)).toBe(
      'feed',
    );
    expect(
      FeedGroupingUtils.getFeedTypeColorClass('UNKNOWN' as FeedSpecType),
    ).toBe('feed-type-default');
  });

  it('handles agencies with inactive feeds and missing timestamps', () => {
    const feeds = [
      buildFeed({
        feedOnestopId: 'f-9q5-bart~rt',
        name: 'BART Realtime',
        specType: FeedSpecType.GTFS_RT,
        status: FeedStatus.INACTIVE,
        lastUpdatedAt: null,
        hasAuthentication: false,
      }),
      buildFeed({
        feedOnestopId: 'f-9q5-bart',
        name: 'BART GTFS',
        specType: FeedSpecType.GTFS,
        status: FeedStatus.INACTIVE,
        lastUpdatedAt: null,
        hasAuthentication: false,
      }),
    ];

    const group = FeedGroupingUtils.groupFeedsByAgency(feeds)[0];
    expect(group.hasActiveFeeds).toBeFalse();
    expect(group.hasAuthentication).toBeFalse();
    expect(group.lastUpdatedAt).toBeNull();
    expect(group.primaryFeed.specType).toBe(FeedSpecType.GTFS);
  });
});
