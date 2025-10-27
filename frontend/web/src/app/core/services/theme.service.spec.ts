import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let originalMatchMedia: typeof window.matchMedia;
  let originalLocalStorage: Storage;
  let mockMediaQueryList: MediaQueryList;
  let listeners: Array<(event: MediaQueryListEvent) => void>;
  let localStorageMock: Storage;

  beforeEach(() => {
    originalMatchMedia = window.matchMedia;
    originalLocalStorage = window.localStorage;
    listeners = [];
    document.body.className = '';

    mockMediaQueryList = {
      matches: false,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: jasmine.createSpy('addListener').and.callFake((listener: EventListenerOrEventListenerObject) => {
        if (typeof listener === 'function') {
          listeners.push(listener as (event: MediaQueryListEvent) => void);
        }
      }),
      removeListener: jasmine.createSpy('removeListener'),
      addEventListener: jasmine.createSpy('addEventListener').and.callFake(
        (_type: string, listener: EventListenerOrEventListenerObject) => {
          if (typeof listener === 'function') {
            listeners.push(listener as (event: MediaQueryListEvent) => void);
          }
        }
      ),
      removeEventListener: jasmine.createSpy('removeEventListener'),
      dispatchEvent: jasmine
        .createSpy('dispatchEvent')
        .and.callFake((event: Event) => {
          listeners.forEach((listener) => listener(event as MediaQueryListEvent));
          return true;
        })
    } as MediaQueryList;

    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: jasmine.createSpy('matchMedia').and.returnValue(mockMediaQueryList)
    });

    let storage: Record<string, string> = {};
    localStorageMock = {
      getItem: jasmine.createSpy('getItem').and.callFake((key: string) => storage[key] ?? null),
      setItem: jasmine.createSpy('setItem').and.callFake((key: string, value: string) => {
        storage[key] = value;
      }),
      removeItem: jasmine.createSpy('removeItem').and.callFake((key: string) => {
        delete storage[key];
      }),
      clear: jasmine.createSpy('clear').and.callFake(() => {
        storage = {};
      }),
      key: jasmine.createSpy('key').and.callFake((index: number) => Object.keys(storage)[index] ?? null),
      get length() {
        return Object.keys(storage).length;
      }
    } as unknown as Storage;

    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      writable: true,
      value: localStorageMock
    });
  });

  afterEach(() => {
    document.body.className = '';
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: originalMatchMedia
    });
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      writable: true,
      value: originalLocalStorage
    });
  });

  it('applies light theme by default when system preference is light', () => {
    mockMediaQueryList.matches = false;

    const service = new ThemeService();

    expect(service.theme()).toBe('auto');
    expect(service.effectiveTheme()).toBe('light');
    expect(document.body.classList.contains('light-theme')).toBeTrue();
  });

  it('updates to dark theme when system preference switches while on auto', () => {
    mockMediaQueryList.matches = false;
    const service = new ThemeService();

    mockMediaQueryList.matches = true;
    listeners.forEach((listener) =>
      listener({ matches: true } as MediaQueryListEvent)
    );

    expect(service.effectiveTheme()).toBe('dark');
    expect(document.body.classList.contains('dark-theme')).toBeTrue();
  });

  it('persists manual theme selection to localStorage', () => {
    const service = new ThemeService();

    service.setTheme('dark');

    const setItemSpy = localStorageMock.setItem as jasmine.Spy;
    const lastCall = setItemSpy.calls.mostRecent();
    expect(lastCall.args).toEqual(['mobilispect-theme', 'dark']);
    expect(service.effectiveTheme()).toBe('dark');
    expect(document.body.classList.contains('dark-theme')).toBeTrue();
  });

  it('does not throw when matchMedia is unavailable', () => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: undefined
    });

    let service: ThemeService | undefined;
    expect(() => {
      service = new ThemeService();
    }).not.toThrow();

    expect(service?.effectiveTheme()).toBe('light');
    expect(document.body.classList.contains('light-theme')).toBeTrue();
  });
});
