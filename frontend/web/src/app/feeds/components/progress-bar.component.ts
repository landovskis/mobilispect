import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface ProgressConfig {
  percentage: number;
  color?: 'primary' | 'accent' | 'warn';
  mode?: 'determinate' | 'indeterminate';
  showPercentage?: boolean;
  showIcon?: boolean;
  height?: number;
  animated?: boolean;
}

@Component({
  selector: 'app-progress-bar',
  standalone: true,
  imports: [
    CommonModule,
    MatProgressBarModule,
    MatIconModule,
    MatTooltipModule
  ],
  template: `
    <div class="progress-container" [style.height.px]="config.height || 20">
      <!-- Progress Bar -->
      <mat-progress-bar
        [mode]="config.mode || 'determinate'"
        [value]="config.percentage"
        [color]="config.color || 'primary'"
        [class.animated]="config.animated"
        [style.height.px]="config.height || 20">
      </mat-progress-bar>

      <!-- Overlay Content -->
      <div class="progress-overlay">
        <!-- Status Icon -->
        <div class="progress-icon" *ngIf="config.showIcon">
          <mat-icon
            [matTooltip]="getStatusTooltip()"
            [ngClass]="getIconClass()">
            {{ getStatusIcon() }}
          </mat-icon>
        </div>

        <!-- Percentage Text -->
        <div class="progress-text" *ngIf="config.showPercentage && config.mode === 'determinate'">
          <span [ngClass]="getTextClass()">{{ config.percentage }}%</span>
        </div>

        <!-- Loading Text -->
        <div class="progress-text" *ngIf="config.mode === 'indeterminate'">
          <span class="loading-text">Processing...</span>
        </div>
      </div>

      <!-- Progress Segments (for step-based progress) -->
      <div class="progress-segments" *ngIf="totalSteps && totalSteps > 1">
        <div
          *ngFor="let step of getStepsArray(); let i = index"
          class="progress-segment"
          [ngClass]="{
            'completed': i < getCompletedSteps(),
            'active': i === getCompletedSteps(),
            'pending': i > getCompletedSteps()
          }"
          [style.width.%]="100 / totalSteps"
          [matTooltip]="'Step ' + (i + 1) + ' of ' + totalSteps">
        </div>
      </div>
    </div>
  `,
  styles: [`
    .progress-container {
      position: relative;
      width: 100%;
      border-radius: 4px;
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }

    .progress-overlay {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 8px;
      pointer-events: none;
    }

    .progress-icon {
      display: flex;
      align-items: center;
      z-index: 2;
    }

    .progress-text {
      display: flex;
      align-items: center;
      z-index: 2;
      font-size: 12px;
      font-weight: 500;
    }

    .progress-segments {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      display: flex;
      z-index: 1;
    }

    .progress-segment {
      border-right: 1px solid rgba(255,255,255,0.3);
      position: relative;
    }

    .progress-segment:last-child {
      border-right: none;
    }

    .progress-segment.completed {
      background-color: rgba(76, 175, 80, 0.2);
    }

    .progress-segment.active {
      background-color: rgba(33, 150, 243, 0.3);
      animation: pulse 1.5s infinite;
    }

    .progress-segment.pending {
      background-color: rgba(158, 158, 158, 0.1);
    }

    /* Icon states */
    .icon-spinning {
      animation: spin 1s linear infinite;
    }

    .icon-success {
      color: #4caf50;
    }

    .icon-error {
      color: #f44336;
    }

    .icon-warning {
      color: #ff9800;
    }

    .icon-info {
      color: #2196f3;
    }

    /* Text colors */
    .text-light {
      color: white;
      text-shadow: 0 1px 2px rgba(0,0,0,0.5);
    }

    .text-dark {
      color: #333;
    }

    .loading-text {
      color: #2196f3;
      animation: fade 1.5s infinite alternate;
    }

    /* Animations */
    .animated {
      transition: all 0.3s ease-in-out;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    @keyframes pulse {
      0%, 100% { opacity: 0.6; }
      50% { opacity: 1; }
    }

    @keyframes fade {
      0% { opacity: 0.6; }
      100% { opacity: 1; }
    }

    /* Material Design color overrides */
    ::ng-deep .mat-progress-bar-fill::after {
      background-color: currentColor;
    }

    ::ng-deep .mat-progress-bar-buffer {
      background-color: rgba(255,255,255,0.3);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProgressBarComponent {
  @Input() config: ProgressConfig = { percentage: 0 };
  @Input() totalSteps?: number;
  @Input() currentStep?: number;

  getStatusIcon(): string {
    const percentage = this.config.percentage;
    const mode = this.config.mode;

    if (mode === 'indeterminate') return 'sync';
    if (percentage >= 100) return 'check_circle';
    if (percentage >= 75) return 'trending_up';
    if (percentage >= 25) return 'schedule';
    return 'hourglass_empty';
  }

  getIconClass(): string {
    const percentage = this.config.percentage;
    const mode = this.config.mode;

    if (mode === 'indeterminate') return 'icon-info icon-spinning';
    if (percentage >= 100) return 'icon-success';
    if (percentage >= 75) return 'icon-info';
    if (percentage >= 25) return 'icon-warning';
    return 'icon-info';
  }

  getTextClass(): string {
    const percentage = this.config.percentage;
    // Use light text for higher progress percentages (darker background)
    return percentage > 50 ? 'text-light' : 'text-dark';
  }

  getStatusTooltip(): string {
    const percentage = this.config.percentage;
    const mode = this.config.mode;

    if (mode === 'indeterminate') return 'Processing...';
    if (percentage >= 100) return 'Complete!';
    if (percentage >= 75) return 'Almost done';
    if (percentage >= 50) return 'More than halfway';
    if (percentage >= 25) return 'Making progress';
    return 'Just getting started';
  }

  getStepsArray(): number[] {
    return Array(this.totalSteps || 0).fill(0).map((_, i) => i);
  }

  getCompletedSteps(): number {
    if (!this.totalSteps) return 0;

    if (this.currentStep !== undefined) {
      return this.currentStep;
    }

    // Calculate based on percentage
    return Math.floor((this.config.percentage / 100) * this.totalSteps);
  }
}
