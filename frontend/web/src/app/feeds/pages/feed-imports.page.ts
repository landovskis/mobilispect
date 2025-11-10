import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule } from '@angular/material/dialog';
import { Observable, Subject } from 'rxjs';
import { takeUntil, take } from 'rxjs/operators';
import { FeedImportSummary } from '../models/import.models';
import { ImportService } from '../services/import.service';
import { FeedImportsTabComponent } from '../components/feed-imports-tab.component';
import { FeedsMetricsService } from '../services/feeds-metrics.service';
import { FeedsEventsService } from '../services/feeds-events.service';

@Component({
  selector: 'app-feed-imports-page',
  standalone: true,
  imports: [
    CommonModule,
    MatSnackBarModule,
    MatDialogModule,
    FeedImportsTabComponent
  ],
  template: `
    <app-feed-imports-tab
      [loading]="loadingHistory"
      [history]="importHistory"
      [totalItems]="totalImportElements"
      [pageIndex]="importHistoryPage"
      [pageSize]="importHistorySize"
      [activeImports$]="activeImports$"
      [selectedImportIds]="selectedImportIds"
      [allImportsSelected]="allImportsSelected"
      [someImportsSelected]="someImportsSelected"
      (selectAllChange)="toggleAllImports($event)"
      (selectionChange)="toggleImportSelection($event.id, $event.selected)"
      (bulkCancel)="bulkCancelImports()"
      (cancelImport)="cancelImport($event)"
      (pageChange)="loadImportHistory($event)"
    ></app-feed-imports-tab>
  `
})
export class FeedImportsPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  activeImports$: Observable<FeedImportSummary[]>;

  importHistory: FeedImportSummary[] = [];
  importHistoryPage = 0;
  importHistorySize = 20;
  totalImportPages = 0;
  totalImportElements = 0;
  loadingHistory = false;

  selectedImportIds = new Set<string>();
  allImportsSelected = false;
  someImportsSelected = false;

  constructor(
    private readonly importService: ImportService,
    private readonly snackBar: MatSnackBar,
    private readonly metrics: FeedsMetricsService,
    private readonly events: FeedsEventsService
  ) {
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
        this.importHistory = response.imports as any[];
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

  toggleImportSelection(importId: string, selected: boolean): void {
    if (selected) {
      this.selectedImportIds.add(importId);
    } else {
      this.selectedImportIds.delete(importId);
    }
    this.updateSelectionState();
  }

  toggleAllImports(selectAll: boolean): void {
    this.selectedImportIds.clear();
    if (selectAll) {
      this.activeImports$.pipe(take(1)).subscribe(imports => {
        (imports || []).forEach(imp => this.selectedImportIds.add(imp.id));
        this.updateSelectionState();
      });
    } else {
      this.updateSelectionState();
    }
  }

  bulkCancelImports(): void {
    const importIds = Array.from(this.selectedImportIds);
    if (!importIds.length) {
      return;
    }

    const message = `Are you sure you want to cancel ${importIds.length} import(s)?`;
    if (!confirm(message)) {
      return;
    }

    this.snackBar.open(`Cancelling ${importIds.length} imports...`, 'Close', { duration: 3000 });

    this.importService.bulkCancelImports(importIds).then(results => {
      const successCount = results.filter(r => r.status === 'COMPLETED').length;
      const failCount = results.length - successCount;

      if (failCount === 0) {
        this.snackBar.open(`✅ Successfully cancelled ${successCount} imports`, 'Close', { duration: 4000 });
      } else {
        this.snackBar.open(`⚠️ Cancelled ${successCount} imports, ${failCount} failed`, 'Close', { duration: 6000 });
      }

      this.selectedImportIds.clear();
      this.updateSelectionState();
      this.importService.refreshActiveImports();
      this.loadImportHistory(this.importHistoryPage);
    }).catch(error => {
      console.error('Bulk cancel failed:', error);
      this.snackBar.open(`❌ Bulk cancellation failed: ${error.message || 'Unknown error'}`, 'Close', { duration: 8000 });
    });
  }

  private updateSelectionState(): void {
    this.activeImports$.pipe(take(1)).subscribe(imports => {
      const list = imports || [];
      const total = list.length;
      const selected = this.selectedImportIds.size;
      this.allImportsSelected = selected > 0 && selected === total;
      this.someImportsSelected = selected > 0 && selected < total;
    });
  }
}
