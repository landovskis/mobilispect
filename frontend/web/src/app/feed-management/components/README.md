# Feed Management Components

This directory contains the extracted, modular components for the Feed Management feature.

## Component Architecture

The feed management UI is composed of several focused, reusable components:

### Tab Components

#### FeedHistoryTabComponent
**Location**: `feed-history-tab.component.ts`
**Purpose**: Orchestrates the display of active imports and import history
**Type**: Container/Orchestrator Component

Composed of:
- `ActiveImportsListComponent` - Displays running imports with progress
- `ImportHistoryTableComponent` - Displays completed imports table

**Usage**:
```typescript
<app-feed-history-tab
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
  (pageChange)="loadImportHistory($event)">
</app-feed-history-tab>
```

#### FeedRegionsTabComponent
**Location**: `feed-regions-tab.component.ts`
**Purpose**: Displays available metropolitan regions with feed counts
**Type**: Presentation Component

**Usage**:
```typescript
<app-feed-regions-tab
  [regions]="regions"
  (importRegion)="importFeedsForRegion($event)">
</app-feed-regions-tab>
```

---

### Feature Components

#### ActiveImportsListComponent
**Location**: `active-imports-list.component.ts`
**Purpose**: Displays currently running imports with real-time progress
**Type**: Presentation Component with embedded ProgressMonitor

**Features**:
- Real-time progress monitoring via WebSocket
- Bulk selection of imports
- Individual and bulk cancellation
- Responsive card layout

**Usage**:
```typescript
<app-active-imports-list
  [activeImports$]="activeImports$"
  [selectedImportIds]="selectedIds"
  (selectionChange)="handleSelection($event)"
  (bulkCancel)="cancelSelected()"
  (cancelImport)="cancelOne($event)">
</app-active-imports-list>
```

**Inputs**:
- `activeImports$: Observable<FeedImportSummary[]>` - Stream of active imports
- `selectedImportIds: Set<string>` - Currently selected import IDs

**Outputs**:
- `selectionChange: EventEmitter<{id: string, selected: boolean}>` - Import selection changed
- `bulkCancel: EventEmitter<void>` - Bulk cancellation requested
- `cancelImport: EventEmitter<string>` - Single import cancellation

---

#### ImportHistoryTableComponent
**Location**: `import-history-table.component.ts`
**Purpose**: Displays completed imports in a paginated table
**Type**: Presentation Component

**Features**:
- Material Design table with sorting
- Pagination with configurable page sizes
- Status badges with icons
- File size formatting
- Loading and empty states

**Usage**:
```typescript
<app-import-history-table
  [loading]="isLoading"
  [history]="imports"
  [totalItems]="total"
  [pageIndex]="0"
  [pageSize]="20"
  [pageSizeOptions]="[10, 20, 50, 100]"
  [displayedColumns]="['feedName', 'region', 'status', 'startedAt', 'completedAt', 'fileSize']"
  [showHeader]="true"
  (pageChange)="loadPage($event)">
</app-import-history-table>
```

**Inputs**:
- `loading: boolean` - Show loading spinner
- `history: FeedImportSummary[] | null` - Import history data
- `totalItems: number` - Total number of items for pagination
- `pageIndex: number` - Current page index (0-based)
- `pageSize: number` - Items per page
- `pageSizeOptions: number[]` - Available page size options
- `displayedColumns: string[]` - Columns to display
- `showHeader: boolean` - Show "Completed Imports" header

**Outputs**:
- `pageChange: EventEmitter<number>` - Page index changed

---

#### ProgressMonitorComponent
**Location**: `progress-monitor.component.ts`
**Purpose**: Real-time progress monitoring for individual imports
**Type**: Presentation Component with WebSocket integration

**Features**:
- Real-time progress updates via WebSocket
- Progress percentage and step tracking
- Duration and estimated completion time
- Cancellation support
- Connection status indicator

**Usage**:
```typescript
<app-progress-monitor
  [importId]="import.id"
  [showActions]="true"
  [showConnectionStatus]="false"
  (cancelRequested)="handleCancel($event)">
</app-progress-monitor>
```

---

#### RegionSelectorComponent
**Location**: `region-selector.component.ts`
**Purpose**: Dropdown selector for choosing metropolitan regions
**Type**: Form Component

**Usage**:
```typescript
<app-region-selector
  [regions]="regions"
  [selectedRegionId]="selectedId"
  [disabled]="false"
  (regionChange)="onRegionChange($event)">
</app-region-selector>
```

---

## Design Principles

### 1. Single Responsibility
Each component has a clear, focused purpose:
- **ActiveImportsListComponent**: Only handles active import display and interaction
- **ImportHistoryTableComponent**: Only handles completed import table display
- **FeedHistoryTabComponent**: Only orchestrates the two sub-components

### 2. Composition Over Inheritance
Components are composed together rather than extending base classes:
```
FeedHistoryTabComponent (orchestrator)
├── ActiveImportsListComponent (feature)
│   └── ProgressMonitorComponent (widget)
└── ImportHistoryTableComponent (feature)
```

### 3. OnPush Change Detection
All components use `ChangeDetectionStrategy.OnPush` for optimal performance with Observable inputs.

### 4. Standalone Components
All components are standalone (not requiring NgModule) for better tree-shaking and lazy loading.

### 5. Input/Output Pattern
Clear component boundaries with well-defined inputs and outputs:
- `@Input()` for data flow down
- `@Output()` for events flow up
- Observables for reactive data streams

---

## Testing Strategy

### Unit Tests
Each component should have:
- **Input validation tests**: Verify component handles null/undefined inputs
- **Output emission tests**: Verify events are emitted correctly
- **Render tests**: Verify correct DOM structure
- **Interaction tests**: Verify user interactions trigger correct outputs

### Integration Tests
Test component composition:
- `FeedHistoryTabComponent` integration with sub-components
- Event propagation through component hierarchy
- Observable data flow

---

## Migration Guide

### Before (Monolithic)
```typescript
// FeedHistoryTabComponent had 430+ lines handling:
// - Active imports display
// - Progress monitoring
// - Import history table
// - Pagination
// - Bulk selection
// - All styling
```

### After (Modular)
```typescript
// FeedHistoryTabComponent: 118 lines (orchestration only)
// ActiveImportsListComponent: ~270 lines (active imports)
// ImportHistoryTableComponent: ~260 lines (history table)
// ProgressMonitorComponent: ~390 lines (progress tracking)
```

**Benefits**:
- ✅ Easier to test (smaller units)
- ✅ Easier to reuse (focused components)
- ✅ Easier to maintain (clear boundaries)
- ✅ Better performance (targeted re-renders)
- ✅ Better documentation (component-level docs)

---

## Future Enhancements

### Potential Extractions
1. **StatusBadgeComponent**: Reusable status badge with icon
2. **FileSize Pipe**: Format file sizes consistently
3. **EmptyStateComponent**: Reusable empty state with icon and message

### Feature Additions
1. **Export functionality**: Export import history to CSV/JSON
2. **Advanced filtering**: Filter imports by status, date range, region
3. **Sorting**: Sortable columns in history table
4. **Search**: Search imports by feed name or region

---

## References

- [Angular Component Interaction](https://angular.io/guide/component-interaction)
- [Angular OnPush Strategy](https://angular.io/api/core/ChangeDetectionStrategy)
- [Material Design Components](https://material.angular.io/components)
- [RxJS Observables](https://rxjs.dev/guide/observable)
