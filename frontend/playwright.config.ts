const { defineConfig, devices } = require('@playwright/test');

// In CI we serve the static SPA build locally. Local full-stack e2e still targets
// http://localhost:18083 (the Docker stack: nginx -> frontend + /api -> backend).
const serveStatic = process.env.PLAYWRIGHT_SERVE === 'true';

module.exports = defineConfig({
  testDir: './tests',
  timeout: 30000,
  expect: {
    timeout: 5000,
  },
  reporter: 'html',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:18083',
    trace: 'on-first-retry',
  },
  webServer: serveStatic
    ? {
        command: 'python3 -m http.server 18083 --directory dist/frontend/browser',
        url: 'http://localhost:18083',
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
