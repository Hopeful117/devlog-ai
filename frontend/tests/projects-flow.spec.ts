const { test, expect } = require('@playwright/test');

test('lists projects and opens a project overview', async ({ page }) => {
  await page.goto('/projects');

  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible();

  const project = page.getByRole('link', { name: 'devlog-ai' });
  await expect(project).toBeVisible();
  await project.click();

  await expect(page).toHaveURL(/\/projects\/devlog-ai$/);

  await page.getByRole('link', { name: 'Overview' }).click();
  await expect(page).toHaveURL(/\/projects\/devlog-ai\/overview$/);

  await expect(page.getByText(/#\d+ —/).first()).toBeVisible();
  await expect(page.getByText(/null #/)).toHaveCount(0);
});
