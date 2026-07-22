import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideAppHttp } from './core/http/provide-app-http';
import { APP_ENVIRONMENT } from './core/config/app-environment';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideAppHttp(),
    { provide: APP_ENVIRONMENT, useValue: environment },
  ],
};
