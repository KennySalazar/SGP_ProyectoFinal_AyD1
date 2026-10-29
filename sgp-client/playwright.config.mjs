import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e/hu061',
  testMatch: '**/*.spec.mjs',
  // La suite HU-061 conserva su ejecución habitual sin backend real.
  testIgnore: [
    ...(process.env.HU062_FASE1 === 'true' ? [] : ['**/hu062-fase1.spec.mjs']),
    ...(process.env.HU062_FASE2 === 'true' ? [] : ['**/hu062-fase2.spec.mjs']),
    ...(process.env.HU062_FASE3 === 'true' ? [] : ['**/hu062-fase3.spec.mjs']),
    ...(process.env.HU062_PAQUETES === 'true' ? [] : ['**/hu062-paquetes.spec.mjs']),
  ],
  timeout: 90000,
  workers: 1,
  // Evidencia DT-CAL-04: reporte HTML navegable con una captura por prueba.
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    headless: true,
    screenshot: 'on',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'node e2e/hu061/server.mjs',
    url: 'http://127.0.0.1:4173/__e2e/setup',
    reuseExistingServer: false,
  },
});
