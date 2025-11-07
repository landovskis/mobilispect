import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ImportConfirmationData {
  regionName: string;
  feedCount: number;
}

@Component({
  selector: 'app-import-confirmation-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule
  ],
  template: `
    <div class="import-dialog">
      <h2 mat-dialog-title>
        <mat-icon>download</mat-icon>
        Start Feed Import
      </h2>

      <mat-dialog-content>
        <p>You are about to start importing feeds from <strong>{{ data.regionName }}</strong>.</p>
        <p *ngIf="data.feedCount > 0">This region has <strong>{{ data.feedCount }} feeds</strong> available.</p>
        <p>The import will run in the background and you can monitor its progress in the Active Imports view.</p>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button (click)="onCancel()">Cancel</button>
        <button mat-raised-button color="primary" (click)="onStartImport()">
          <mat-icon>download</mat-icon>
          Start Import
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .import-dialog h2 {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    mat-dialog-content {
      padding: 20px 0;
    }

    mat-dialog-content p {
      margin-bottom: 12px;
    }

    mat-dialog-actions {
      padding: 16px 0;
    }
  `]
})
export class ImportConfirmationDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ImportConfirmationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ImportConfirmationData
  ) {}

  onCancel(): void {
    this.dialogRef.close(false);
  }

  onStartImport(): void {
    this.dialogRef.close(true);
  }
}
