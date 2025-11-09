import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { FeedImportSummary } from '../models/import.models';
import { ActiveImportsCardComponent } from './active-imports-card.component';
import { ImportsHistoryCardComponent } from './imports-history-card.component';

/**
 * Feed Imports Tab Component
 *
 * Orchestrates the display of active imports and imports history.
 * Composed of two main card components:
 * - ActiveImportsCardComponent: Displays running imports
 * - ImportsHistoryCardComponent: Displays completed imports
 *
 * @example
 * ```html
 * <app-feed-imports-tab
 *   [loading]="isLoading"
 *   [history]="imports"
 *   [activeImports$]="activeImports$"
 *   (pageChange)="loadPage($event)"
 *   (cancelImport)="cancel($event)">
 * </app-feed-imports-tab>
 * ```
 */
@Component({
  selector: 'app-feed-imports-tab',
  standalone: true,
  imports: [
    CommonModule,
    ActiveImportsCardComponent,
    ImportsHistoryCardComponent
  ],
  template: `
    <div class="tab-content">
      <!-- Active Imports Card -->
      <app-active-imports-card
        [activeImports$]="activeImports$"
        [selectedImportIds]="selectedImportIds"
        (selectionChange)="onSelectionChange($event.id, $event.selected)"
        (bulkCancel)="onBulkCancel()"
        (cancelImport)="onCancelImport($event)"
      ></app-active-imports-card>

      <!-- Imports History Card -->
      <app-imports-history-card
        [loading]="loading"
        [history]="history"
        [totalItems]="totalItems"
        [pageIndex]="pageIndex"
        [pageSize]="pageSize"
        [pageSizeOptions]="pageSizeOptions"
        [displayedColumns]="displayedColumns"
        [showHeader]="hasActiveImports"
        (pageChange)="onPageChange($event)"
      ></app-imports-history-card>
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
export class FeedImportsTabComponent {
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
