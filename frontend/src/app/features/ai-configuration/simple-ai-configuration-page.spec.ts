import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AiConfigurationService } from './ai-configuration.service';
import { SimpleAiConfigurationPage } from './simple-ai-configuration-page';

const catalog = {
  catalogVersion: '1',
  providers: [
    {
      providerId: 'openai_chatgpt',
      displayName: 'ChatGPT',
      authenticationMethods: ['OAUTH'],
      models: [
        {
          identifier: 'gpt-5.6-luna',
          displayName: 'Recommended model',
          capabilities: [],
          availability: 'AVAILABLE',
        },
      ],
      recommendedModel: 'gpt-5.6-luna',
      capabilities: [],
      lifecycleStatus: 'STABLE',
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
    },
    {
      providerId: 'anthropic',
      displayName: 'Claude',
      authenticationMethods: ['API_KEY'],
      models: [
        {
          identifier: 'claude-sonnet',
          displayName: 'Claude Sonnet',
          capabilities: [],
          availability: 'AVAILABLE',
        },
      ],
      recommendedModel: 'claude-sonnet',
      capabilities: [],
      lifecycleStatus: 'STABLE',
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
    },
  ],
} as const;

const configuration = {
  provider: null,
  authenticationMethod: null,
  selectedModel: null,
  modelSelectionOrigin: null,
  allowedParameters: {},
  state: 'NOT_CONFIGURED' as const,
};

describe('SimpleAiConfigurationPage', () => {
  function render() {
    const service = {
      getCatalog: vi.fn(() => of(catalog)),
      getConfiguration: vi.fn(() => of(configuration)),
      saveConfiguration: vi.fn((request) =>
        of({ ...configuration, ...request, state: 'REAUTH_REQUIRED' as const }),
      ),
      startOAuth: vi.fn(() =>
        of({ authorizationUrl: 'https://auth.test', intentId: 'intent', expiresAt: '' }),
      ),
      startApiKeyEnrollment: vi.fn(() =>
        of({
          intakeEndpoint: 'https://engine.test/enroll',
          intentId: 'intent',
          provider: 'anthropic',
          authenticationMethod: 'API_KEY' as const,
          issuedAt: 1,
          expiresAt: 2,
          principalBinding: 'binding',
          signature: 'signature',
          expiresAtInstant: '',
        }),
      ),
      enrollApiKey: vi.fn(() => of({ credentialHandle: 'opaque-handle' })),
      completeApiKey: vi.fn(() => of(void 0)),
      testConfiguration: vi.fn(() =>
        of({ status: 'SUCCESS' as const, provider: 'anthropic', model: 'claude-sonnet' }),
      ),
      revoke: vi.fn(() => of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [SimpleAiConfigurationPage],
      providers: [provideRouter([]), { provide: AiConfigurationService, useValue: service }],
    });
    const fixture = TestBed.createComponent(SimpleAiConfigurationPage);
    fixture.detectChanges();
    return { fixture, service };
  }

  it('renders product provider names and a distinct initial state', async () => {
    const { fixture } = render();
    await fixture.whenStable();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('ChatGPT');
    expect(fixture.nativeElement.textContent).toContain('Claude');
    expect(fixture.nativeElement.textContent).toContain('Not configured');
    expect(fixture.nativeElement.textContent).not.toContain('openai_chatgpt');
  });

  it('clears the API key before enrollment completes', async () => {
    const { fixture, service } = render();
    await fixture.whenStable();
    await fixture.whenStable();
    fixture.detectChanges();
    fixture.componentInstance.selectProvider('anthropic');
    await fixture.componentInstance.connect();
    fixture.componentInstance.apiKey.set('secret-value');
    await fixture.componentInstance.submitApiKey();
    expect(fixture.componentInstance.apiKey()).toBe('');
    expect(service.startApiKeyEnrollment).toHaveBeenCalled();
  });

  it('saves a changed model before testing it', async () => {
    const { fixture, service } = render();
    await fixture.whenStable();
    await fixture.whenStable();
    fixture.detectChanges();
    fixture.componentInstance.selectProvider('anthropic');

    await fixture.componentInstance.test();

    expect(service.saveConfiguration).toHaveBeenCalled();
    expect(service.testConfiguration).toHaveBeenCalled();
    expect(service.saveConfiguration.mock.invocationCallOrder[0]).toBeLessThan(
      service.testConfiguration.mock.invocationCallOrder[0],
    );
  });

  it('clears an in-progress API-key enrollment when switching provider', () => {
    const { fixture } = render();
    fixture.componentInstance.apiKeyEnrollment.set({
      intentId: 'intent',
      provider: 'anthropic',
      authenticationMethod: 'API_KEY',
      issuedAt: 1,
      expiresAt: 2,
      expiresAtInstant: '2026-01-01T00:05:00Z',
      principalBinding: 'binding',
      signature: 'signature',
      intakeEndpoint: 'https://ai.example/enroll',
    });
    fixture.componentInstance.apiKey.set('secret');

    fixture.componentInstance.selectProvider('openai_chatgpt');

    expect(fixture.componentInstance.apiKeyEnrollment()).toBeNull();
    expect(fixture.componentInstance.apiKey()).toBe('');
  });

  it('sends the bounded Advanced parameter through the shared save contract', async () => {
    const { fixture, service } = render();
    await fixture.whenStable();
    await fixture.whenStable();
    fixture.componentInstance.selectProvider('anthropic');
    fixture.componentInstance.advanced.set(true);
    fixture.componentInstance.maxOutputTokens.set(4096);

    await fixture.componentInstance.save();

    expect(service.saveConfiguration).toHaveBeenCalledWith(
      expect.objectContaining({
        allowedParameters: { maxOutputTokens: 4096 },
      }),
    );
  });

  it('revokes explicitly and refreshes configuration state', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, service } = render();
    fixture.componentInstance.selectProvider('anthropic');

    await fixture.componentInstance.revoke();

    expect(service.revoke).toHaveBeenCalledWith('anthropic');
    expect(service.getConfiguration).toHaveBeenCalledTimes(2);
  });
});
