// Polyfill for libraries expecting Node.js globals in browser environment.
(globalThis as any).global = globalThis;
