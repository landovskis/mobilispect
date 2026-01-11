import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export type ThemePreference = 'light' | 'dark';
export type ActiveTheme = 'light' | 'dark';

/**
 * Centralized theme manager to satisfy the constitutional light/dark mode requirement.
 * Applies a `dark-theme` class to both <body> and <html> to ensure Material variables
 * and component host-context selectors are toggled together.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly storageKey = 'mobilispect-theme';
  private readonly document = inject(DOCUMENT);
  private readonly mediaQuery = this.getMediaQuery();
  private readonly hasStoredPreference: boolean;

  private readonly preferenceSubject = new BehaviorSubject<ThemePreference>(
    'light',
  );
  private readonly activeThemeSubject = new BehaviorSubject<ActiveTheme>(
    'light',
  );

  readonly preference$: Observable<ThemePreference> =
    this.preferenceSubject.asObservable();
  readonly activeTheme$: Observable<ActiveTheme> =
    this.activeThemeSubject.asObservable();

  constructor() {
    const storedPreference = this.readStoredPreference();
    this.hasStoredPreference = storedPreference !== null;

    const initialPreference = storedPreference ?? this.resolveSystemTheme();
    this.preferenceSubject.next(initialPreference);
    this.applyTheme(initialPreference);
    this.listenForSystemChanges();
  }

  setPreference(preference: ThemePreference): ActiveTheme {
    this.preferenceSubject.next(preference);
    this.persistPreference(preference);
    this.applyTheme(preference);
    return preference;
  }

  toggle(): ActiveTheme {
    const next: ActiveTheme =
      this.activeThemeSubject.value === 'dark' ? 'light' : 'dark';
    return this.setPreference(next);
  }

  private applyTheme(theme: ActiveTheme): void {
    this.activeThemeSubject.next(theme);

    const bodyClassList = this.document.body.classList;
    const rootClassList = this.document.documentElement.classList;
    const isDark = theme === 'dark';

    bodyClassList.toggle('dark-theme', isDark);
    bodyClassList.toggle('light-theme', !isDark);
    rootClassList.toggle('dark-theme', isDark);
    rootClassList.toggle('light-theme', !isDark);

    this.document.documentElement.setAttribute('data-theme', theme);
    this.document.body.setAttribute('data-theme', theme);
  }

  private listenForSystemChanges(): void {
    if (this.hasStoredPreference || !this.mediaQuery) {
      return;
    }

    const handler = (event: MediaQueryListEvent): void => {
      this.applyTheme(event.matches ? 'dark' : 'light');
    };

    if (this.mediaQuery.addEventListener) {
      this.mediaQuery.addEventListener('change', handler);
    } else if (this.mediaQuery.addListener) {
      this.mediaQuery.addListener(handler);
    }
  }

  private readStoredPreference(): ThemePreference | null {
    try {
      const stored = localStorage.getItem(this.storageKey);
      if (stored === 'light' || stored === 'dark') {
        return stored;
      }
    } catch {
      return null;
    }

    return null;
  }

  private persistPreference(preference: ThemePreference): void {
    try {
      localStorage.setItem(this.storageKey, preference);
    } catch {
      // Ignore storage failures (e.g., disabled cookies)
    }
  }

  private getMediaQuery(): MediaQueryList | null {
    if (
      typeof window === 'undefined' ||
      typeof window.matchMedia !== 'function'
    ) {
      return null;
    }

    return window.matchMedia('(prefers-color-scheme: dark)');
  }

  private resolveSystemTheme(): ActiveTheme {
    return this.mediaQuery?.matches ? 'dark' : 'light';
  }
}
