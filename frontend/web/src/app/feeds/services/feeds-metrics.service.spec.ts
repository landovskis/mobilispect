import { firstValueFrom } from 'rxjs';
import { FeedsMetricsService } from './feeds-metrics.service';

describe('FeedsMetricsService', () => {
  let service: FeedsMetricsService;

  beforeEach(() => {
    service = new FeedsMetricsService();
  });

  it('updates selected region info', async () => {
    service.setSelectedRegion('r-1', 'Region One');
    const selected = await firstValueFrom(service.selectedRegion$);
    expect(selected).toEqual({ id: 'r-1', name: 'Region One' });

    service.resetSelectedRegion();
    const reset = await firstValueFrom(service.selectedRegion$);
    expect(reset).toEqual({ id: null, name: null });
  });

  it('updates counts for metrics', async () => {
    service.setDiscoverFeedCount(12);
    const discoverCount = await firstValueFrom(service.discoverFeedCount$);
    expect(discoverCount).toBe(12);

    service.setTotalImportElements(42);
    const totalImports = await firstValueFrom(service.totalImportElements$);
    expect(totalImports).toBe(42);
  });
});
