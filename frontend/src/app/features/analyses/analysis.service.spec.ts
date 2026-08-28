import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { CreateAnalysisRequest } from './analysis.models';
import { AiTaskSelectedEvidenceResponse } from './ai-task-selected-evidence.models';
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

  it('loads typed selected evidence from the Analysis-scoped endpoint', () => {
    const response: AiTaskSelectedEvidenceResponse = {
      state: 'NO_AI_TASK',
      analysisId: 'analysis/id',
      projectId: 'project-id',
      task: null,
      selectionVersion: null,
      selectionDigest: null,
      snapshotMetadata: null,
      categories: null,
    };
    let actual: AiTaskSelectedEvidenceResponse | undefined;

    service.getSelectedEvidence('analysis/id').subscribe((value) => (actual = value));

    const request = http.expectOne('/api/v1/analyses/analysis%2Fid/selected-evidence');
    expect(request.request.method).toBe('GET');
    request.flush(response);
    expect(actual).toEqual(response);
  });

  it('propagates selected-evidence read failures', () => {
    let status: number | undefined;
    service.getSelectedEvidence('analysis-id').subscribe({
      error: (error: { readonly status: number }) => (status = error.status),
    });

    const request = http.expectOne('/api/v1/analyses/analysis-id/selected-evidence');
    request.flush(
      { code: 'INTERNAL_ERROR', message: 'An unexpected error occurred.' },
      { status: 500, statusText: 'Internal Server Error' },
    );
    expect(status).toBe(500);
  });
});
