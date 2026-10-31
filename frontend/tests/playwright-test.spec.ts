import { expect, test } from './e2e-fixtures';

test('Application serves the SPA shell', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Frontend/);
});

test('AI settings supports an API-key enrollment without exposing the key', async ({ page }) => {
  const catalog = {
    catalogVersion: 'test',
    providers: [
      {
        providerId: 'anthropic',
        displayName: 'Claude',
        authenticationMethods: ['API_KEY'],
        models: [
          {
            identifier: 'claude-sonnet',
            displayName: 'Claude Sonnet',
            capabilities: ['CHAT'],
            availability: 'AVAILABLE',
          },
        ],
        recommendedModel: 'claude-sonnet',
        parameters: [
          {
            name: 'maxOutputTokens',
            displayName: 'Maximum output tokens',
            type: 'integer',
            minimum: 256,
            maximum: 32768,
            defaultValue: 2000,
          },
        ],
        capabilities: ['CHAT'],
        lifecycleStatus: 'ACTIVE',
      },
    ],
  };
  const configuration = {
    provider: null,
    authenticationMethod: null,
    selectedModel: null,
    modelSelectionOrigin: null,
    allowedParameters: {},
    state: 'NOT_CONFIGURED',
  };

  await page.route('**/api/v1/ai/catalog', (route) => route.fulfill({ json: catalog }));
  await page.route('**/api/v1/ai/configuration', async (route) => {
    if (route.request().method() === 'PUT') {
      await route.fulfill({
        json: {
          ...configuration,
          ...route.request().postDataJSON(),
          state: 'CONFIGURATION_INCOMPLETE',
        },
      });
      return;
    }
    await route.fulfill({ json: configuration });
  });
  await page.route('**/api/v1/ai/configuration/api-key/anthropic/start', (route) =>
    route.fulfill({
      json: {
        intakeEndpoint: 'https://ai.example/api/v1/auth/api-key/enroll',
        intentId: 'intent',
        provider: 'anthropic',
        authenticationMethod: 'API_KEY',
        issuedAt: 1,
        expiresAt: 2,
        principalBinding: 'binding',
        signature: 'signature',
        expiresAtInstant: '2026-01-01T00:05:00Z',
      },
    }),
  );
  await page.route('https://ai.example/api/v1/auth/api-key/enroll', (route) =>
    route.fulfill({ json: { credentialHandle: 'opaque-handle' } }),
  );
  await page.route('**/api/v1/ai/configuration/api-key/anthropic/complete', (route) =>
    route.fulfill({ status: 204 }),
  );

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/settings/ai');
  await expect(page.getByRole('heading', { name: 'Connect your AI assistant' })).toBeVisible();
  await page.getByRole('radio', { name: /Claude/ }).click();
  await page.getByRole('button', { name: 'Connect' }).click();
  await page.getByLabel('Anthropic API key').fill('sk-ant-test-secret');
  await page.getByRole('button', { name: 'Save API key' }).click();

  await expect(page.getByLabel('Anthropic API key')).toHaveCount(0);
  await expect(page.locator('body')).not.toContainText('sk-ant-test-secret');
  await expect(page).toHaveURL(/settings\/ai$/);
  expect(page.url()).not.toContain('sk-ant-test-secret');
  expect(await page.evaluate(() => Object.values(localStorage))).not.toContain(
    'sk-ant-test-secret',
  );
});
