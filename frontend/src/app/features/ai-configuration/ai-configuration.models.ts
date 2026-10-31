export type AiConfigurationState =
  | 'NOT_CONFIGURED'
  | 'CONFIGURATION_INCOMPLETE'
  | 'READY'
  | 'REAUTH_REQUIRED'
  | 'PROVIDER_UNAVAILABLE'
  | 'MODEL_UNAVAILABLE'
  | 'CREDENTIAL_INVALID'
  | 'REVOKED';

export type AiAuthenticationMethod = 'NONE' | 'API_KEY' | 'OAUTH';
export type AiModelSelectionOrigin = 'RECOMMENDED' | 'EXPLICIT';
export type AiModelAvailability = 'AVAILABLE' | 'UNAVAILABLE' | 'EXPERIMENTAL';

export interface AiProviderModel {
  readonly identifier: string;
  readonly displayName: string;
  readonly capabilities: readonly string[];
  readonly availability: AiModelAvailability;
}

export interface AiProviderCatalogEntry {
  readonly providerId: string;
  readonly displayName: string;
  readonly authenticationMethods: readonly AiAuthenticationMethod[];
  readonly models: readonly AiProviderModel[];
  readonly recommendedModel: string | null;
  readonly capabilities: readonly string[];
  readonly lifecycleStatus: string;
}

export interface AiProviderCatalog {
  readonly catalogVersion: string;
  readonly providers: readonly AiProviderCatalogEntry[];
}

export interface AiUserConfiguration {
  readonly provider: string | null;
  readonly authenticationMethod: AiAuthenticationMethod | null;
  readonly selectedModel: string | null;
  readonly modelSelectionOrigin: AiModelSelectionOrigin | null;
  readonly allowedParameters: Readonly<Record<string, unknown>>;
  readonly state: AiConfigurationState;
}

export interface SaveAiConfigurationRequest {
  readonly provider: string;
  readonly authenticationMethod: AiAuthenticationMethod;
  readonly selectedModel: string;
  readonly modelSelectionOrigin: AiModelSelectionOrigin;
  readonly allowedParameters: Readonly<Record<string, unknown>>;
}

export interface OAuthStartResponse {
  readonly authorizationUrl: string;
  readonly intentId: string;
  readonly expiresAt: string;
}

export interface ApiKeyEnrollmentResponse {
  readonly intakeEndpoint: string;
  readonly intentId: string;
  readonly provider: string;
  readonly authenticationMethod: AiAuthenticationMethod;
  readonly issuedAt: number;
  readonly expiresAt: number;
  readonly principalBinding: string;
  readonly signature: string;
  readonly expiresAtInstant: string;
}

export interface ApiKeyEnrollmentResult {
  readonly credentialHandle: string;
}

export type AiConfigurationTestStatus =
  | 'SUCCESS'
  | 'CREDENTIAL_INVALID'
  | 'PROVIDER_UNAVAILABLE'
  | 'MODEL_UNAVAILABLE'
  | 'TIMEOUT'
  | 'CONFIGURATION_INVALID';

export interface AiConfigurationTestResult {
  readonly status: AiConfigurationTestStatus;
  readonly provider: string | null;
  readonly model: string | null;
}
