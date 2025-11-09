import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, Subject, takeUntil } from 'rxjs';
import { SchedulerService } from '../services/scheduler.service';
import { AutoUpdateConfig, SchedulerStatus } from '../models/scheduler.model';

@Component({
  selector: 'app-auto-update-config',
  standalone: false,
  template: `
    <div class="auto-update-config">
      <app-mobilispect-card>
        <div card-header>
          <div card-title>Automatic Feed Updates Configuration</div>
          <div card-subtitle>Configure daily automatic feed update checks</div>
        </div>

        <div card-content>
          <!-- Scheduler Status -->
          <div class="status-section" *ngIf="schedulerStatus$ | async as status">
            <h3>Scheduler Status</h3>
            <div class="status-grid">
              <div class="status-item">
                <span class="label">Status:</span>
                <mat-chip [class.chip-success]="status.enabled" [class.chip-error]="!status.enabled">
                  {{ status.enabled ? 'Enabled' : 'Disabled' }}
                </mat-chip>
              </div>
              <div class="status-item">
                <span class="label">Active Feeds:</span>
                <span class="value">{{ status.totalActiveFeeds }}</span>
              </div>
              <div class="status-item">
                <span class="label">Checked in Last 24h:</span>
                <span class="value">{{ status.feedsCheckedInLast24Hours }}</span>
              </div>
              <div class="status-item">
                <span class="label">Next Run:</span>
                <span class="value">{{ status.nextScheduledRun }}</span>
              </div>
            </div>
          </div>

          <mat-divider class="divider"></mat-divider>

          <!-- Configuration Form -->
          <form [formGroup]="configForm" (ngSubmit)="saveConfiguration()">
            <h3>Global Settings</h3>

            <div class="form-row">
              <mat-checkbox formControlName="globalAutoUpdateEnabled">
                Enable automatic updates globally
              </mat-checkbox>
              <mat-hint>When disabled, no feeds will be automatically updated</mat-hint>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Default Check Interval (hours)</mat-label>
                <input matInput type="number" formControlName="defaultCheckIntervalHours" min="1" max="168">
                <mat-hint>How often to check each feed for updates (1-168 hours)</mat-hint>
                <mat-error *ngIf="configForm.get('defaultCheckIntervalHours')?.hasError('required')">
                  Check interval is required
                </mat-error>
                <mat-error *ngIf="configForm.get('defaultCheckIntervalHours')?.hasError('min')">
                  Minimum interval is 1 hour
                </mat-error>
                <mat-error *ngIf="configForm.get('defaultCheckIntervalHours')?.hasError('max')">
                  Maximum interval is 168 hours (1 week)
                </mat-error>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Max Concurrent Imports</mat-label>
                <input matInput type="number" formControlName="maxConcurrentImports" min="1" max="10">
                <mat-hint>Maximum number of feeds to import simultaneously</mat-hint>
                <mat-error *ngIf="configForm.get('maxConcurrentImports')?.hasError('required')">
                  Max concurrent imports is required
                </mat-error>
                <mat-error *ngIf="configForm.get('maxConcurrentImports')?.hasError('min')">
                  Must allow at least 1 concurrent import
                </mat-error>
                <mat-error *ngIf="configForm.get('maxConcurrentImports')?.hasError('max')">
                  Maximum 10 concurrent imports allowed
                </mat-error>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-checkbox formControlName="notifyOnFailures">
                Send notifications on import failures
              </mat-checkbox>
              <mat-hint>Email notifications will be sent to administrators</mat-hint>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Retry Failed Imports</mat-label>
                <mat-select formControlName="retryFailedImports">
                  <mat-option [value]="0">Never retry</mat-option>
                  <mat-option [value]="1">Retry once</mat-option>
                  <mat-option [value]="2">Retry twice</mat-option>
                  <mat-option [value]="3">Retry 3 times</mat-option>
                </mat-select>
                <mat-hint>Number of automatic retry attempts for failed imports</mat-hint>
              </mat-form-field>
            </div>

            <div class="actions">
              <button
                mat-raised-button
                color="primary"
                type="submit"
                [disabled]="configForm.invalid || saving">
                <mat-icon>save</mat-icon>
                {{ saving ? 'Saving...' : 'Save Configuration' }}
              </button>

              <button
                mat-button
                type="button"
                (click)="resetForm()"
                [disabled]="saving">
                Reset
              </button>

              <button
                mat-button
                color="accent"
                type="button"
                (click)="triggerManualCheck()"
                [disabled]="triggering">
                <mat-icon>refresh</mat-icon>
                {{ triggering ? 'Checking...' : 'Trigger Manual Check' }}
              </button>
            </div>
          </form>
        </div>
      </app-mobilispect-card>

      <!-- Recent Activity -->
      <app-mobilispect-card class="activity-card">
        <div card-header>
          <div card-title>Recent Automatic Import Activity</div>
        </div>
        <div card-content>
          <div *ngIf="importStats$ | async as stats" class="stats-grid">
            <div class="stat-item success">
              <span class="number">{{ stats.successfulImportsLast24h }}</span>
              <span class="label">Successful (24h)</span>
            </div>
            <div class="stat-item failed">
              <span class="number">{{ stats.failedImportsLast24h }}</span>
              <span class="label">Failed (24h)</span>
            </div>
            <div class="stat-item running">
              <span class="number">{{ stats.currentlyRunningAutoImports }}</span>
              <span class="label">Currently Running</span>
            </div>
            <div class="stat-item total">
              <span class="number">{{ stats.totalAutomaticImportsLast24h }}</span>
              <span class="label">Total (24h)</span>
            </div>
          </div>

          <div *ngIf="(importStats$ | async)?.lastAutomaticImportTime" class="last-run">
            <mat-icon>schedule</mat-icon>
            <span>Last automatic import: {{ (importStats$ | async)?.lastAutomaticImportTime | date:'medium' }}</span>
          </div>
        </div>
      </app-mobilispect-card>
    </div>
  `,
  styleUrls: ['./auto-update-config.component.scss']
})
export class AutoUpdateConfigComponent implements OnInit {
  configForm: FormGroup;
  schedulerStatus$: Observable<SchedulerStatus>;
  importStats$: Observable<any>;
  saving = false;
  triggering = false;

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private schedulerService: SchedulerService,
    private snackBar: MatSnackBar
  ) {
    this.configForm = this.createForm();
    this.schedulerStatus$ = this.schedulerService.getSchedulerStatus();
    this.importStats$ = this.schedulerService.getImportStats();
  }

  ngOnInit(): void {
    this.loadConfiguration();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private createForm(): FormGroup {
    return this.fb.group({
      globalAutoUpdateEnabled: [true],
      defaultCheckIntervalHours: [24, [Validators.required, Validators.min(1), Validators.max(168)]],
      maxConcurrentImports: [3, [Validators.required, Validators.min(1), Validators.max(10)]],
      notifyOnFailures: [true],
      retryFailedImports: [2, [Validators.required]]
    });
  }

  private loadConfiguration(): void {
    this.schedulerService.getAutoUpdateConfig()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (config) => {
          this.configForm.patchValue(config);
        },
        error: (error) => {
          console.error('Error loading configuration:', error);
          this.snackBar.open('Failed to load configuration', 'Close', { duration: 3000 });
        }
      });
  }

  saveConfiguration(): void {
    if (this.configForm.invalid) {
      return;
    }

    this.saving = true;
    const config: AutoUpdateConfig = this.configForm.value;

    this.schedulerService.updateAutoUpdateConfig(config)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.saving = false;
          this.snackBar.open('Configuration saved successfully', 'Close', { duration: 3000 });
        },
        error: (error) => {
          this.saving = false;
          console.error('Error saving configuration:', error);
          this.snackBar.open('Failed to save configuration', 'Close', { duration: 3000 });
        }
      });
  }

  resetForm(): void {
    this.loadConfiguration();
  }

  triggerManualCheck(): void {
    this.triggering = true;

    this.schedulerService.triggerManualCheck()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (result) => {
          this.triggering = false;
          this.snackBar.open(
            `Manual check completed: ${result.checkedCount} feeds checked, ${result.updatesTriggered} updates triggered`,
            'Close',
            { duration: 5000 }
          );

          // Refresh stats
          this.importStats$ = this.schedulerService.getImportStats();
        },
        error: (error) => {
          this.triggering = false;
          console.error('Error triggering manual check:', error);
          this.snackBar.open('Failed to trigger manual check', 'Close', { duration: 3000 });
        }
      });
  }
}
