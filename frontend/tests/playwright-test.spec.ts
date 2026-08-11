const { test, expect } = require('@playwright/test');

test('Application serves the SPA shell', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Frontend/);
});
