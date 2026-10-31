import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import {
  AiConfigurationState,
  AiConfigurationTestStatus,
  AiModelSelectionOrigin,
  AiProviderCatalog,
  AiProviderCatalogEntry,
  AiUserConfiguration,
  ApiKeyEnrollmentResponse,
} from './ai-configuration.models';
import { AiConfigurationService } from './ai-configuration.service';

@Component({
  selector: 'app-simple-ai-configuration-page',
  imports: [FormsModule],
  templateUrl: './simple-ai-configuration-page.html',
  styleUrl: './simple-ai-configuration-page.scss',
})
export class SimpleAiConfigurationPage implements OnInit {
  private readonly service = inject(AiConfigurationService);
  private readonly route = inject(ActivatedRoute);

  readonly catalog = signal<AiProviderCatalog | null>(null);
  readonly configuration = signal<AiUserConfiguration | null>(null);
  readonly selectedProvider = signal('');
  readonly selectedModel = signal('');
  readonly modelSelectionOrigin = signal<AiModelSelectionOrigin>('RECOMMENDED');
  readonly apiKey = signal('');
  readonly apiKeyEnrollment = signal<ApiKeyEnrollmentResponse | null>(null);
  readonly busy = signal(false);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly testStatus = signal<AiConfigurationTestStatus | null>(null);
  readonly advanced = signal(false);
  readonly maxOutputTokens = signal(2000);

  readonly userProviders = computed(() =>
    (this.catalog()?.providers ?? []).filter(
      (provider) => provider.providerId === 'openai_chatgpt' || provider.providerId === 'anthropic',
    ),
  );

  readonly selectedEntry = computed<AiProviderCatalogEntry | undefined>(() =>
    this.userProviders().find((provider) => provider.providerId === this.selectedProvider()),
  );

  readonly selectedModels = computed(() => this.selectedEntry()?.models ?? []);
  readonly outputTokenParameter = computed(() =>
    this.selectedEntry()?.parameters?.find((parameter) => parameter.name === 'maxOutputTokens'),
  );

  ngOnInit(): void {
    const oauthStatus = this.route.snapshot.queryParamMap.get('oauth');
    if (oauthStatus === 'connected')
      this.notice.set('ChatGPT is connected. Run a test to verify it.');
    if (oauthStatus === 'failed')
      this.error.set('ChatGPT authorization was not completed. Try again.');
    void this.load();
  }

  selectProvider(providerId: string): void {
    if (providerId !== this.selectedProvider() && this.apiKeyEnrollment()) {
      this.apiKey.set('');
      this.apiKeyEnrollment.set(null);
    }
    this.selectedProvider.set(providerId);
    const entry = this.userProviders().find((provider) => provider.providerId === providerId);
    const recommended = entry?.recommendedModel ?? entry?.models[0]?.identifier ?? '';
    this.selectedModel.set(recommended);
    this.modelSelectionOrigin.set('RECOMMENDED');
    this.error.set('');
    this.notice.set('');
  }

  selectModel(model: string): void {
    const selected = this.selectedModels().find((candidate) => candidate.identifier === model);
    if (
      selected?.availability === 'UNAVAILABLE' &&
      !window.confirm(
        'This model is unavailable. Select it only to review its state; confirm a supported replacement to continue.',
      )
    )
      return;
    this.selectedModel.set(model);
    this.modelSelectionOrigin.set('EXPLICIT');
  }

  useRecommended(): void {
    const recommended = this.selectedEntry()?.recommendedModel;
    if (!recommended || !window.confirm('Use the recommended model?')) return;
    this.selectedModel.set(recommended);
    this.modelSelectionOrigin.set('RECOMMENDED');
  }

  async save(): Promise<void> {
    if (!this.selectedProvider() || !this.selectedModel()) {
      this.error.set('Choose a provider and model first.');
      return;
    }
    await this.runAction(async () => {
      const saved = await firstValueFrom(
        this.service.saveConfiguration({
          provider: this.selectedProvider(),
          authenticationMethod: this.authenticationMethod(),
          selectedModel: this.selectedModel(),
          modelSelectionOrigin: this.modelSelectionOrigin(),
          allowedParameters: this.advanced() ? { maxOutputTokens: this.maxOutputTokens() } : {},
        }),
      );
      this.configuration.set(saved);
      this.notice.set('Configuration saved.');
    });
  }

  async connect(): Promise<void> {
    await this.save();
    if (this.error()) return;
    if (this.authenticationMethod() === 'OAUTH') {
      await this.runAction(async () => {
        const response = await firstValueFrom(this.service.startOAuth());
        window.location.assign(response.authorizationUrl);
      });
      return;
    }
    await this.runAction(async () => {
      this.apiKeyEnrollment.set(await firstValueFrom(this.service.startApiKeyEnrollment()));
      this.notice.set(
        'Enter the API key once. It is sent directly to the secure intake and is not stored here.',
      );
    });
  }

  async submitApiKey(): Promise<void> {
    const enrollment = this.apiKeyEnrollment();
    const value = this.apiKey();
    if (!enrollment || !value.trim()) {
      this.error.set('Enter an API key to continue.');
      return;
    }
    this.apiKey.set('');
    await this.runAction(async () => {
      const result = await firstValueFrom(this.service.enrollApiKey(enrollment, value));
      await firstValueFrom(
        this.service.completeApiKey(enrollment.intentId, result.credentialHandle),
      );
      this.apiKeyEnrollment.set(null);
      this.notice.set('Anthropic is connected. Run a test to verify it.');
      await this.reloadConfiguration();
    });
  }

  cancelApiKey(): void {
    this.apiKey.set('');
    this.apiKeyEnrollment.set(null);
    this.notice.set('API-key setup cancelled.');
    this.error.set('');
  }

  async test(): Promise<void> {
    await this.save();
    if (this.error()) return;
    await this.runAction(async () => {
      const result = await firstValueFrom(this.service.testConfiguration());
      this.testStatus.set(result.status);
      if (result.status === 'SUCCESS') {
        this.notice.set('Configuration is ready for the selected model.');
        await this.reloadConfiguration();
      } else {
        this.error.set(this.testMessage(result.status));
      }
    });
  }

  async revoke(): Promise<void> {
    if (!window.confirm('Disconnect this provider?')) return;
    await this.runAction(async () => {
      await firstValueFrom(
        this.service.revoke(this.selectedProvider() as 'openai_chatgpt' | 'anthropic'),
      );
      await this.reloadConfiguration();
      this.notice.set('Connection revoked. Reconnect explicitly to use this provider.');
    });
  }

  stateLabel(state: AiConfigurationState | null): string {
    return (
      {
        NOT_CONFIGURED: 'Not configured',
        CONFIGURATION_INCOMPLETE: 'Configuration incomplete',
        READY: 'Ready',
        REAUTH_REQUIRED: 'Reauthorization required',
        PROVIDER_UNAVAILABLE: 'Provider unavailable',
        MODEL_UNAVAILABLE: 'Model unavailable',
        CREDENTIAL_INVALID: 'Credential invalid',
        REVOKED: 'Connection revoked',
      } as Record<AiConfigurationState, string>
    )[state ?? 'NOT_CONFIGURED'];
  }

  authenticationMethod(): 'API_KEY' | 'OAUTH' {
    return this.selectedProvider() === 'openai_chatgpt' ? 'OAUTH' : 'API_KEY';
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [catalog, configuration] = await Promise.all([
        firstValueFrom(this.service.getCatalog()),
        firstValueFrom(this.service.getConfiguration()),
      ]);
      this.catalog.set(catalog);
      this.configuration.set(configuration);
      const provider = configuration.provider ?? this.userProviders()[0]?.providerId ?? '';
      this.selectProvider(provider);
      if (configuration.selectedModel && configuration.provider === provider) {
        this.selectedModel.set(configuration.selectedModel);
        this.modelSelectionOrigin.set(configuration.modelSelectionOrigin ?? 'EXPLICIT');
      }
      const configuredTokens = configuration.allowedParameters?.['maxOutputTokens'];
      if (typeof configuredTokens === 'number') this.maxOutputTokens.set(configuredTokens);
    } catch {
      this.error.set('AI configuration could not be loaded. Try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private async reloadConfiguration(): Promise<void> {
    this.configuration.set(await firstValueFrom(this.service.getConfiguration()));
  }

  private async runAction(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      await action();
    } catch (error: unknown) {
      this.error.set(this.actionError(error));
    } finally {
      this.busy.set(false);
    }
  }

  private actionError(error: unknown): string {
    const body = (error as { error?: { code?: string } })?.error;
    return (
      (
        {
          CHATGPT_AUTHORIZATION_UNAVAILABLE: 'ChatGPT authorization is temporarily unavailable.',
          API_KEY_ENROLLMENT_FAILED: 'The API key could not be accepted. Check it and try again.',
          CREDENTIAL_INVALID: 'The saved credential is invalid. Reconnect this provider.',
          MODEL_UNAVAILABLE: 'The selected model is unavailable. Choose and confirm another model.',
          INVALID_PARAMETER: 'The advanced parameter is outside the supported safe range.',
          PROVIDER_UNAVAILABLE: 'The provider is temporarily unavailable. Try again later.',
        } as Record<string, string>
      )[body?.code ?? ''] ?? 'The request could not be completed. Try again.'
    );
  }

  private testMessage(status: AiConfigurationTestStatus): string {
    return (
      {
        CREDENTIAL_INVALID: 'The credential is invalid. Reconnect this provider.',
        PROVIDER_UNAVAILABLE: 'The provider is unavailable. Try again later.',
        MODEL_UNAVAILABLE: 'The selected model is unavailable. Choose and confirm another model.',
        TIMEOUT: 'The provider did not respond in time. Try again.',
        CONFIGURATION_INVALID: 'Complete the configuration before testing it.',
        SUCCESS: '',
      } as Record<AiConfigurationTestStatus, string>
    )[status];
  }
}
