import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { CreateAnalysisRequest } from './analysis.models';
import { AnalysisService } from './analysis.service';

describe('AnalysisService', () => {
  let service: AnalysisService;
  let http: HttpTestingController;
  beforeEach(() => {
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
    service = TestBed.inject(AnalysisService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('uses the Java Core list, detail, diagnostics and resource endpoints', () => {
    service.getAnalysesByProject('project-id').subscribe();
    service.getAnalysis('analysis-id').subscribe();
    service.getDiagnostics('analysis-id').subscribe();
    service.getWarnings('analysis-id').subscribe();
    service.getContext('analysis-id').subscribe();
    service.getProfile('analysis-id').subscribe();
    service.getAiTasksByAnalysis('analysis-id').subscribe();
    for (const url of [
      '/api/v1/analyses/project/project-id',
      '/api/v1/analyses/analysis-id',
      '/api/v1/analyses/analysis-id/diagnostics',
      '/api/v1/analyses/analysis-id/warnings',
      '/api/v1/analyses/analysis-id/context',
      '/api/v1/analyses/analysis-id/profile',
      '/api/v1/ai-tasks/analysis/analysis-id',
    ]) {
      const request = http.expectOne(url);
      expect(request.request.method).toBe('GET');
      request.flush(
        url.includes('ai-tasks') || url.includes('warnings') || url.includes('/project/') ? [] : {},
      );
    }
  });

  it('creates and distinctly launches an Analysis', () => {
    const body: CreateAnalysisRequest = {
      projectId: 'project-id',
      type: 'ARCHITECTURE_REVIEW',
      intentId: 'architecture-overview-v1',
    };
    service.createAnalysis(body).subscribe();
    const create = http.expectOne('/api/v1/analyses');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(body);
    create.flush({ id: 'analysis-id' });
    service.launchAnalysis('analysis-id').subscribe();
    const launch = http.expectOne('/api/v1/analyses/analysis-id/workflow');
    expect(launch.request.method).toBe('POST');
    expect(launch.request.body).toBeNull();
    launch.flush({ analysisId: 'analysis-id' });
  });
});
