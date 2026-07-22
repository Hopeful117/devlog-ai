import { InjectionToken } from '@angular/core';

export interface AppEnvironment {
  readonly backendBaseUrl: string;
  readonly analysisPollingIntervalMs: number;
}

export const APP_ENVIRONMENT = new InjectionToken<AppEnvironment>('APP_ENVIRONMENT');
