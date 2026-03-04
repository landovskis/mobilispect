import {
  Feed,
  FeedSpecType,
  FeedStatus,
  FeedUtils,
  MetropolitanRegion,
  RegionUtils,
} from './region.models';
import { vi } from 'vitest';

describe('RegionUtils', () => {
  const baseRegion: Omit<MetropolitanRegion, 'name' | 'adm0Name' | 'adm1Name'> =
    {
      regionOnestopId: 'r-test',
      autoUpdateEnabled: false,
      feedCount: 0,
      lastCheckAt: null,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    };

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('deduplicates repeating location parts in display name', () => {
    const region: MetropolitanRegion = {
      ...baseRegion,
      name: 'Montréal, Québec, Canada',
      adm1Name: 'Québec',
      adm0Name: 'Canada',
    };

    expect(RegionUtils.getDisplayName(region)).toBe('Montréal, Québec, Canada');
  });

  it('falls back to simple join when parts are distinct', () => {
    const region: MetropolitanRegion = {
      ...baseRegion,
      name: 'San Francisco',
      adm1Name: 'California',
      adm0Name: 'United States',
    };

    expect(RegionUtils.getDisplayName(region)).toBe(
      'San Francisco, California, United States',
    );
  });

  it('filters empty parts and de-duplicates case-insensitively', () => {
    const region: MetropolitanRegion = {
      ...baseRegion,
      name: 'Toronto',
      adm1Name: 'toronto',
      adm0Name: '',
    };

    expect(RegionUtils.getDisplayName(region)).toBe('Toronto');
  });

  it('detects auto-update settings and recent checks', () => {
    const now = new Date('2024-06-01T12:00:00Z');
    vi.setSystemTime(now);

    const region: MetropolitanRegion = {
      ...baseRegion,
      name: 'Austin',
      adm1Name: 'Texas',
      adm0Name: 'United States',
      autoUpdateEnabled: true,
      lastCheckAt: new Date('2024-06-01T10:30:00Z').toISOString(),
    };

    expect(RegionUtils.hasAutoUpdatesEnabled(region)).toBe(true);
    expect(RegionUtils.hasBeenCheckedRecently(region)).toBe(true);
  });

  it('handles missing or stale check timestamps', () => {
    expect(
      RegionUtils.hasBeenCheckedRecently({
        ...baseRegion,
        name: 'Nowhere',
        adm1Name: null,
        adm0Name: null,
        lastCheckAt: null,
      }),
    ).toBe(false);

    vi.setSystemTime(new Date('2024-06-01T12:00:00Z'));
    const oldCheck: MetropolitanRegion = {
      ...baseRegion,
      name: 'Old',
      adm1Name: 'State',
      adm0Name: 'Country',
      lastCheckAt: new Date('2024-05-30T12:00:00Z').toISOString(),
    };

    expect(RegionUtils.hasBeenCheckedRecently(oldCheck)).toBe(false);
  });

  it('formats last check time for different ranges', () => {
    const now = new Date('2024-06-02T12:00:00Z');
    vi.setSystemTime(now);

    const region: MetropolitanRegion = {
      ...baseRegion,
      name: 'Phoenix',
      adm1Name: 'Arizona',
      adm0Name: 'United States',
      lastCheckAt: new Date('2024-06-02T11:30:00Z').toISOString(),
    };

    expect(RegionUtils.formatLastCheck(region)).toBe('Less than an hour ago');

    const oneDayOld: MetropolitanRegion = {
      ...region,
      lastCheckAt: new Date('2024-06-01T08:00:00Z').toISOString(),
    };

    expect(RegionUtils.formatLastCheck(oneDayOld)).toBe('1 days ago');

    const fewHoursOld: MetropolitanRegion = {
      ...region,
      lastCheckAt: new Date('2024-06-02T08:00:00Z').toISOString(),
    };
    expect(RegionUtils.formatLastCheck(fewHoursOld)).toBe('4 hours ago');

    expect(RegionUtils.formatLastCheck({ ...region, lastCheckAt: null })).toBe(
      'Never checked',
    );
  });
});

describe('FeedUtils', () => {
  const baseFeed: Feed = {
    feedOnestopId: 'f-abc-test',
    regionOnestopId: 'r-test',
    name: 'Metro Transit GTFS',
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

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('evaluates availability and feed type helpers', () => {
    expect(FeedUtils.isAvailableForImport(baseFeed)).toBe(true);
    expect(FeedUtils.isStaticFeed(baseFeed)).toBe(true);
    expect(FeedUtils.isRealTimeFeed(baseFeed)).toBe(false);
  });

  it('maps display names and status metadata', () => {
    expect(FeedUtils.getDisplayName(baseFeed)).toBe('Metro Transit GTFS');
    expect(FeedUtils.getSpecTypeDisplayName(FeedSpecType.GTFS_RT)).toBe(
      'GTFS Realtime',
    );
    expect(FeedUtils.getStatusDisplayName(FeedStatus.ERROR)).toBe('Error');
    expect(FeedUtils.getStatusColorClass(FeedStatus.INACTIVE)).toBe(
      'chip-neutral',
    );
    expect(FeedUtils.getSpecTypeDisplayName('other' as FeedSpecType)).toBe(
      'other',
    );
    expect(FeedUtils.getStatusDisplayName('other' as FeedStatus)).toBe('other');
    expect(FeedUtils.getStatusColorClass('other' as FeedStatus)).toBe(
      'chip-neutral',
    );
  });

  it('checks recent updates and formats timestamps', () => {
    const now = new Date('2024-06-10T12:00:00Z');
    vi.setSystemTime(now);

    const recentFeed: Feed = {
      ...baseFeed,
      lastUpdatedAt: new Date('2024-06-08T10:00:00Z').toISOString(),
    };

    expect(FeedUtils.hasBeenUpdatedRecently(recentFeed)).toBe(true);
    expect(FeedUtils.formatLastUpdated(recentFeed)).toBe(
      new Date('2024-06-08T10:00:00Z').toLocaleDateString(),
    );
    expect(FeedUtils.formatLastUpdated(baseFeed)).toBe('Never updated');
    expect(FeedUtils.hasBeenUpdatedRecently(baseFeed)).toBe(false);
  });
});
