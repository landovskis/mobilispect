import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, takeUntil, catchError, of } from 'rxjs';
import { FeedAuthenticationService } from '../services/feed-authentication.service';
import {
  AuthType,
  FeedAuthentication,
  FeedAuthenticationRequest,
  AuthenticationTestResult,
  AuthenticationFormData
} from '../models/feed-authentication.model';

@Component({
  selector: 'app-feed-authentication',
  standalone: false,
  template: `
    <div class="feed-authentication-panel">
      <div class="panel-header">
        <h3>Feed Authentication</h3>
        <div class="header-actions">
          <button
            class="btn btn-outline"
            (click)="testAuthentication()"
            [disabled]="!hasAuthentication || testing">
            <span *ngIf="testing" class="spinner"></span>
            {{ testing ? 'Testing...' : 'Test Connection' }}
          </button>
          <button
            class="btn btn-primary"
            (click)="saveAuthentication()"
            [disabled]="authForm.invalid || saving">
            <span *ngIf="saving" class="spinner"></span>
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </div>

      <div class="panel-content">
        <!-- Authentication Status -->
        <div *ngIf="authentication" class="auth-status" [ngClass]="getStatusClass()">
          <div class="status-indicator"></div>
          <div class="status-info">
            <span class="status-text">{{ getStatusText() }}</span>
            <small class="status-details">{{ getStatusDetails() }}</small>
          </div>
          <div class="status-actions">
            <button
              *ngIf="authentication.failureCount > 0"
              class="btn btn-sm btn-outline"
              (click)="resetFailures()">
              Reset Failures
            </button>
            <button
              class="btn btn-sm"
              [ngClass]="authentication.isActive ? 'btn-warning' : 'btn-success'"
              (click)="toggleActive()">
              {{ authentication.isActive ? 'Deactivate' : 'Activate' }}
            </button>
          </div>
        </div>

        <!-- Test Results -->
        <div *ngIf="testResult" class="test-result" [ngClass]="testResult.success ? 'success' : 'error'">
          <div class="result-icon">
            <i [class]="testResult.success ? 'icon-check' : 'icon-x'"></i>
          </div>
          <div class="result-content">
            <div class="result-message">{{ testResult.message }}</div>
            <div *ngIf="testResult.responseCode" class="result-details">
              Response Code: {{ testResult.responseCode }}
              <span *ngIf="testResult.responseTime"> | {{ testResult.responseTime }}ms</span>
            </div>
            <div *ngIf="testResult.error" class="result-error">{{ testResult.error }}</div>
          </div>
        </div>

        <!-- Authentication Form -->
        <form [formGroup]="authForm" class="auth-form">
          <!-- Authentication Type -->
          <div class="form-group">
            <label for="authType">Authentication Type</label>
            <select
              id="authType"
              formControlName="authType"
              class="form-control"
              (change)="onAuthTypeChange()">
              <option value="NONE">No Authentication</option>
              <option value="BASIC">Basic Authentication</option>
              <option value="BEARER_TOKEN">Bearer Token</option>
              <option value="API_KEY">API Key</option>
              <option value="OAUTH2">OAuth2</option>
              <option value="CERTIFICATE">Client Certificate</option>
            </select>
          </div>

          <!-- Basic Authentication -->
          <div *ngIf="authForm.get('authType')?.value === 'BASIC'" class="auth-fields">
            <div class="form-group">
              <label for="username">Username</label>
              <input
                id="username"
                type="text"
                formControlName="username"
                class="form-control"
                placeholder="Enter username">
            </div>
            <div class="form-group">
              <label for="password">Password</label>
              <input
                id="password"
                type="password"
                formControlName="password"
                class="form-control"
                placeholder="Enter password">
            </div>
          </div>

          <!-- Bearer Token -->
          <div *ngIf="authForm.get('authType')?.value === 'BEARER_TOKEN'" class="auth-fields">
            <div class="form-group">
              <label for="token">Bearer Token</label>
              <textarea
                id="token"
                formControlName="token"
                class="form-control"
                rows="3"
                placeholder="Enter bearer token"></textarea>
            </div>
          </div>

          <!-- API Key -->
          <div *ngIf="authForm.get('authType')?.value === 'API_KEY'" class="auth-fields">
            <div class="form-group">
              <label for="apiKey">API Key</label>
              <input
                id="apiKey"
                type="password"
                formControlName="apiKey"
                class="form-control"
                placeholder="Enter API key">
            </div>
            <div class="form-group">
              <label for="apiKeyHeader">Header Name (optional)</label>
              <input
                id="apiKeyHeader"
                type="text"
                formControlName="apiKeyHeader"
                class="form-control"
                placeholder="X-API-Key">
            </div>
          </div>

          <!-- OAuth2 -->
          <div *ngIf="authForm.get('authType')?.value === 'OAUTH2'" class="auth-fields">
            <div class="form-group">
              <label for="accessToken">Access Token</label>
              <textarea
                id="accessToken"
                formControlName="accessToken"
                class="form-control"
                rows="3"
                placeholder="Enter access token"></textarea>
            </div>
            <div class="form-group">
              <label for="refreshToken">Refresh Token (optional)</label>
              <input
                id="refreshToken"
                type="password"
                formControlName="refreshToken"
                class="form-control"
                placeholder="Enter refresh token">
            </div>
          </div>

          <!-- Certificate -->
          <div *ngIf="authForm.get('authType')?.value === 'CERTIFICATE'" class="auth-fields">
            <div class="form-group">
              <label for="certificatePath">Certificate Path</label>
              <input
                id="certificatePath"
                type="text"
                formControlName="certificatePath"
                class="form-control"
                placeholder="Enter certificate file path">
            </div>
          </div>

          <!-- Expiration -->
          <div *ngIf="authForm.get('authType')?.value !== 'NONE'" class="form-group">
            <label for="expiresAt">Expiration Date (optional)</label>
            <input
              id="expiresAt"
              type="datetime-local"
              formControlName="expiresAt"
              class="form-control">
          </div>

          <!-- Notes -->
          <div *ngIf="authForm.get('authType')?.value !== 'NONE'" class="form-group">
            <label for="notes">Notes (optional)</label>
            <textarea
              id="notes"
              formControlName="notes"
              class="form-control"
              rows="2"
              placeholder="Additional notes about this authentication setup"></textarea>
          </div>
        </form>

        <!-- Delete Authentication -->
        <div *ngIf="hasAuthentication" class="danger-zone">
          <h4>Danger Zone</h4>
          <p>Removing authentication will delete all stored credentials for this feed.</p>
          <button
            class="btn btn-danger"
            (click)="deleteAuthentication()"
            [disabled]="deleting">
            <span *ngIf="deleting" class="spinner"></span>
            {{ deleting ? 'Deleting...' : 'Remove Authentication' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./feed-authentication.component.scss']
})
export class FeedAuthenticationComponent implements OnInit, OnDestroy {
  @Input() feedOnestopId!: string;

  authForm!: FormGroup;
  authentication: FeedAuthentication | null = null;
  testResult: AuthenticationTestResult | null = null;

  loading = false;
  saving = false;
  testing = false;
  deleting = false;

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private authService: FeedAuthenticationService
  ) {
    this.initializeForm();
  }

  ngOnInit(): void {
    this.loadAuthentication();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get hasAuthentication(): boolean {
    return this.authentication !== null;
  }

  private initializeForm(): void {
    this.authForm = this.fb.group({
      authType: ['NONE', Validators.required],
      username: [''],
      password: [''],
      token: [''],
      apiKey: [''],
      apiKeyHeader: ['X-API-Key'],
      certificatePath: [''],
      accessToken: [''],
      refreshToken: [''],
      expiresAt: [''],
      notes: ['']
    });
  }

  private loadAuthentication(): void {
    this.loading = true;

    this.authService.getAuthentication(this.feedOnestopId)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          // No authentication found, that's okay
          return of(null);
        })
      )
      .subscribe({
        next: (auth) => {
          this.authentication = auth;
          if (auth) {
            this.populateForm(auth);
          }
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        }
      });
  }

  private populateForm(auth: FeedAuthentication): void {
    this.authForm.patchValue({
      authType: auth.authType,
      expiresAt: auth.expiresAt ? new Date(auth.expiresAt).toISOString().slice(0, 16) : '',
      notes: auth.notes || ''
    });
  }

  onAuthTypeChange(): void {
    const authType = this.authForm.get('authType')?.value;
    this.resetFormFields();
    this.updateValidators(authType);
    this.testResult = null;
  }

  private resetFormFields(): void {
    const fieldsToReset = ['username', 'password', 'token', 'apiKey', 'certificatePath', 'accessToken', 'refreshToken'];
    fieldsToReset.forEach(field => {
      this.authForm.get(field)?.setValue('');
    });
  }

  private updateValidators(authType: string): void {
    // Clear all validators first
    Object.keys(this.authForm.controls).forEach(key => {
      if (key !== 'authType') {
        this.authForm.get(key)?.clearValidators();
      }
    });

    // Add validators based on auth type
    switch (authType) {
      case 'BASIC':
        this.authForm.get('username')?.setValidators([Validators.required]);
        this.authForm.get('password')?.setValidators([Validators.required]);
        break;
      case 'BEARER_TOKEN':
        this.authForm.get('token')?.setValidators([Validators.required]);
        break;
      case 'API_KEY':
        this.authForm.get('apiKey')?.setValidators([Validators.required]);
        break;
      case 'OAUTH2':
        this.authForm.get('accessToken')?.setValidators([Validators.required]);
        break;
      case 'CERTIFICATE':
        this.authForm.get('certificatePath')?.setValidators([Validators.required]);
        break;
    }

    // Update validity
    Object.keys(this.authForm.controls).forEach(key => {
      this.authForm.get(key)?.updateValueAndValidity();
    });
  }

  saveAuthentication(): void {
    if (this.authForm.invalid) return;

    this.saving = true;
    const formData = this.authForm.value as AuthenticationFormData;
    const request = this.buildAuthRequest(formData);

    this.authService.createOrUpdateAuthentication(this.feedOnestopId, request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (auth) => {
          this.authentication = auth;
          this.saving = false;
          this.testResult = null;
        },
        error: () => {
          this.saving = false;
        }
      });
  }

  private buildAuthRequest(formData: AuthenticationFormData): FeedAuthenticationRequest {
    const request: FeedAuthenticationRequest = {
      authType: formData.authType as AuthType,
      expiresAt: formData.expiresAt ? new Date(formData.expiresAt).toISOString() : undefined,
      notes: formData.notes || undefined
    };

    switch (formData.authType) {
      case 'BASIC':
        request.primaryCredential = formData.username;
        request.secondaryCredential = formData.password;
        break;
      case 'BEARER_TOKEN':
        request.primaryCredential = formData.token;
        break;
      case 'API_KEY':
        request.primaryCredential = formData.apiKey;
        if (formData.apiKeyHeader && formData.apiKeyHeader !== 'X-API-Key') {
          request.authParameters = { headerName: formData.apiKeyHeader };
        }
        break;
      case 'OAUTH2':
        request.primaryCredential = formData.accessToken;
        request.secondaryCredential = formData.refreshToken;
        break;
      case 'CERTIFICATE':
        request.primaryCredential = formData.certificatePath;
        break;
    }

    return request;
  }

  testAuthentication(): void {
    if (!this.hasAuthentication) return;

    this.testing = true;
    this.testResult = null;

    this.authService.testAuthentication(this.feedOnestopId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (result) => {
          this.testResult = result;
          this.testing = false;
        },
        error: () => {
          this.testing = false;
        }
      });
  }

  resetFailures(): void {
    this.authService.resetFailures(this.feedOnestopId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.loadAuthentication();
        }
      });
  }

  toggleActive(): void {
    if (!this.authentication) return;

    const newActiveState = !this.authentication.isActive;

    this.authService.setAuthenticationActive(this.feedOnestopId, newActiveState)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.loadAuthentication();
        }
      });
  }

  deleteAuthentication(): void {
    if (!confirm('Are you sure you want to remove authentication for this feed?')) {
      return;
    }

    this.deleting = true;

    this.authService.deleteAuthentication(this.feedOnestopId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.authentication = null;
          this.authForm.reset({ authType: 'NONE' });
          this.testResult = null;
          this.deleting = false;
        },
        error: () => {
          this.deleting = false;
        }
      });
  }

  getStatusClass(): string {
    if (!this.authentication) return '';

    if (!this.authentication.isActive) return 'status-inactive';
    if (this.authentication.failureCount >= 5) return 'status-locked';
    if (this.authentication.expiresAt && new Date(this.authentication.expiresAt) < new Date()) return 'status-expired';
    return 'status-active';
  }

  getStatusText(): string {
    if (!this.authentication) return '';

    if (!this.authentication.isActive) return 'Inactive';
    if (this.authentication.failureCount >= 5) return 'Locked';
    if (this.authentication.expiresAt && new Date(this.authentication.expiresAt) < new Date()) return 'Expired';
    return 'Active';
  }

  getStatusDetails(): string {
    if (!this.authentication) return '';

    const details = [];

    if (this.authentication.failureCount > 0) {
      details.push(`${this.authentication.failureCount} failures`);
    }

    if (this.authentication.expiresAt) {
      const expiresDate = new Date(this.authentication.expiresAt);
      const now = new Date();
      const daysUntilExpiry = Math.ceil((expiresDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));

      if (daysUntilExpiry < 0) {
        details.push('Expired');
      } else if (daysUntilExpiry <= 7) {
        details.push(`Expires in ${daysUntilExpiry} days`);
      }
    }

    return details.join(' • ');
  }
}
