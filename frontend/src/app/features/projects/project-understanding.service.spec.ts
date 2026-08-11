import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ProjectUnderstandingService } from './project-understanding.service';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';

describe('ProjectUnderstandingService', () => {
  let service: ProjectUnderstandingService;
  let httpMock: HttpTestingController;

  const mockEnv = {
    backendBaseUrl: 'http://localhost:18080',
    analysisPollingIntervalMs: 2000,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ProjectUnderstandingService, { provide: APP_ENVIRONMENT, useValue: mockEnv }],
    });
    service = TestBed.inject(ProjectUnderstandingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call the correct endpoint with projectId and request', () => {
    const projectId = 'test-project-id';
    const request = {
      sourceId: 'source-1',
      targetRevision: 'main',
    };

    service.execute(projectId, request).subscribe((response) => {
      expect(response).toEqual({
        analysisId: 'analysis-1',
        status: 'IN_PROGRESS',
        sourceId: 'source-1',
        targetRevision: 'main',
        intentId: 'describe-project',
        intentVersion: 'v1',
        outcome: 'CREATED',
        sourceSnapshot: {},
      });
    });

    const req = httpMock.expectOne(
      `${mockEnv.backendBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/understanding-executions`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({
      analysisId: 'analysis-1',
      status: 'IN_PROGRESS',
      sourceId: 'source-1',
      targetRevision: 'main',
      intentId: 'describe-project',
      intentVersion: 'v1',
      outcome: 'CREATED',
      sourceSnapshot: {},
    });
  });

  it('should encode special characters in projectId', () => {
    const projectId = 'project-with/special%chars';
    const request = { sourceId: 'source-1' };

    service.execute(projectId, request).subscribe();

    const req = httpMock.expectOne(
      `${mockEnv.backendBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/understanding-executions`,
    );
    expect(req.request.url).toContain(encodeURIComponent(projectId));
    req.flush({
      analysisId: 'analysis-1',
      status: 'IN_PROGRESS',
      sourceId: 'source-1',
      targetRevision: null,
      intentId: 'describe-project',
      intentVersion: 'v1',
      outcome: 'CREATED',
      sourceSnapshot: {},
    });
  });
});
