const { defineConfig, devices } = require('@playwright/test');

// In CI we serve the static SPA build locally. Local full-stack e2e still targets
// http://localhost:18083 (the Docker stack: nginx -> frontend + /api -> backend).
const serveStatic = process.env.PLAYWRIGHT_SERVE === 'true';
const servePort = process.env.PLAYWRIGHT_SERVE_PORT || '18084';

module.exports = defineConfig({
  testDir: './tests',
  timeout: 30000,
  expect: {
    timeout: 5000,
  },
  reporter: 'html',
  use: {
    baseURL:
      process.env.PLAYWRIGHT_BASE_URL ||
      (serveStatic ? `http://localhost:${servePort}` : 'http://localhost:18083'),
    trace: 'on-first-retry',
  },
  webServer: serveStatic
    ? {
        command: `npm run start -- --host 127.0.0.1 --port ${servePort}`,
        url: `http://localhost:${servePort}`,
        reuseExistingServer: false,
      }
    : undefined,
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
