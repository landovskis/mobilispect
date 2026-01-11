// Polyfill for libraries expecting Node.js globals in browser environment
const globalRef = globalThis as typeof globalThis & { global?: typeof globalThis };
if (!globalRef.global) {
  globalRef.global = globalRef;
}

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
