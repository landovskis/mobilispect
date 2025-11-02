import { Injectable, signal, effect } from '@angular/core';

export type Theme = 'light' | 'dark' | 'auto';

/**
 * ThemeService - Constitutional Requirement (Principle III)
 *
 * Manages light/dark mode theming across the application.
 * Supports manual selection and automatic detection via prefers-color-scheme.
 * Theme preference is persisted in localStorage.
 */
@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly STORAGE_KEY = 'mobilispect-theme';
  private readonly DARK_CLASS = 'dark-theme';
  private readonly LIGHT_CLASS = 'light-theme';
  private readonly isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined';
  private readonly hasLocalStorage = typeof localStorage !== 'undefined';
  private readonly supportsMatchMedia =
    this.isBrowser && typeof window.matchMedia === 'function';

  // Signal for reactive theme state
  private themeSignal = signal<Theme>(this.getInitialTheme());

  // Public readonly signal
  public readonly theme = this.themeSignal.asReadonly();

  // Media query for system preference detection (assigned in constructor)
  private mediaQuery: MediaQueryList | null = null;

  // Computed effective theme (resolved in constructor and on updates)
  public readonly effectiveTheme = signal<'light' | 'dark'>('light');

  constructor() {
    if (this.supportsMatchMedia) {
      this.mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    }

    // Initialize effective theme
    this.updateEffectiveTheme();

    // Apply initial theme
    this.applyTheme(this.theme());

    // Listen for system theme changes
    this.mediaQuery?.addEventListener('change', () => {
      if (this.theme() !== 'auto') {
        return;
      }
      this.updateEffectiveTheme();
      this.applyTheme('auto');
    });

    // Effect to persist theme changes
    effect(() => {
      const currentTheme = this.theme();
      if (this.hasLocalStorage) {
        localStorage.setItem(this.STORAGE_KEY, currentTheme);
      }
      this.updateEffectiveTheme();
    });
  }

  /**
   * Set the theme preference
   */
  setTheme(theme: Theme): void {
    this.themeSignal.set(theme);
    this.applyTheme(theme);
  }

  /**
   * Toggle between light and dark (if currently manual)
   * If on auto, switches to opposite of current system preference
   */
  toggleTheme(): void {
    const current = this.effectiveTheme();
    const newTheme: Theme = current === 'dark' ? 'light' : 'dark';
    this.setTheme(newTheme);
  }

  /**
   * Check if dark mode is currently active
   */
  isDarkMode(): boolean {
    return this.effectiveTheme() === 'dark';
  }

  /**
   * Get initial theme from localStorage or system preference
   */
  private getInitialTheme(): Theme {
    if (!this.hasLocalStorage) {
      return 'auto';
    }

    const stored = localStorage.getItem(this.STORAGE_KEY) as Theme | null;

    // Validate stored theme
    if (stored && ['light', 'dark', 'auto'].includes(stored)) {
      return stored;
    }

    // Default to auto (respect system preference)
    return 'auto';
  }

  /**
   * Resolve 'auto' theme to actual light/dark based on system preference
   */
  private resolveEffectiveTheme(theme: Theme): 'light' | 'dark' {
    if (theme === 'auto') {
      const matches =
        typeof this.mediaQuery?.matches === 'boolean' ? this.mediaQuery.matches : false;
      return matches ? 'dark' : 'light';
    }
    return theme;
  }

  /**
   * Update the effective theme signal
   */
  private updateEffectiveTheme(): void {
    this.effectiveTheme.set(this.resolveEffectiveTheme(this.theme()));
  }

  /**
   * Apply theme to document html and body elements
   */
  private applyTheme(theme: Theme): void {
    const effective = this.resolveEffectiveTheme(theme);
    if (!this.isBrowser) {
      this.effectiveTheme.set(effective);
      return;
    }
    const html = document.documentElement;
    const body = document.body;

    // Remove both theme classes first from both elements
    html.classList.remove(this.DARK_CLASS, this.LIGHT_CLASS);
    body.classList.remove(this.DARK_CLASS, this.LIGHT_CLASS);

    // Add the appropriate theme class to both elements
    if (effective === 'dark') {
      html.classList.add(this.DARK_CLASS);
      body.classList.add(this.DARK_CLASS);
    } else {
      html.classList.add(this.LIGHT_CLASS);
      body.classList.add(this.LIGHT_CLASS);
    }

    // Update effective theme signal
    this.effectiveTheme.set(effective);
  }
}
