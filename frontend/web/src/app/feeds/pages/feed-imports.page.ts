import { Component, OnDestroy, OnInit, inject } from '@angular/core';

import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule } from '@angular/material/dialog';
import { Observable, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { FeedImportDetail, FeedImportSummary } from '../models';
import { ImportService } from '../services/import.service';
import { ActiveImportsCardComponent } from '../components/active-imports-card.component';
import { ImportsHistoryCardComponent } from '../components/imports-history-card.component';
import { FeedsMetricsService } from '../services/feeds-metrics.service';
import { FeedsEventsService } from '../services/feeds-events.service';

@Component({
  selector: 'app-feed-imports-page',
  standalone: true,
  imports: [
    MatSnackBarModule,
    MatDialogModule,
    ActiveImportsCardComponent,
    ImportsHistoryCardComponent
],
  template: `
    <div class="tab-content py-6 max-md:py-4">
      <app-active-imports-card
        [activeImports$]="activeImports$"
        (cancelImport)="cancelImport($event)"
      ></app-active-imports-card>

      <app-imports-history-card
        [loading]="loadingHistory"
        [history]="importHistory"
        [totalItems]="totalImportElements"
        [pageIndex]="importHistoryPage"
        [pageSize]="importHistorySize"
        [pageSizeOptions]="pageSizeOptions"
        [displayedColumns]="displayedColumns"
        [showHeader]="hasActiveImports"
        (pageChange)="loadImportHistory($event)"
      ></app-imports-history-card>
    </div>
  `
})
export class FeedImportsPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  activeImports$: Observable<FeedImportSummary[]>;

  importHistory: FeedImportDetail[] = [];
  importHistoryPage = 0;
  importHistorySize = 20;
  pageSizeOptions: number[] = [10, 20, 50, 100];
  displayedColumns: string[] = ['feedName', 'region', 'status', 'startedAt', 'completedAt', 'fileSize'];
  totalImportPages = 0;
  totalImportElements = 0;
  loadingHistory = false;

  private readonly importService = inject(ImportService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly metrics = inject(FeedsMetricsService);
  private readonly events = inject(FeedsEventsService);

  constructor() {
    this.activeImports$ = this.importService.getActiveImportsObservable();
  }

  ngOnInit(): void {
    this.loadImportHistory();
    this.importService.refreshActiveImports();
    this.events.refresh$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.loadImportHistory(this.importHistoryPage);
      this.importService.refreshActiveImports();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get hasActiveImports(): boolean {
    return false;
  }

  loadImportHistory(page: number = 0): void {
    this.loadingHistory = true;
    this.importHistoryPage = page;

    this.importService.getAllImportHistory({
      page,
      size: this.importHistorySize
    }).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (response) => {
        this.importHistory = response.imports;
        this.totalImportPages = response.totalPages;
        this.totalImportElements = response.totalElements;
        this.metrics.setTotalImportElements(response.totalElements);
        this.loadingHistory = false;
      },
      error: (error) => {
        console.error('Failed to load import history:', error);
        this.loadingHistory = false;
        this.snackBar.open('Failed to load import history', 'Close', { duration: 4000 });
      }
    });
  }

  cancelImport(importId: string): void {
    this.snackBar.open('Cancelling import...', 'Close', { duration: 2000 });

    this.importService.cancelImport(importId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.snackBar.open('✅ Import cancelled successfully', 'Close', { duration: 4000 });
        this.importService.refreshActiveImports();
        this.loadImportHistory(this.importHistoryPage);
      },
      error: (error) => {
        console.error('Failed to cancel import:', error);
        const errorMessage = error.message || error.error?.message || 'Unknown error occurred';
        this.snackBar.open(`❌ Failed to cancel import: ${errorMessage}`, 'Retry', {
          duration: 8000,
          panelClass: ['error-snackbar']
        }).onAction().subscribe(() => this.cancelImport(importId));
      }
    });
  }
}
