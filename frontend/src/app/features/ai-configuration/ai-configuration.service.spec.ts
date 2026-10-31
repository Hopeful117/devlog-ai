import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { AiConfigurationService } from './ai-configuration.service';

describe('AiConfigurationService', () => {
  let service: AiConfigurationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: APP_ENVIRONMENT,
          useValue: { backendBaseUrl: 'http://core.test' },
        },
      ],
    });
    service = TestBed.inject(AiConfigurationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses Core for catalog and configuration operations', () => {
    service.getCatalog().subscribe();
    expect(http.expectOne('http://core.test/api/v1/ai/catalog').request.method).toBe('GET');

    service.getConfiguration().subscribe();
    expect(http.expectOne('http://core.test/api/v1/ai/configuration').request.method).toBe('GET');

    service.testConfiguration().subscribe();
    const request = http.expectOne('http://core.test/api/v1/ai/configuration/test');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
  });

  it('sends the API key only to the governed intake endpoint', () => {
    const enrollment = {
      intakeEndpoint: 'http://engine.test/api/v1/auth/api-key/enroll',
      intentId: 'intent-1',
      provider: 'anthropic',
      authenticationMethod: 'API_KEY' as const,
      issuedAt: 1,
      expiresAt: 2,
      principalBinding: 'binding',
      signature: 'signature',
      expiresAtInstant: '2026-01-01T00:00:02Z',
    };

    service.enrollApiKey(enrollment, 'secret-value').subscribe();
    const request = http.expectOne(enrollment.intakeEndpoint);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      apiKey: 'secret-value',
      authenticationMethod: 'API_KEY',
      expiresAt: 2,
      intentId: 'intent-1',
      issuedAt: 1,
      principalBinding: 'binding',
      provider: 'anthropic',
      signature: 'signature',
    });
  });

  it('uses the existing provider-specific revocation endpoints', () => {
    service.revoke('anthropic').subscribe();
    expect(
      http.expectOne('http://core.test/api/v1/ai-credential-associations/anthropic').request.method,
    ).toBe('DELETE');
  });
});
