import { TestBed } from '@angular/core/testing';
import { ThemeService, ThemePreference } from './theme.service';
import { vi } from 'vitest';

const buildMediaQueryStub = (
  matches: boolean,
  registerHandler?: (handler: (event: MediaQueryListEvent) => void) => void
): MediaQueryList => ({
  matches,
  media: '(prefers-color-scheme: dark)',
  onchange: null,
  addEventListener: (_eventName: string, handler: EventListenerOrEventListenerObject) =>
    registerHandler?.(handler as (event: MediaQueryListEvent) => void),
  removeEventListener: () => {},
  addListener: (handler: (event: MediaQueryListEvent) => void) => registerHandler?.(handler),
  removeListener: () => {},
  dispatchEvent: () => false,
});

describe('ThemeService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
    localStorage.clear();
  });

  it('prefers system color scheme when no stored preference', () => {
    vi.spyOn(window, 'matchMedia').mockReturnValue(buildMediaQueryStub(true) as MediaQueryList);

    const service = TestBed.inject(ThemeService);

    expect(service).toBeTruthy();
    expect(document.body.classList.contains('dark-theme')).toBe(true);
    expect(localStorage.getItem('mobilispect-theme')).toBeNull();
  });

  it('persists explicit theme preference and updates body classes', () => {
    vi.spyOn(window, 'matchMedia').mockReturnValue(buildMediaQueryStub(false) as MediaQueryList);

    const service = TestBed.inject(ThemeService);

    service.setPreference('dark');
    expect(localStorage.getItem('mobilispect-theme')).toBe('dark' as ThemePreference);
    expect(document.body.classList.contains('dark-theme')).toBe(true);

    service.setPreference('light');
    expect(localStorage.getItem('mobilispect-theme')).toBe('light' as ThemePreference);
    expect(document.body.classList.contains('dark-theme')).toBe(false);
    expect(document.body.classList.contains('light-theme')).toBe(true);
  });

  it('reacts to system preference changes while in system mode', () => {
    let changeHandler: ((event: MediaQueryListEvent) => void) | undefined;
    vi.spyOn(window, 'matchMedia').mockReturnValue(
      buildMediaQueryStub(false, (handler) => {
        changeHandler = handler;
      }) as MediaQueryList
    );

    TestBed.inject(ThemeService);
    expect(document.body.classList.contains('dark-theme')).toBe(false);

    changeHandler?.({ matches: true } as MediaQueryListEvent);
    expect(document.body.classList.contains('dark-theme')).toBe(true);

    // User preference should freeze system changes
    TestBed.inject(ThemeService).setPreference('light');
    changeHandler?.({ matches: false } as MediaQueryListEvent);
    expect(document.body.classList.contains('light-theme')).toBe(true);
  });
});
