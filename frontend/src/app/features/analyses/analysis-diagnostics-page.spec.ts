import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { AnalysisDiagnosticsPage } from './analysis-diagnostics-page';
import { AnalysisDiagnostics } from './analysis.models';
import { AnalysisService } from './analysis.service';
import { InsightProposalService } from '../insights/insight-proposal.service';
import { InsightService } from '../insights/insight.service';

const analysis = {
  id: 'analysis-id',
  projectId: 'project-id',
  type: 'ARCHITECTURE_REVIEW',
  intentId: 'architecture-overview',
  intentVersion: 'v1',
  status: 'IN_PROGRESS',
  startedAt: '2026-07-22T10:00:01Z',
  completedAt: null,
  createdAt: '2026-07-22T10:00:00Z',
  updatedAt: '2026-07-22T10:00:01Z',
  userGuidance: null,
} as const;
const diagnostics = (status: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'): AnalysisDiagnostics => ({
  identity: {
    analysisId: 'analysis-id',
    projectId: 'project-id',
    analysisType: 'ARCHITECTURE_REVIEW',
    intentId: 'architecture-overview',
    intentVersion: 'v1',
    status,
  },
  revision: { requestedRevision: 'main', resolvedRevisions: { source: 'abc123' } },
  timeline: {
    createdAt: analysis.createdAt,
    startedAt: analysis.startedAt,
    completedAt: status === 'IN_PROGRESS' ? null : '2026-07-22T10:01:00Z',
    duration: 'PT59S',
  },
  counts: { sourceCount: 1, factCount: 8, observationCount: 3, warningCount: 0, proposalCount: 2 },
  completeness: { collectionComplete: true, truncated: false, warningCount: 0, errorCount: 0 },
  collectors: {
    collectorCount: 4,
    successfulCollectors: 4,
    collectorsWithWarnings: 0,
    failedCollectors: 0,
  },
  aiTask: {
    taskType: 'INSIGHT_GENERATION',
    status: status === 'IN_PROGRESS' ? 'PROCESSING' : status,
    intentId: 'architecture-overview',
    intentVersion: 'v1',
    provider: 'ai-engine',
    startedAt: analysis.startedAt,
    completedAt: null,
  },
  pipeline: [
    { name: 'WORKSPACE', status: 'COMPLETED', resourceCount: 1 },
    { name: 'AI_TASK', status: status === 'IN_PROGRESS' ? 'PROCESSING' : status, resourceCount: 1 },
  ],
  technicalMetadata: {
    contextBuilderVersion: 'analysis-context-v3',
    collectorVersions: {},
    serializedContextSize: 100,
  },
  profile: {
    profileAvailable: true,
    profileId: 'profile-id',
    profileVersion: 'v1',
    profileCompleteness: 'COMPLETE',
    characteristicCount: 5,
  },
  links: {},
});

describe('AnalysisDiagnosticsPage', () => {
  const params = new BehaviorSubject(convertToParamMap({ id: 'analysis-id' }));
  const getAnalysis = vi.fn();
  const getDiagnostics = vi.fn();
  const getAiTasksByAnalysis = vi.fn();
  const getSelectedEvidence = vi.fn();
  beforeEach(async () => {
    vi.useFakeTimers();
    getAnalysis.mockReset().mockReturnValue(of(analysis));
    getDiagnostics.mockReset().mockReturnValue(of(diagnostics('COMPLETED')));
    getSelectedEvidence.mockReset().mockReturnValue(
      of({
        state: 'SNAPSHOT_PENDING',
        analysisId: 'analysis-id',
        projectId: 'project-id',
        task: {
          id: 'task-id',
          taskType: 'INSIGHT_GENERATION',
          status: 'PROCESSING',
          createdAt: analysis.createdAt,
        },
        selectionVersion: null,
        selectionDigest: null,
        snapshotMetadata: null,
        categories: null,
      }),
    );
    getAiTasksByAnalysis.mockReset().mockReturnValue(
      of([
        {
          id: 'task-id',
          analysisId: 'analysis-id',
          correlationId: 'correlation-id',
          taskType: 'INSIGHT_GENERATION',
          intentId: 'architecture-overview',
          intentVersion: 'v1',
          intentSnapshot: null,
          userGuidanceSnapshot: { focus: '<script>alert(1)</script>' },
          promptRequestId: 'request-id',
          promptVersion: 'architecture-overview-prompt-v1',
          provider: 'openai',
          modelIdentifier: 'gpt-4.1-mini',
          promptContentDigest: 'abc',
          contextDigest: 'def',
          selectedKnowledgeSnapshot: null,
          selectionVersion: 'selection-v1',
          selectionDigest: 'ghi',
          status: 'COMPLETED',
          contextSnapshot: null,
          externalJobId: 'job-id',
          attemptCount: 1,
          failureCode: null,
          failureMessage: null,
          createdAt: analysis.createdAt,
          submittedAt: analysis.startedAt,
          startedAt: analysis.startedAt,
          completedAt: '2026-07-22T10:01:00Z',
        },
      ]),
    );
    await TestBed.configureTestingModule({
      imports: [AnalysisDiagnosticsPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
        {
          provide: APP_ENVIRONMENT,
          useValue: { backendBaseUrl: '', analysisPollingIntervalMs: 10 },
        },
        {
          provide: AnalysisService,
          useValue: {
            getAnalysis,
            getDiagnostics,
            getAiTasksByAnalysis,
            getSelectedEvidence,
            getWarnings: () => of([]),
            getProfile: () =>
              of({
                deterministicSummary: 'Deterministic profile',
                completeness: { status: 'COMPLETE' },
                characteristicCount: 5,
              }),
            getContext: () => of({ unsafe: '<img src=x onerror=alert(1)>' }),
          },
        },
        { provide: InsightProposalService, useValue: { getProposalsByAnalysis: () => of([]) } },
        { provide: InsightService, useValue: { getInsightsByAnalysis: () => of([]) } },
      ],
    }).compileComponents();
  });
  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('loads diagnostics immediately and renders only backend pipeline stages', async () => {
    const fixture = TestBed.createComponent(AnalysisDiagnosticsPage);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    expect(getDiagnostics).toHaveBeenCalledWith('analysis-id');
    expect(fixture.nativeElement.textContent).toContain('WORKSPACE');
    expect(fixture.nativeElement.textContent).toContain('AI_TASK');
  });
  it('does not overlap polling and stops on COMPLETED', async () => {
    const first = new Subject<AnalysisDiagnostics>();
    getDiagnostics.mockReturnValueOnce(first).mockReturnValue(of(diagnostics('COMPLETED')));
    const component = TestBed.createComponent(AnalysisDiagnosticsPage).componentInstance;
    const seen: unknown[] = [];
    component.diagnostics$.subscribe((value) => seen.push(value));
    await vi.advanceTimersByTimeAsync(50);
    expect(getDiagnostics).toHaveBeenCalledTimes(1);
    first.next(diagnostics('IN_PROGRESS'));
    first.complete();
    await vi.advanceTimersByTimeAsync(10);
    expect(getDiagnostics).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(100);
    expect(getDiagnostics).toHaveBeenCalledTimes(2);
    expect(seen.length).toBeGreaterThan(1);
  });
  it('stops polling on FAILED', async () => {
    getDiagnostics.mockReturnValue(of(diagnostics('FAILED')));
    const component = TestBed.createComponent(AnalysisDiagnosticsPage).componentInstance;
    component.diagnostics$.subscribe();
    await vi.advanceTimersByTimeAsync(100);
    expect(getDiagnostics).toHaveBeenCalledTimes(1);
  });
  it('renders Analysis not found', () => {
    getAnalysis.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const fixture = TestBed.createComponent(AnalysisDiagnosticsPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Analysis not found');
  });
  it('renders context as escaped text', () => {
    const fixture = TestBed.createComponent(AnalysisDiagnosticsPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('pre')?.textContent).toContain('<img');
    expect(fixture.nativeElement.querySelector('pre img')).toBeNull();
  });
  it('renders provider metadata and guidance safely', async () => {
    const fixture = TestBed.createComponent(AnalysisDiagnosticsPage);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('gpt-4.1-mini');
    expect(fixture.nativeElement.textContent).toContain('<script>alert(1)</script>');
    expect(fixture.nativeElement.querySelector('script')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('LLM_API_KEY');
  });
  it('hosts historical supplied evidence before generated Analysis output', async () => {
    const fixture = TestBed.createComponent(AnalysisDiagnosticsPage);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(getSelectedEvidence).toHaveBeenCalledWith('analysis-id');
    expect(text).toContain('Evidence supplied to the model');
    expect(text.indexOf('Evidence supplied to the model')).toBeLessThan(
      text.indexOf('Insight Proposals'),
    );
    expect(text).toContain('execution-time evidence selected for the displayed AI Task');
  });
  it('does not manually subscribe', () =>
    expect(AnalysisDiagnosticsPage.toString()).not.toContain('.subscribe('));
});