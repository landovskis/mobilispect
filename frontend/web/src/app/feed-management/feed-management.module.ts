import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { MatChipsModule } from '@angular/material/chips';
import { MatBadgeModule } from '@angular/material/badge';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';

import { FeedManagementRoutingModule } from './feed-management-routing.module';
import { ThemeToggleComponent } from '../core/components/theme-toggle.component';
import { FeedManagementComponent } from './pages/feed-management.component';
import { RegionListComponent } from './components/region-list.component';
import { ImportDialogComponent } from './components/import-dialog.component';
import { ImportProgressDialogComponent } from './components/import-progress-dialog.component';
import { FeedAuthenticationComponent } from './components/feed-authentication.component';
import { AutoUpdateConfigComponent } from './components/auto-update-config.component';
import { ProgressBarComponent } from './components/progress-bar.component';
import { ProgressMonitorComponent } from './components/progress-monitor.component';
import { ScheduledJobsComponent } from './pages/scheduled-jobs.component';
import { RegionService } from './services/region.service';
import { ImportService } from './services/import.service';
import { FeedAuthenticationService } from './services/feed-authentication.service';

@NgModule({
  declarations: [
    FeedManagementComponent,
    FeedAuthenticationComponent,
    AutoUpdateConfigComponent,
    ScheduledJobsComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    FeedManagementRoutingModule,

    // Standalone components
    RegionListComponent,
    ImportDialogComponent,
    ImportProgressDialogComponent,
    ProgressBarComponent,
    ProgressMonitorComponent,
    ThemeToggleComponent,

    // Angular Material modules
    MatCardModule,
    MatButtonModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatDialogModule,
    MatSnackBarModule,
    MatIconModule,
    MatSelectModule,
    MatInputModule,
    MatFormFieldModule,
    MatPaginatorModule,
    MatSortModule,
    MatChipsModule,
    MatBadgeModule,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatTooltipModule,
    MatTabsModule,
    MatCheckboxModule,
    MatExpansionModule,
    MatDividerModule,
    MatMenuModule
  ],
  providers: [
    RegionService,
    ImportService,
    FeedAuthenticationService
  ]
})
export class FeedManagementModule { }
