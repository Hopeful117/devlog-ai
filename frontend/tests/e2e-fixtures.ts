import { readFileSync } from 'node:fs';
import { test as base } from '@playwright/test';

function readAccessToken(): string | undefined {
  try {
    const fixture = JSON.parse(readFileSync('test-results/e2e-auth.json', 'utf8')) as {
      accessToken?: string;
    };
    return fixture.accessToken;
  } catch {
    return undefined;
  }
}

export const test = base.extend({
  page: async ({ page }, use) => {
    const accessToken = readAccessToken();
    if (accessToken) {
      await page.route('**/api/**', async (route) => {
        await route.continue({
          headers: { ...route.request().headers(), authorization: `Bearer ${accessToken}` },
        });
      });
    }
    await use(page);
  },
});

export { expect } from '@playwright/test';
