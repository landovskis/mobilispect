// Polyfill for libraries expecting Node.js globals in browser environment.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
(globalThis as any).global = globalThis;
