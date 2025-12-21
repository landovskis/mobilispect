import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import { firstValueFrom } from 'rxjs';

describe('AuthService', () => {
  let httpMock: HttpTestingController;

  const createToken = (payload: Record<string, unknown>): string => {
    const header = { alg: 'none', typ: 'JWT' };
    const encode = (value: Record<string, unknown>): string => btoa(JSON.stringify(value));
    return `${encode(header)}.${encode(payload)}.signature`;
  };

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService],
    });

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('hydrates a user from a valid stored token', async () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2024-06-01T12:00:00Z'));

    const nowSeconds = Math.floor(Date.now() / 1000);
    const token = createToken({
      sub: 'user-1',
      preferred_username: 'alex',
      email: 'alex@example.com',
      roles: ['FEED_MANAGER'],
      permissions: ['IMPORTS_VIEW'],
      exp: nowSeconds + 3600,
      iat: nowSeconds,
    });

    localStorage.setItem('auth_token', token);

    const service = TestBed.inject(AuthService);
    const user = await firstValueFrom(service.getCurrentUser());

    expect(user?.username).toBe('alex');
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.getHighestFeedRole()).toBe('FEED_MANAGER');
    expect(service.getAuthorizationHeader()).toBe(`Bearer ${token}`);

    jasmine.clock().uninstall();
  });

  it('clears auth state when token is expired', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2024-06-01T12:00:00Z'));

    const nowSeconds = Math.floor(Date.now() / 1000);
    const token = createToken({
      sub: 'user-2',
      preferred_username: 'expired',
      roles: ['FEED_VIEWER'],
      permissions: [],
      exp: nowSeconds - 10,
      iat: nowSeconds - 100,
    });

    localStorage.setItem('auth_token', token);

    const service = TestBed.inject(AuthService);
    expect(service.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem('auth_token')).toBeNull();

    jasmine.clock().uninstall();
  });

  it('logs in and stores the authenticated user', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2024-06-01T12:00:00Z'));

    const nowSeconds = Math.floor(Date.now() / 1000);
    const token = createToken({
      sub: 'user-3',
      preferred_username: 'sam',
      roles: ['FEED_OPERATOR'],
      permissions: ['IMPORTS_START'],
      exp: nowSeconds + 3600,
      iat: nowSeconds,
    });

    const service = TestBed.inject(AuthService);

    service.login('sam', 'secret').subscribe(user => {
      expect(user.username).toBe('sam');
      expect(service.hasRole(['FEED_OPERATOR'])).toBeTrue();
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush({ access_token: token, user: { username: 'sam' } });

    expect(localStorage.getItem('auth_token')).toBe(token);
    expect(localStorage.getItem('current_user')).toContain('sam');

    jasmine.clock().uninstall();
  });

  it('returns null authorization header when not authenticated', () => {
    const service = TestBed.inject(AuthService);

    expect(service.getAuthorizationHeader()).toBeNull();
    expect(service.hasRole(['FEED_MANAGER'])).toBeFalse();
    expect(service.hasPermission('IMPORTS_VIEW')).toBeFalse();
    expect(service.canViewImports()).toBeFalse();
  });

  it('selects the highest available role and permissions', () => {
    const service = TestBed.inject(AuthService);

    (service as any).currentUserSubject.next({
      id: 'user-4',
      username: 'viewer',
      roles: ['FEED_VIEWER'],
      permissions: ['IMPORTS_VIEW'],
      isAuthenticated: true,
    });

    expect(service.getHighestFeedRole()).toBe('FEED_VIEWER');
    expect(service.canViewImports()).toBeTrue();
    expect(service.canInitiateImports()).toBeFalse();
    expect(service.canCancelImports()).toBeFalse();

    (service as any).currentUserSubject.next({
      id: 'user-5',
      username: 'operator',
      roles: ['FEED_OPERATOR'],
      permissions: ['IMPORTS_START'],
      isAuthenticated: true,
    });

    expect(service.getHighestFeedRole()).toBe('FEED_OPERATOR');
    expect(service.canInitiateImports()).toBeTrue();
    expect(service.canManageAuthentication()).toBeFalse();

    (service as any).currentUserSubject.next({
      id: 'user-6',
      username: 'manager',
      roles: ['FEED_MANAGER'],
      permissions: ['IMPORTS_START', 'IMPORTS_VIEW'],
      isAuthenticated: true,
    });

    expect(service.getHighestFeedRole()).toBe('FEED_MANAGER');
    expect(service.canManageAuthentication()).toBeTrue();
    expect(service.canConfigureRegions()).toBeTrue();
  });

  it('hydrates username from fallback fields', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2024-06-01T12:00:00Z'));

    const nowSeconds = Math.floor(Date.now() / 1000);
    const token = createToken({
      sub: 'user-7',
      username: 'fallback',
      roles: [],
      permissions: [],
      exp: nowSeconds + 3600,
      iat: nowSeconds,
    });

    localStorage.setItem('auth_token', token);

    const service = TestBed.inject(AuthService);
    const user = (service as any).currentUserSubject.value;

    expect(user?.username).toBe('fallback');

    jasmine.clock().uninstall();
  });

  it('clears invalid tokens and reports unauthenticated state', () => {
    localStorage.setItem('auth_token', 'not-a-token');

    const service = TestBed.inject(AuthService);

    expect(service.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem('auth_token')).toBeNull();
  });

  it('refreshes and stores tokens, then logs out on refresh failure', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2024-06-01T12:00:00Z'));

    const nowSeconds = Math.floor(Date.now() / 1000);
    const token = createToken({
      sub: 'user-8',
      preferred_username: 'refresh',
      roles: ['FEED_VIEWER'],
      permissions: ['IMPORTS_VIEW'],
      exp: nowSeconds + 3600,
      iat: nowSeconds,
    });

    const service = TestBed.inject(AuthService);

    service.refreshToken().subscribe(updated => {
      expect(updated).toBe(token);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    req.flush({ access_token: token });

    expect(localStorage.getItem('auth_token')).toBe(token);

    service.refreshToken().subscribe({
      next: () => fail('Expected refresh failure'),
      error: () => {
        expect(localStorage.getItem('auth_token')).toBeNull();
      },
    });

    const errorReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    errorReq.flush({ message: 'bad' }, { status: 401, statusText: 'Unauthorized' });

    jasmine.clock().uninstall();
  });
});
