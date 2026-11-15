import { InjectionToken } from '@angular/core';

export interface AppEnvironment {
  readonly backendBaseUrl: string;
  readonly analysisPollingIntervalMs: number;
  readonly organizerBaseUrl: string;
  readonly oidc: {
    readonly url: string;
    readonly realm: string;
    readonly clientId: string;
  };
}

export const APP_ENVIRONMENT = new InjectionToken<AppEnvironment>('APP_ENVIRONMENT');
