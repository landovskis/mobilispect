import { FeedsEventsService } from './feeds-events.service';
import { firstValueFrom, take } from 'rxjs';

describe('FeedsEventsService', () => {
  it('emits refresh events', async () => {
    const service = new FeedsEventsService();

    const refreshPromise = firstValueFrom(service.refresh$.pipe(take(1)));
    service.triggerRefresh();

    await expectAsync(refreshPromise).toBeResolved();
  });
});
