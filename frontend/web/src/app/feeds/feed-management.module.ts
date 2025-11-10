import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
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
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';

import { FeedManagementRoutingModule } from './feed-management-routing.module';
import { ThemeToggleComponent } from '../core/components/theme-toggle.component';
import { AppBarComponent } from '../shared/components/app-bar.component';
import { RegionListComponent } from './components/region-list.component';
import { ProgressMonitorComponent } from './components/progress-monitor.component';
import { RegionService } from './services/region.service';
import { ImportService } from './services/import.service';
import { FeedAuthenticationService } from './services/feed-authentication.service';
import { FeedRegionsTabComponent } from './components/feed-regions-tab.component';
import { FeedActiveImportsTabComponent } from './components/feed-active-imports-tab.component';
import { FeedImportsTabComponent } from './components/feed-imports-tab.component';
import { RegionSelectorComponent } from './components/region-selector.component';
import { AgencyFeedCardComponent } from './components/agency-feed-card.component';
import { MobilispectCardComponent } from '../core/components/mobilispect-card.component';
import { FeedsShellComponent } from './pages/feeds-shell.component';
import { DiscoverFeedsPageComponent } from './pages/discover-feeds.page';
import { FeedImportsPageComponent } from './pages/feed-imports.page';
import { ScheduledJobsComponent } from './pages/scheduled-jobs.component';

@NgModule({
  declarations: [],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    FeedManagementRoutingModule,

    // Standalone components
    FeedsShellComponent,
    DiscoverFeedsPageComponent,
    FeedImportsPageComponent,
    RegionListComponent,
    RegionSelectorComponent,
    ProgressMonitorComponent,
    ThemeToggleComponent,
    AppBarComponent,
    FeedRegionsTabComponent,
    FeedActiveImportsTabComponent,
    FeedImportsTabComponent,
    AgencyFeedCardComponent,
    MobilispectCardComponent,
    ScheduledJobsComponent,

    // Angular Material modules
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
    MatTooltipModule,
    MatCheckboxModule,
    MatExpansionModule,
    MatDividerModule,
    MatMenuModule,
    MatSidenavModule
  ],
  providers: [
    RegionService,
    ImportService,
    FeedAuthenticationService
  ]
})
export class FeedManagementModule { }
