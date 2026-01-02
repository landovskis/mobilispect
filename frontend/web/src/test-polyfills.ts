/* Ensure browser tests have a global alias expected by some libraries (e.g. sockjs-client). */
if (typeof window !== 'undefined') {
  (window as any).global = window;
}
