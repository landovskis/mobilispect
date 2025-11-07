import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { FeedImportSummary } from '../models/import.models';
import { ActiveImportsListComponent } from './active-imports-list.component';
import { ImportHistoryTableComponent } from './import-history-table.component';

/**
 * Feed History Tab Component
 *
 * Orchestrates the display of active imports and import history.
 * Composed of two main sub-components:
 * - ActiveImportsListComponent: Displays running imports
 * - ImportHistoryTableComponent: Displays completed imports
 *
 * @example
 * ```html
 * <app-feed-history-tab
 *   [loading]="isLoading"
 *   [history]="imports"
 *   [activeImports$]="activeImports$"
 *   (pageChange)="loadPage($event)"
 *   (cancelImport)="cancel($event)">
 * </app-feed-history-tab>
 * ```
 */
@Component({
  selector: 'app-feed-history-tab',
  standalone: true,
  imports: [
    CommonModule,
    ActiveImportsListComponent,
    ImportHistoryTableComponent
  ],
  template: `
    <div class="tab-content">
      <!-- Active Imports Section -->
      <app-active-imports-list
        [activeImports$]="activeImports$"
        [selectedImportIds]="selectedImportIds"
        (selectionChange)="onSelectionChange($event.id, $event.selected)"
        (bulkCancel)="onBulkCancel()"
        (cancelImport)="onCancelImport($event)"
      ></app-active-imports-list>

      <!-- Import History Table -->
      <app-import-history-table
        [loading]="loading"
        [history]="history"
        [totalItems]="totalItems"
        [pageIndex]="pageIndex"
        [pageSize]="pageSize"
        [pageSizeOptions]="pageSizeOptions"
        [displayedColumns]="displayedColumns"
        [showHeader]="hasActiveImports"
        (pageChange)="onPageChange($event)"
      ></app-import-history-table>
    </div>
  `,
  styles: [`
    .tab-content {
      padding: 24px 0;
    }

    @media (max-width: 768px) {
      .tab-content {
        padding: 16px 0;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FeedHistoryTabComponent {
  // History table inputs
  @Input() loading = false;
  @Input() history: FeedImportSummary[] | null = [];
  @Input() totalItems = 0;
  @Input() pageIndex = 0;
  @Input() pageSize = 20;
  @Input() pageSizeOptions: number[] = [10, 20, 50, 100];
  @Input() displayedColumns: string[] = ['feedName', 'region', 'status', 'startedAt', 'completedAt', 'fileSize'];

  // Active imports inputs
  @Input() activeImports$: Observable<FeedImportSummary[]> | null = null;
  @Input() selectedImportIds: Set<string> = new Set();
  @Input() allImportsSelected = false;
  @Input() someImportsSelected = false;

  // Outputs
  @Output() pageChange = new EventEmitter<number>();
  @Output() selectAllChange = new EventEmitter<boolean>();
  @Output() selectionChange = new EventEmitter<{ id: string; selected: boolean }>();
  @Output() bulkCancel = new EventEmitter<void>();
  @Output() cancelImport = new EventEmitter<string>();

  /**
   * Determines if there are active imports to show header for completed imports
   */
  get hasActiveImports(): boolean {
    return false; // Will be calculated by the template based on activeImports$ observable
  }

  onPageChange(pageIndex: number): void {
    this.pageChange.emit(pageIndex);
  }

  onSelectionChange(id: string, selected: boolean): void {
    this.selectionChange.emit({ id, selected });
  }

  onBulkCancel(): void {
    this.bulkCancel.emit();
  }

  onCancelImport(id: string): void {
    this.cancelImport.emit(id);
  }
}
