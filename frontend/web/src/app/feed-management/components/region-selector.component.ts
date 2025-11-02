import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, OnInit, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MetropolitanRegion } from '../models/region.models';

interface RegionWithLocation extends MetropolitanRegion {
  country?: string;
  state?: string;
}

@Component({
  selector: 'app-region-selector',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatIconModule,
    MatChipsModule,
    MatButtonModule
  ],
  template: `
    <div class="region-selector-container">
      <!-- Section Header -->
      <div class="section-header">
        <div class="header-content">
          <mat-icon class="header-icon">filter_alt</mat-icon>
          <div class="header-text">
            <h3 class="header-title">Location Filters</h3>
            <p class="header-subtitle">Select a country, state, and region to view available transit feeds</p>
          </div>
        </div>
        <button
          mat-button
          class="clear-filters-btn"
          *ngIf="selectedCountry || selectedState"
          (click)="clearFilters()">
          <mat-icon>clear</mat-icon>
          Clear Filters
        </button>
      </div>

      <!-- Filters Card -->
      <div class="filters-card">
        <div class="filters-row">
          <!-- Country Filter -->
          <mat-form-field class="filter-field" appearance="fill">
            <mat-label>Country</mat-label>
            <mat-icon matPrefix class="field-icon">public</mat-icon>
            <mat-select
              [(value)]="selectedCountry"
              (selectionChange)="onCountryChange($event.value)"
              [disabled]="disabled">
              <mat-option [value]="null">
                <span class="option-text">All Countries</span>
              </mat-option>
              <mat-option *ngFor="let country of availableCountries" [value]="country">
                <span class="option-text">{{ country }}</span>
              </mat-option>
            </mat-select>
            <mat-hint *ngIf="availableCountries.length > 0">{{ availableCountries.length }} available</mat-hint>
          </mat-form-field>

          <!-- State/Province Filter -->
          <mat-form-field class="filter-field" appearance="fill">
            <mat-label>State/Province</mat-label>
            <mat-icon matPrefix class="field-icon">location_city</mat-icon>
            <mat-select
              [(value)]="selectedState"
              (selectionChange)="onStateChange($event.value)"
              [disabled]="disabled || !selectedCountry">
              <mat-option [value]="null">
                <span class="option-text">All States/Provinces</span>
              </mat-option>
              <mat-option *ngFor="let state of availableStates" [value]="state">
                <span class="option-text">{{ state }}</span>
              </mat-option>
            </mat-select>
            <mat-hint *ngIf="selectedCountry && availableStates.length > 0">{{ availableStates.length }} available</mat-hint>
            <mat-hint *ngIf="!selectedCountry">Select a country first</mat-hint>
          </mat-form-field>

          <!-- Region Selector -->
          <mat-form-field class="filter-field region-field" appearance="fill">
            <mat-label>Metropolitan Region</mat-label>
            <mat-icon matPrefix class="field-icon">place</mat-icon>
            <mat-select
              [value]="selectedRegionId"
              (selectionChange)="onRegionChange($event.value)"
              [disabled]="disabled">
              <mat-option *ngFor="let region of filteredRegions" [value]="region.regionOnestopId">
                <span class="option-text">{{ region.name }}</span>
              </mat-option>
            </mat-select>
            <mat-hint *ngIf="filteredRegions.length > 0">{{ filteredRegions.length }} region{{ filteredRegions.length !== 1 ? 's' : '' }}</mat-hint>
          </mat-form-field>
        </div>

        <!-- Active Filters Display -->
        <div class="active-filters" *ngIf="selectedCountry || selectedState">
          <span class="active-label">Active Filters:</span>
          <mat-chip-listbox>
            <mat-chip *ngIf="selectedCountry" class="filter-chip" (removed)="clearCountry()">
              <mat-icon class="chip-icon">public</mat-icon>
              {{ selectedCountry }}
              <button matChipRemove>
                <mat-icon>cancel</mat-icon>
              </button>
            </mat-chip>
            <mat-chip *ngIf="selectedState" class="filter-chip" (removed)="clearState()">
              <mat-icon class="chip-icon">location_city</mat-icon>
              {{ selectedState }}
              <button matChipRemove>
                <mat-icon>cancel</mat-icon>
              </button>
            </mat-chip>
          </mat-chip-listbox>
        </div>
      </div>

      <!-- Results Summary -->
      <div class="results-summary" *ngIf="filteredRegions.length !== regions.length || filteredRegions.length > 0">
        <mat-icon class="summary-icon">insights</mat-icon>
        <span class="summary-text">
          <strong>{{ filteredRegions.length }}</strong> of <strong>{{ regions.length }}</strong> regions match your filters
        </span>
      </div>
    </div>
  `,
  styles: [`
    .region-selector-container {
      padding: 24px 0;
      margin-bottom: 24px;
    }

    /* Section Header */
    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 20px;
      gap: 16px;
    }

    .header-content {
      display: flex;
      gap: 16px;
      align-items: flex-start;
    }

    .header-icon {
      color: #2980B9;
      font-size: 32px;
      width: 32px;
      height: 32px;
      margin-top: 4px;
    }

    :host-context(.dark-theme) .header-icon {
      color: #64b5f6;
    }

    .header-text {
      flex: 1;
    }

    .header-title {
      margin: 0 0 4px 0;
      font-size: 1.5rem;
      font-weight: 600;
      color: #1A3A52;
      font-family: "Red Hat Display", "Public Sans", sans-serif;
    }

    :host-context(.dark-theme) .header-title {
      color: #ffffff;
    }

    .header-subtitle {
      margin: 0;
      font-size: 0.875rem;
      color: #666;
      line-height: 1.5;
    }

    :host-context(.dark-theme) .header-subtitle {
      color: rgba(255, 255, 255, 0.7);
    }

    .clear-filters-btn {
      flex-shrink: 0;
      border-radius: 8px;
    }

    /* Filters Card */
    .filters-card {
      background: #ffffff;
      border-radius: 12px;
      padding: 24px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
      border: 1px solid #e0e0e0;
      transition: box-shadow 0.3s ease;
    }

    .filters-card:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
    }

    :host-context(.dark-theme) .filters-card {
      background: #1e1e1e;
      border-color: #404040;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
    }

    :host-context(.dark-theme) .filters-card:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
    }

    .filters-row {
      display: grid;
      grid-template-columns: 1fr 1fr 2fr;
      gap: 20px;
      align-items: start;
    }

    .filter-field {
      width: 100%;
    }

    .region-field {
      grid-column: span 1;
    }

    .field-icon {
      color: #2980B9;
      margin-right: 8px;
    }

    :host-context(.dark-theme) .field-icon {
      color: #64b5f6;
    }

    .option-text {
      font-size: 0.9375rem;
    }

    /* Active Filters */
    .active-filters {
      margin-top: 20px;
      padding-top: 20px;
      border-top: 1px solid #e0e0e0;
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }

    :host-context(.dark-theme) .active-filters {
      border-top-color: #404040;
    }

    .active-label {
      font-size: 0.875rem;
      font-weight: 500;
      color: #666;
    }

    :host-context(.dark-theme) .active-label {
      color: rgba(255, 255, 255, 0.7);
    }

    .filter-chip {
      background-color: rgba(41, 128, 185, 0.12) !important;
      color: #2980B9 !important;
      border: 1px solid rgba(41, 128, 185, 0.3);
      font-weight: 500;
    }

    :host-context(.dark-theme) .filter-chip {
      background-color: rgba(41, 128, 185, 0.25) !important;
      border-color: rgba(41, 128, 185, 0.5);
    }

    .chip-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      margin-right: 4px;
    }

    /* Results Summary */
    .results-summary {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 16px;
      padding: 14px 18px;
      background: linear-gradient(135deg, rgba(41, 128, 185, 0.1), rgba(41, 128, 185, 0.05));
      border-left: 4px solid #2980B9;
      border-radius: 8px;
      font-size: 0.9375rem;
    }

    :host-context(.dark-theme) .results-summary {
      background: linear-gradient(135deg, rgba(41, 128, 185, 0.2), rgba(41, 128, 185, 0.1));
      border-left-color: #64b5f6;
    }

    .summary-icon {
      color: #2980B9;
      font-size: 22px;
      width: 22px;
      height: 22px;
    }

    :host-context(.dark-theme) .summary-icon {
      color: #64b5f6;
    }

    .summary-text {
      color: #1A3A52;
      line-height: 1.5;
    }

    :host-context(.dark-theme) .summary-text {
      color: rgba(255, 255, 255, 0.87);
    }

    .summary-text strong {
      font-weight: 600;
      color: #2980B9;
    }

    :host-context(.dark-theme) .summary-text strong {
      color: #64b5f6;
    }

    /* Responsive Design */
    @media (max-width: 1024px) {
      .filters-row {
        grid-template-columns: 1fr 1fr;
      }

      .region-field {
        grid-column: span 2;
      }
    }

    @media (max-width: 768px) {
      .section-header {
        flex-direction: column;
      }

      .header-content {
        width: 100%;
      }

      .clear-filters-btn {
        width: 100%;
      }

      .filters-card {
        padding: 20px;
      }

      .filters-row {
        grid-template-columns: 1fr;
      }

      .region-field {
        grid-column: span 1;
      }

      .active-filters {
        flex-direction: column;
        align-items: flex-start;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionSelectorComponent implements OnInit, OnChanges {
  @Input() regions: MetropolitanRegion[] = [];
  @Input() selectedRegionId: string | null = null;
  @Input() disabled = false;

  @Output() regionChange = new EventEmitter<string>();

  selectedCountry: string | null = null;
  selectedState: string | null = null;

  availableCountries: string[] = [];
  availableStates: string[] = [];
  filteredRegions: MetropolitanRegion[] = [];

  ngOnInit(): void {
    this.extractLocationData();
    this.filteredRegions = this.regions;
  }

  ngOnChanges(): void {
    this.extractLocationData();
    this.applyFilters();
  }

  private extractLocationData(): void {
    // Extract country and state from region names
    // Assuming format like "San Francisco Bay Area, CA, USA" or "Toronto, ON, Canada"
    const countriesSet = new Set<string>();
    const statesSet = new Set<string>();

    this.regions.forEach(region => {
      const parts = region.name.split(',').map(p => p.trim());
      if (parts.length >= 2) {
        const country = parts[parts.length - 1]; // Last part is country
        const state = parts[parts.length - 2]; // Second to last is state/province

        countriesSet.add(country);
        statesSet.add(state);
      }
    });

    this.availableCountries = Array.from(countriesSet).sort();
  }

  onCountryChange(country: string | null): void {
    this.selectedCountry = country;
    this.selectedState = null;
    this.updateAvailableStates();
    this.applyFilters();
  }

  onStateChange(state: string | null): void {
    this.selectedState = state;
    this.applyFilters();
  }

  private updateAvailableStates(): void {
    if (!this.selectedCountry) {
      this.availableStates = [];
      return;
    }

    const statesSet = new Set<string>();
    this.regions.forEach(region => {
      const parts = region.name.split(',').map(p => p.trim());
      if (parts.length >= 2) {
        const country = parts[parts.length - 1];
        const state = parts[parts.length - 2];

        if (country === this.selectedCountry) {
          statesSet.add(state);
        }
      }
    });

    this.availableStates = Array.from(statesSet).sort();
  }

  private applyFilters(): void {
    this.filteredRegions = this.regions.filter(region => {
      const parts = region.name.split(',').map(p => p.trim());

      if (parts.length < 2) {
        return true; // Show regions without proper formatting
      }

      const country = parts[parts.length - 1];
      const state = parts[parts.length - 2];

      // Apply country filter
      if (this.selectedCountry && country !== this.selectedCountry) {
        return false;
      }

      // Apply state filter
      if (this.selectedState && state !== this.selectedState) {
        return false;
      }

      return true;
    });
  }

  onRegionChange(regionId: string): void {
    this.regionChange.emit(regionId);
  }

  clearFilters(): void {
    this.selectedCountry = null;
    this.selectedState = null;
    this.updateAvailableStates();
    this.applyFilters();
  }

  clearCountry(): void {
    this.selectedCountry = null;
    this.selectedState = null;
    this.updateAvailableStates();
    this.applyFilters();
  }

  clearState(): void {
    this.selectedState = null;
    this.applyFilters();
  }
}
