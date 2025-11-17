import { MetropolitanRegion, RegionUtils } from './region.models';

describe('RegionUtils', () => {
  const baseRegion: Omit<MetropolitanRegion, 'name' | 'adm0Name' | 'adm1Name'> = {
    regionOnestopId: 'r-test',
    autoUpdateEnabled: false,
    feedCount: 0,
    lastCheckAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

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

    expect(RegionUtils.getDisplayName(region)).toBe('San Francisco, California, United States');
  });
});
