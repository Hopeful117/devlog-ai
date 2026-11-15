import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { APP_ENVIRONMENT } from './core/config/app-environment';
import { environment } from '../environments/environment';
import { provideKeycloak } from 'keycloak-angular';
import {
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  includeBearerTokenInterceptor,
} from 'keycloak-angular';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

const e2eMode = (globalThis as { __DEVLOG_E2E__?: boolean }).__DEVLOG_E2E__ === true;

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors(e2eMode ? [] : [includeBearerTokenInterceptor])),
    ...(e2eMode
      ? []
      : [
          {
            provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
            useValue: [{ urlPattern: /\/api\// }],
          },
          provideKeycloak({
            config: environment.oidc,
            initOptions: {
              onLoad: 'login-required',
              pkceMethod: 'S256',
              checkLoginIframe: false,
            },
          }),
        ]),
    { provide: APP_ENVIRONMENT, useValue: environment },
  ],
};
