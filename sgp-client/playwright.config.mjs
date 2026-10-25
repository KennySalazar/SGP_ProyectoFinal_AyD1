import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e/hu061',
  testMatch: '**/*.spec.mjs',
  // La suite HU-061 conserva su ejecución habitual sin backend real.
  testIgnore: [
    ...(process.env.HU062_FASE1 === 'true' ? [] : ['**/hu062-fase1.spec.mjs']),
    ...(process.env.HU062_FASE2 === 'true' ? [] : ['**/hu062-fase2.spec.mjs']),
  ],
  timeout: 90000,
  workers: 1,
  use: { baseURL: 'http://127.0.0.1:4173', headless: true, trace: 'retain-on-failure' },
  webServer: {
    command: 'node e2e/hu061/server.mjs',
    url: 'http://127.0.0.1:4173/__e2e/setup',
    reuseExistingServer: false,
  },
});
