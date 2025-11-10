import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface SelectedRegionInfo {
  id: string | null;
  name: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class FeedsMetricsService {
  private readonly selectedRegionSubject = new BehaviorSubject<SelectedRegionInfo>({
    id: null,
    name: null
  });

  private readonly discoverFeedCountSubject = new BehaviorSubject<number>(0);
  private readonly totalImportElementsSubject = new BehaviorSubject<number>(0);

  readonly selectedRegion$ = this.selectedRegionSubject.asObservable();
  readonly discoverFeedCount$ = this.discoverFeedCountSubject.asObservable();
  readonly totalImportElements$ = this.totalImportElementsSubject.asObservable();

  setSelectedRegion(id: string | null, name: string | null): void {
    this.selectedRegionSubject.next({ id, name });
  }

  resetSelectedRegion(): void {
    this.setSelectedRegion(null, null);
  }

  setDiscoverFeedCount(count: number): void {
    this.discoverFeedCountSubject.next(count);
  }

  setTotalImportElements(count: number): void {
    this.totalImportElementsSubject.next(count);
  }
}
