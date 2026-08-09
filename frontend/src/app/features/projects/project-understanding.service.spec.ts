import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ProjectUnderstandingService } from './project-understanding.service';

describe('ProjectUnderstandingService', () => {
  it('launches understanding through one typed Core request', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: 'http://core.test' } },
      ],
    });
    const service = TestBed.inject(ProjectUnderstandingService);
    const http = TestBed.inject(HttpTestingController);
    const requestBody = { sourceId: 'source-1', targetRevision: 'main' };
    let outcome: string | undefined;

    service.execute('project/1', requestBody).subscribe((response) => (outcome = response.outcome));

    const request = http.expectOne(
      'http://core.test/api/v1/projects/project%2F1/understanding-executions',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush({
      analysisId: 'analysis-1',
      status: 'IN_PROGRESS',
      sourceId: 'source-1',
      targetRevision: 'main',
      intentId: 'describe-project',
      intentVersion: 'v1',
      outcome: 'REUSED',
      sourceSnapshot: {},
    });
    expect(outcome).toBe('REUSED');
    http.verify();
  });
});
