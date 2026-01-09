import { FeedImportSummary, ImportStatus, TriggerType } from './import.models';
import { RegionImportGroup, RegionImportGroupingUtils } from './region-import-group.model';

describe('RegionImportGroupingUtils', () => {
  const fixedDate = '2026-01-07T12:00:00Z';

  const createImport = (
    id: string,
    feedId: string,
    feedName: string,
    regionId: string,
    regionName: string,
    status: ImportStatus,
    progressPercentage: number = 0
  ): FeedImportSummary => ({
    id,
    feedOnestopId: feedId,
    feedName,
    regionOnestopId: regionId,
    regionName,
    status,
    triggerType: TriggerType.MANUAL,
    startedAt: fixedDate,
    completedAt: null,
    progress: progressPercentage > 0 ? {
      progressPercentage,
      totalSteps: 5,
      currentStep: 'Processing',
      estimatedTimeRemainingSeconds: 60
    } : null
  });

  describe('groupImportsByRegion', () => {
    it('should group imports by region', () => {
      // Given: Imports from two different regions
      const imports: FeedImportSummary[] = [
        createImport('1', 'f-bart', 'BART', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.RUNNING, 50),
        createImport('2', 'f-muni', 'MUNI', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.RUNNING, 75),
        createImport('3', 'f-mta', 'MTA', 'r-nyc', 'New York City', ImportStatus.PENDING, 0),
      ];

      // When: Grouping by region
      const result = RegionImportGroupingUtils.groupImportsByRegion(imports);

      // Then: Two region groups are created
      expect(result.length).toBe(2);

      const sfBay = result.find(g => g.regionOnestopId === 'r-sf-bay');
      expect(sfBay).toBeDefined();
      expect(sfBay!.regionName).toBe('San Francisco Bay Area');
      expect(sfBay!.feedImports.length).toBe(2);
      expect(sfBay!.totalFeeds).toBe(2);

      const nyc = result.find(g => g.regionOnestopId === 'r-nyc');
      expect(nyc).toBeDefined();
      expect(nyc!.regionName).toBe('New York City');
      expect(nyc!.feedImports.length).toBe(1);
      expect(nyc!.totalFeeds).toBe(1);
    });

    it('should calculate average progress correctly', () => {
      // Given: Imports with different progress levels
      const imports: FeedImportSummary[] = [
        createImport('1', 'f-bart', 'BART', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.RUNNING, 50),
        createImport('2', 'f-muni', 'MUNI', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.RUNNING, 100),
        createImport('3', 'f-caltrain', 'Caltrain', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.RUNNING, 25),
      ];

      // When: Grouping by region
      const result = RegionImportGroupingUtils.groupImportsByRegion(imports);

      // Then: Average progress is calculated correctly (50 + 100 + 25) / 3 = 58.33...
      expect(result.length).toBe(1);
      expect(result[0].averageProgress).toBeCloseTo(58.33, 1);
    });

    it('should handle imports with null progress', () => {
      // Given: Some imports have no progress data
      const imports: FeedImportSummary[] = [
        createImport('1', 'f-bart', 'BART', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.PENDING, 0),
        createImport('2', 'f-muni', 'MUNI', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.RUNNING, 50),
      ];

      // When: Grouping by region
      const result = RegionImportGroupingUtils.groupImportsByRegion(imports);

      // Then: Average only counts imports with progress data (50 / 1 = 50)
      expect(result.length).toBe(1);
      expect(result[0].averageProgress).toBe(50);
    });

    it('should handle empty imports array', () => {
      // Given: Empty imports array
      const imports: FeedImportSummary[] = [];

      // When: Grouping by region
      const result = RegionImportGroupingUtils.groupImportsByRegion(imports);

      // Then: Empty array is returned
      expect(result).toEqual([]);
    });

    it('should identify regions with failures', () => {
      // Given: Imports with one failed import
      const imports: FeedImportSummary[] = [
        createImport('1', 'f-bart', 'BART', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.RUNNING, 50),
        createImport('2', 'f-muni', 'MUNI', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.FAILED, 0),
      ];

      // When: Grouping by region
      const result = RegionImportGroupingUtils.groupImportsByRegion(imports);

      // Then: hasFailures flag is set
      expect(result.length).toBe(1);
      expect(result[0].hasFailures).toBe(true);
      expect(result[0].allCompleted).toBe(false);
    });

    it('should identify when all imports are completed', () => {
      // Given: All imports are completed
      const imports: FeedImportSummary[] = [
        createImport('1', 'f-bart', 'BART', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.COMPLETED, 100),
        createImport('2', 'f-muni', 'MUNI', 'r-sf-bay', 'San Francisco Bay Area', ImportStatus.COMPLETED, 100),
      ];

      // When: Grouping by region
      const result = RegionImportGroupingUtils.groupImportsByRegion(imports);

      // Then: allCompleted flag is set
      expect(result.length).toBe(1);
      expect(result[0].allCompleted).toBe(true);
      expect(result[0].hasFailures).toBe(false);
    });

    it('should handle feeds with no region gracefully', () => {
      // Given: Import with no region data
      const imports: FeedImportSummary[] = [
        {
          id: '1',
          feedOnestopId: 'f-orphan',
          feedName: 'Orphan Feed',
          regionOnestopId: null,
          regionName: null,
          status: ImportStatus.RUNNING,
          triggerType: TriggerType.MANUAL,
          startedAt: fixedDate,
          completedAt: null,
          progress: null
        },
      ];

      // When: Grouping by region
      const result = RegionImportGroupingUtils.groupImportsByRegion(imports);

      // Then: Group is created with 'unknown-region' key
      expect(result.length).toBe(1);
      expect(result[0].regionOnestopId).toBe('unknown-region');
      expect(result[0].regionName).toBe('Unknown Region');
      expect(result[0].feedImports.length).toBe(1);
    });
  });

  describe('sortRegionGroups', () => {
    it('should sort regions alphabetically by name', () => {
      // Given: Unsorted region groups
      const groups: RegionImportGroup[] = [
        {
          regionOnestopId: 'r-nyc',
          regionName: 'New York City',
          feedImports: [],
          totalFeeds: 1,
          averageProgress: 0,
          hasFailures: false,
          allCompleted: false,
        },
        {
          regionOnestopId: 'r-sf-bay',
          regionName: 'San Francisco Bay Area',
          feedImports: [],
          totalFeeds: 2,
          averageProgress: 50,
          hasFailures: false,
          allCompleted: false,
        },
        {
          regionOnestopId: 'r-la',
          regionName: 'Los Angeles',
          feedImports: [],
          totalFeeds: 1,
          averageProgress: 25,
          hasFailures: false,
          allCompleted: false,
        },
      ];

      // When: Sorting
      const result = RegionImportGroupingUtils.sortRegionGroups(groups);

      // Then: Sorted alphabetically
      expect(result.map(g => g.regionName)).toEqual([
        'Los Angeles',
        'New York City',
        'San Francisco Bay Area',
      ]);
    });

    it('should handle empty groups array', () => {
      // Given: Empty array
      const groups: RegionImportGroup[] = [];

      // When: Sorting
      const result = RegionImportGroupingUtils.sortRegionGroups(groups);

      // Then: Empty array returned
      expect(result).toEqual([]);
    });
  });

  describe('calculateAverageProgress', () => {
    it('should calculate average of all feed progress percentages', () => {
      // Given: Imports with varying progress
      const imports: FeedImportSummary[] = [
        createImport('1', 'f-1', 'Feed 1', 'r-test', 'Test Region', ImportStatus.RUNNING, 25),
        createImport('2', 'f-2', 'Feed 2', 'r-test', 'Test Region', ImportStatus.RUNNING, 50),
        createImport('3', 'f-3', 'Feed 3', 'r-test', 'Test Region', ImportStatus.RUNNING, 75),
        createImport('4', 'f-4', 'Feed 4', 'r-test', 'Test Region', ImportStatus.RUNNING, 100),
      ];

      // When: Calculating average
      const result = RegionImportGroupingUtils.calculateAverageProgress(imports);

      // Then: Average is (25 + 50 + 75 + 100) / 4 = 62.5
      expect(result).toBe(62.5);
    });

    it('should ignore imports with no progress data', () => {
      // Given: Mix of imports with and without progress
      const imports: FeedImportSummary[] = [
        createImport('1', 'f-1', 'Feed 1', 'r-test', 'Test Region', ImportStatus.PENDING, 0),
        createImport('2', 'f-2', 'Feed 2', 'r-test', 'Test Region', ImportStatus.RUNNING, 50),
        createImport('3', 'f-3', 'Feed 3', 'r-test', 'Test Region', ImportStatus.RUNNING, 100),
      ];

      // When: Calculating average
      const result = RegionImportGroupingUtils.calculateAverageProgress(imports);

      // Then: Average only includes imports with progress (50 + 100) / 2 = 75
      expect(result).toBe(75);
    });

    it('should return 0 when no imports have progress', () => {
      // Given: All imports have no progress
      const imports: FeedImportSummary[] = [
        createImport('1', 'f-1', 'Feed 1', 'r-test', 'Test Region', ImportStatus.PENDING, 0),
        createImport('2', 'f-2', 'Feed 2', 'r-test', 'Test Region', ImportStatus.PENDING, 0),
      ];

      // When: Calculating average
      const result = RegionImportGroupingUtils.calculateAverageProgress(imports);

      // Then: Returns 0
      expect(result).toBe(0);
    });

    it('should return 0 for empty imports array', () => {
      // Given: Empty imports
      const imports: FeedImportSummary[] = [];

      // When: Calculating average
      const result = RegionImportGroupingUtils.calculateAverageProgress(imports);

      // Then: Returns 0
      expect(result).toBe(0);
    });
  });
});
