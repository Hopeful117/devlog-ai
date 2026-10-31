import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  AiConfigurationTestResult,
  AiProviderCatalog,
  AiUserConfiguration,
  ApiKeyEnrollmentResponse,
  ApiKeyEnrollmentResult,
  OAuthStartResponse,
  SaveAiConfigurationRequest,
} from './ai-configuration.models';

@Injectable({ providedIn: 'root' })
export class AiConfigurationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1/ai`;
  private readonly configurationUrl = `${this.baseUrl}/configuration`;

  getCatalog(): Observable<AiProviderCatalog> {
    return this.http.get<AiProviderCatalog>(`${this.baseUrl}/catalog`);
  }

  getConfiguration(): Observable<AiUserConfiguration> {
    return this.http.get<AiUserConfiguration>(this.configurationUrl);
  }

  saveConfiguration(request: SaveAiConfigurationRequest): Observable<AiUserConfiguration> {
    return this.http.put<AiUserConfiguration>(this.configurationUrl, request);
  }

  startOAuth(): Observable<OAuthStartResponse> {
    return this.http.post<OAuthStartResponse>(`${this.configurationUrl}/oauth/chatgpt/start`, {});
  }

  startApiKeyEnrollment(): Observable<ApiKeyEnrollmentResponse> {
    return this.http.post<ApiKeyEnrollmentResponse>(
      `${this.configurationUrl}/api-key/anthropic/start`,
      {},
    );
  }

  enrollApiKey(
    enrollment: ApiKeyEnrollmentResponse,
    apiKey: string,
  ): Observable<ApiKeyEnrollmentResult> {
    return this.http.post<ApiKeyEnrollmentResult>(enrollment.intakeEndpoint, {
      apiKey,
      authenticationMethod: enrollment.authenticationMethod,
      expiresAt: enrollment.expiresAt,
      intentId: enrollment.intentId,
      issuedAt: enrollment.issuedAt,
      principalBinding: enrollment.principalBinding,
      provider: enrollment.provider,
      signature: enrollment.signature,
    });
  }

  completeApiKey(intentId: string, credentialHandle: string): Observable<void> {
    return this.http.post<void>(`${this.configurationUrl}/api-key/anthropic/complete`, {
      credentialHandle,
      intentId,
    });
  }

  testConfiguration(): Observable<AiConfigurationTestResult> {
    return this.http.post<AiConfigurationTestResult>(`${this.configurationUrl}/test`, {});
  }

  revoke(provider: 'openai_chatgpt' | 'anthropic'): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}-credential-associations/${provider === 'openai_chatgpt' ? 'chatgpt' : 'anthropic'}`,
    );
  }
}
