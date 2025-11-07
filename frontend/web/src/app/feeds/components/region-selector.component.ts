import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MetropolitanRegion } from '../models/region.models';
import { Observable, Subject } from 'rxjs';
import { map, startWith, takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-region-selector',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
    MatAutocompleteModule
  ],
  template: `
    <div class="region-selector-container">
      <mat-form-field class="region-autocomplete" appearance="outline">
        <mat-label>Search Metropolitan Region</mat-label>
        <mat-icon matPrefix class="search-icon">search</mat-icon>
        <input
          type="text"
          matInput
          [formControl]="searchControl"
          [matAutocomplete]="auto"
          placeholder="Type to search regions..."
          [disabled]="disabled">
        <button
          *ngIf="searchControl.value"
          matSuffix
          mat-icon-button
          aria-label="Clear"
          (click)="clearSearch()">
          <mat-icon>close</mat-icon>
        </button>
        <mat-autocomplete
          #auto="matAutocomplete"
          (optionSelected)="onRegionSelected($event.option.value)"
          [displayWith]="displayRegion">
          <mat-option *ngFor="let region of filteredRegions$ | async" [value]="region">
            <div class="region-option">
              <mat-icon class="region-icon">place</mat-icon>
              <span class="region-name">{{ region.name }}</span>
              <span class="region-feed-count" *ngIf="region.feedCount > 0">
                {{ region.feedCount }} feed{{ region.feedCount !== 1 ? 's' : '' }}
              </span>
            </div>
          </mat-option>
        </mat-autocomplete>
        <mat-hint *ngIf="regions.length > 0">{{ regions.length }} region{{ regions.length !== 1 ? 's' : '' }} available</mat-hint>
      </mat-form-field>
    </div>
  `,
  styles: [`
    .region-selector-container {
      width: 100%;
      max-width: 600px;
    }

    .region-autocomplete {
      width: 100%;
    }

    .search-icon {
      color: #2980B9;
      margin-right: 8px;
    }

    :host-context(.dark-theme) .search-icon {
      color: #64b5f6;
    }

    .region-option {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 4px 0;
    }

    .region-icon {
      color: #2980B9;
      font-size: 20px;
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }

    :host-context(.dark-theme) .region-icon {
      color: #64b5f6;
    }

    .region-name {
      flex: 1;
      font-size: 0.9375rem;
      color: #1A3A52;
    }

    :host-context(.dark-theme) .region-name {
      color: rgba(255, 255, 255, 0.87);
    }

    .region-feed-count {
      font-size: 0.8125rem;
      color: #666;
      background: rgba(41, 128, 185, 0.1);
      padding: 2px 8px;
      border-radius: 12px;
      font-weight: 500;
    }

    :host-context(.dark-theme) .region-feed-count {
      color: rgba(255, 255, 255, 0.6);
      background: rgba(41, 128, 185, 0.2);
    }

    ::ng-deep .mat-mdc-option {
      min-height: 48px !important;
    }

    ::ng-deep .mat-mdc-autocomplete-panel {
      max-height: 400px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionSelectorComponent implements OnInit, OnDestroy {
  @Input() regions: MetropolitanRegion[] = [];
  @Input() selectedRegionId: string | null = null;
  @Input() disabled = false;

  @Output() regionChange = new EventEmitter<string>();

  searchControl = new FormControl<string | MetropolitanRegion>('');
  filteredRegions$!: Observable<MetropolitanRegion[]>;

  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    // Initialize the autocomplete filter
    this.filteredRegions$ = this.searchControl.valueChanges.pipe(
      startWith(''),
      map(value => this._filterRegions(value)),
      takeUntil(this.destroy$)
    );

    // Set initial selected region if provided
    if (this.selectedRegionId) {
      const selectedRegion = this.regions.find(r => r.regionOnestopId === this.selectedRegionId);
      if (selectedRegion) {
        this.searchControl.setValue(selectedRegion, { emitEvent: false });
      }
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private _filterRegions(value: string | MetropolitanRegion | null): MetropolitanRegion[] {
    if (!value) {
      return this.regions;
    }

    const filterValue = typeof value === 'string'
      ? value.toLowerCase()
      : value.name.toLowerCase();

    return this.regions.filter(region =>
      region.name.toLowerCase().includes(filterValue) ||
      region.regionOnestopId.toLowerCase().includes(filterValue)
    );
  }

  displayRegion(region: MetropolitanRegion | null): string {
    return region ? region.name : '';
  }

  onRegionSelected(region: MetropolitanRegion): void {
    this.regionChange.emit(region.regionOnestopId);
  }

  clearSearch(): void {
    this.searchControl.setValue('');
    this.regionChange.emit('');
  }
}
