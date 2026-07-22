import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { IntentCatalogService } from './intent-catalog.service';

describe('IntentCatalogService', () => {
  it('loads supported Intents from Java Core', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: APP_ENVIRONMENT,
          useValue: { backendBaseUrl: '', analysisPollingIntervalMs: 10 },
        },
      ],
    });
    TestBed.inject(IntentCatalogService).getSupportedIntents().subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/intents');
    expect(request.request.method).toBe('GET');
    request.flush([]);
    TestBed.inject(HttpTestingController).verify();
  });
});
