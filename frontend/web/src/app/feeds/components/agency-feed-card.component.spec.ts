import { AgencyFeedCardComponent } from './agency-feed-card.component';
import { Feed, FeedSpecType, FeedStatus } from '../models';
import { AgencyFeedGroup } from '../models/agency-feed-group.model';

describe('AgencyFeedCardComponent', () => {
  const baseFeed: Feed = {
    feedOnestopId: 'f-test',
    regionOnestopId: 'r-test',
    name: 'Test Feed',
    specType: FeedSpecType.GTFS,
    downloadUrl: 'https://example.com/gtfs.zip',
    currentVersionSha1: null,
    lastCheckedAt: null,
    lastUpdatedAt: null,
    status: FeedStatus.ACTIVE,
    hasAuthentication: false,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  const buildGroup = (feeds: Feed[]): AgencyFeedGroup => ({
    agencyId: 'a-1',
    agencyName: 'Test Agency',
    feeds,
    primaryFeed: feeds[0],
    feedsByType: {
      gtfs: feeds.filter((feed) => feed.specType === FeedSpecType.GTFS).length,
      gtfsRt: feeds.filter((feed) => feed.specType === FeedSpecType.GTFS_RT)
        .length,
    },
    hasActiveFeeds: feeds.some((feed) => feed.status === FeedStatus.ACTIVE),
    hasAuthentication: feeds.some((feed) => feed.hasAuthentication),
    lastUpdatedAt:
      feeds
        .map((feed) => feed.lastUpdatedAt)
        .filter((date) => date !== null)
        .sort()
        .reverse()[0] ?? null,
  });

  it('emits single active feed on import', () => {
    const component = new AgencyFeedCardComponent();
    const activeFeed = { ...baseFeed, feedOnestopId: 'f-1' };
    component.agencyGroup = buildGroup([activeFeed]);

    let emitted: Feed | null = null;
    component.importFeed.subscribe((feed) => (emitted = feed));

    component.onImport();

    if (!emitted) {
      fail('Expected import feed emission.');
      return;
    }
    const result = emitted as unknown as Feed;
    expect(result.feedOnestopId).toBe('f-1');
  });

  it('emits all active feeds when more than one is active', () => {
    const component = new AgencyFeedCardComponent();
    const feeds = [
      { ...baseFeed, feedOnestopId: 'f-1' },
      { ...baseFeed, feedOnestopId: 'f-2' },
    ];
    component.agencyGroup = buildGroup(feeds);

    let emitted: Feed[] | null = null;
    component.importAllFeeds.subscribe((value) => (emitted = value));

    component.onImport();

    if (!emitted) {
      fail('Expected import-all emission.');
      return;
    }
    const result = emitted as unknown as Feed[];
    expect(result.length).toBe(2);
    expect(result[0].feedOnestopId).toBe('f-1');
  });

  it('does not emit when there are no active feeds', () => {
    const component = new AgencyFeedCardComponent();
    const feeds = [
      { ...baseFeed, feedOnestopId: 'f-1', status: FeedStatus.INACTIVE },
    ];
    component.agencyGroup = buildGroup(feeds);

    const emitSpy = spyOn(component.importFeed, 'emit');
    const emitAllSpy = spyOn(component.importAllFeeds, 'emit');

    component.onImport();

    expect(emitSpy).not.toHaveBeenCalled();
    expect(emitAllSpy).not.toHaveBeenCalled();
  });

  it('builds tooltips based on active feed count', () => {
    const component = new AgencyFeedCardComponent();

    component.agencyGroup = buildGroup([
      { ...baseFeed, status: FeedStatus.INACTIVE },
    ]);
    expect(component.getImportTooltip()).toBe('No active feeds available');

    component.agencyGroup = buildGroup([{ ...baseFeed, name: 'Metro Feed' }]);
    expect(component.getImportTooltip()).toBe('Import Metro Feed');

    component.agencyGroup = buildGroup([
      { ...baseFeed, feedOnestopId: 'f-1' },
      { ...baseFeed, feedOnestopId: 'f-2' },
    ]);
    expect(component.getImportTooltip()).toBe(
      'Import all 2 active feeds from this agency',
    );
  });

  it('counts active feeds', () => {
    const component = new AgencyFeedCardComponent();
    component.agencyGroup = buildGroup([
      { ...baseFeed, feedOnestopId: 'f-1' },
      { ...baseFeed, feedOnestopId: 'f-2', status: FeedStatus.INACTIVE },
    ]);

    expect(component.getActiveFeedsCount()).toBe(1);
  });
});
