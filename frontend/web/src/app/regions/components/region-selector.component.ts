import {
  Component,
  Input,
  Output,
  EventEmitter,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  OnChanges,
  SimpleChanges
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MetropolitanRegion, RegionUtils } from '../../feeds/models/region.models';
import { Observable, Subject, of } from 'rxjs';
import { map, startWith, takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-region-selector',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatIconModule
  ],
  template: `
    <div class="region-selector flex w-full max-w-[640px] flex-col gap-3">
      <div class="search-input flex items-center gap-2.5 rounded-[14px] border px-[1.1rem] py-[0.85rem]" [class.disabled]="disabled">
        <mat-icon class="search-icon text-[20px]">search</mat-icon>
        <input
          [id]="searchInputId"
          type="text"
          class="region-input flex-1 border-0 bg-transparent text-base outline-none"
          [formControl]="searchControl"
          placeholder="Type to search regions..."
          [attr.disabled]="disabled ? true : null"
          autocomplete="off">
        @if (searchControl.value) {
          <button
            type="button"
            class="clear-button rounded-full p-1 transition"
            aria-label="Clear search"
            (click)="clearSearch()">
            <mat-icon>close</mat-icon>
          </button>
        }
      </div>

      @if (filteredRegions$ | async; as regions) {
        @if (regions.length > 0) {
          <ul class="region-results m-0 list-none p-0 max-h-[360px] overflow-y-auto rounded-2xl border bg-white shadow-[0_12px_24px_rgba(15,23,42,0.08)]" role="listbox">
            @for (region of regions; track region.regionOnestopId) {
              <li>
                <button
                  type="button"
                  class="region-option flex w-full items-center gap-3 px-[18px] py-3 text-left"
                  [class.selected]="region.regionOnestopId === selectedRegionId"
                  (click)="onRegionSelected(region)"
                  [disabled]="disabled">
                  <mat-icon class="region-icon">place</mat-icon>
                  <div class="region-details flex flex-1 flex-col gap-1">
                    <span class="region-name">{{ getDisplayName(region) }}</span>
                    @if (region.feedCount > 0) {
                      <span class="region-feed-count inline-flex w-fit items-center rounded-full px-2.5 py-0.5 text-[0.78rem] font-semibold">
                        {{ region.feedCount }} feed{{ region.feedCount !== 1 ? 's' : '' }}
                      </span>
                    }
                  </div>
                </button>
              </li>
            }
          </ul>
        }
      }
    </div>
  `,
  styles: [`
    .selector-label {
      font-size: 0.85rem;
      font-weight: 600;
      letter-spacing: 0.02em;
      text-transform: uppercase;
      color: #1e3a8a;
    }

    .search-input {
      border: 1px solid rgba(30, 58, 138, 0.2);
      background: #fff;
      transition: border-color 0.2s ease, box-shadow 0.2s ease;
    }

    .search-input:focus-within {
      border-color: #1e3a8a;
      box-shadow: 0 0 0 2px rgba(30, 58, 138, 0.12);
    }

    .search-input.disabled {
      opacity: 0.6;
      pointer-events: none;
    }

    .search-icon {
      color: #1e3a8a;
    }

    .region-input {
      color: #0f172a;
    }

    .region-input::placeholder {
      color: rgba(15, 23, 42, 0.4);
    }

    .clear-button {
      border: none;
      background: transparent;
      color: rgba(15, 23, 42, 0.5);
      cursor: pointer;
      transition: background 0.2s ease, color 0.2s ease;
    }

    .clear-button:hover {
      background: rgba(30, 58, 138, 0.08);
      color: #1e3a8a;
    }

    .region-option {
      border: none;
      background: transparent;
      cursor: pointer;
      transition: background 0.2s ease, color 0.2s ease;
    }

    .region-option:hover:not(:disabled) {
      background: rgba(15, 23, 42, 0.04);
    }

    .region-option.selected {
      background: rgba(30, 58, 138, 0.08);
      color: #1e3a8a;
      font-weight: 600;
    }

    .region-option:disabled {
      cursor: not-allowed;
      opacity: 0.6;
    }

    .region-name {
      font-size: 0.95rem;
      color: inherit;
    }

    .region-feed-count {
      font-size: 0.78rem;
      color: rgba(15, 23, 42, 0.7);
      background: rgba(30, 58, 138, 0.12);
      font-weight: 600;
    }

    .empty-results {
      border-radius: 12px;
      border: 1px dashed rgba(15, 23, 42, 0.2);
      color: rgba(15, 23, 42, 0.6);
    }

    .empty-results mat-icon {
      color: #94a3b8;
    }

    :host-context(.dark-theme) .search-input {
      background: rgba(15, 23, 42, 0.6);
      border-color: rgba(147, 197, 253, 0.4);
    }

    :host-context(.dark-theme) .search-input:focus-within {
      border-color: #93c5fd;
      box-shadow: 0 0 0 2px rgba(147, 197, 253, 0.25);
    }

    :host-context(.dark-theme) .region-input {
      color: rgba(248, 250, 252, 0.95);
    }

    :host-context(.dark-theme) .region-input::placeholder {
      color: rgba(255, 255, 255, 0.45);
    }

    :host-context(.dark-theme) .region-results {
      background: rgba(15, 23, 42, 0.85);
      border-color: rgba(255, 255, 255, 0.08);
      box-shadow: 0 18px 40px rgba(2, 6, 23, 0.6);
    }

    :host-context(.dark-theme) .region-option:hover:not(:disabled) {
      background: rgba(59, 130, 246, 0.15);
    }

    :host-context(.dark-theme) .region-option.selected {
      background: rgba(59, 130, 246, 0.18);
      color: #bfdbfe;
    }

    :host-context(.dark-theme) .region-feed-count {
      color: rgba(255, 255, 255, 0.8);
      background: rgba(59, 130, 246, 0.25);
    }

    :host-context(.dark-theme) .empty-results {
      border-color: rgba(255, 255, 255, 0.18);
      color: rgba(255, 255, 255, 0.7);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionSelectorComponent implements OnInit, OnDestroy, OnChanges {
  private static nextId = 0;

  @Input() regions: MetropolitanRegion[] = [];
  @Input() selectedRegionId: string | null = null;
  @Input() disabled = false;

  @Output() regionChange = new EventEmitter<string>();

  readonly searchInputId = `region-search-${RegionSelectorComponent.nextId++}`;
  searchControl = new FormControl<string>('', { nonNullable: true });
  filteredRegions$: Observable<MetropolitanRegion[]> = of([]);

  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.filteredRegions$ = this.searchControl.valueChanges.pipe(
      startWith(''),
      map(value => this._filterRegions(value)),
      takeUntil(this.destroy$)
    );

    this.updateControlState();
    this.syncSelectedRegionLabel();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['disabled'] && !changes['disabled'].firstChange) {
      this.updateControlState();
    }

    if (changes['selectedRegionId']) {
      if (this.selectedRegionId) {
        this.syncSelectedRegionLabel();
      } else {
        this.searchControl.setValue('', { emitEvent: false });
      }
    } else if (changes['regions'] && this.selectedRegionId) {
      this.syncSelectedRegionLabel();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private _filterRegions(value: string | null): MetropolitanRegion[] {
    const term = value?.toLowerCase().trim();
    if (!term) {
      return this.regions;
    }

    return this.regions.filter(region => {
      const haystack = [
        region.name,
        region.regionOnestopId,
        region.adm1Name ?? '',
        region.adm0Name ?? ''
      ].join(' ').toLowerCase();
      return haystack.includes(term);
    });
  }

  onRegionSelected(region: MetropolitanRegion): void {
    this.searchControl.setValue(this.getDisplayName(region), { emitEvent: false });
    this.regionChange.emit(region.regionOnestopId);
  }

  clearSearch(): void {
    this.searchControl.setValue('');
    this.regionChange.emit('');
  }

  private syncSelectedRegionLabel(): void {
    if (!this.selectedRegionId) {
      return;
    }

    const selectedRegion = this.regions.find(r => r.regionOnestopId === this.selectedRegionId);
    if (selectedRegion) {
      this.searchControl.setValue(this.getDisplayName(selectedRegion), { emitEvent: false });
    }
  }

  private updateControlState(): void {
    if (this.disabled) {
      this.searchControl.disable({ emitEvent: false });
    } else {
      this.searchControl.enable({ emitEvent: false });
    }
  }
  getDisplayName(region: MetropolitanRegion): string {
    return RegionUtils.getDisplayName(region);
  }
}
