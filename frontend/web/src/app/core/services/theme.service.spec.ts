import { TestBed } from '@angular/core/testing';
import { ThemeService, ThemePreference } from './theme.service';

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
  dispatchEvent: () => false
});

describe('ThemeService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
    localStorage.clear();
  });

  it('prefers system color scheme when no stored preference', () => {
    spyOn(window, 'matchMedia').and.returnValue(buildMediaQueryStub(true));

    const service = TestBed.inject(ThemeService);

    expect(service).toBeTruthy();
    expect(document.body.classList.contains('dark-theme')).toBeTrue();
    expect(localStorage.getItem('mobilispect-theme')).toBeNull();
  });

  it('persists explicit theme preference and updates body classes', () => {
    spyOn(window, 'matchMedia').and.returnValue(buildMediaQueryStub(false));

    const service = TestBed.inject(ThemeService);

    service.setPreference('dark');
    expect(localStorage.getItem('mobilispect-theme')).toBe('dark' as ThemePreference);
    expect(document.body.classList.contains('dark-theme')).toBeTrue();

    service.setPreference('light');
    expect(localStorage.getItem('mobilispect-theme')).toBe('light' as ThemePreference);
    expect(document.body.classList.contains('dark-theme')).toBeFalse();
    expect(document.body.classList.contains('light-theme')).toBeTrue();
  });

  it('reacts to system preference changes while in system mode', () => {
    let changeHandler: ((event: MediaQueryListEvent) => void) | undefined;
    spyOn(window, 'matchMedia').and.returnValue(
      buildMediaQueryStub(false, handler => {
        changeHandler = handler;
      })
    );

    TestBed.inject(ThemeService);
    expect(document.body.classList.contains('dark-theme')).toBeFalse();

    changeHandler?.({ matches: true } as MediaQueryListEvent);
    expect(document.body.classList.contains('dark-theme')).toBeTrue();

    // User preference should freeze system changes
    TestBed.inject(ThemeService).setPreference('light');
    changeHandler?.({ matches: false } as MediaQueryListEvent);
    expect(document.body.classList.contains('light-theme')).toBeTrue();
  });
});
