import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { APP_ENVIRONMENT } from '../../../core/config/app-environment';
import { DeliverableService } from '../../deliverables/deliverable.service';
import { AnalysisResultPage } from './analysis-result-page';
import { AnalysisResult } from '../analysis.models';
import { AnalysisService } from '../analysis.service';

const completedResult: AnalysisResult = {
  analysis: {
    id: 'analysis-id',
    projectId: 'project-id',
    objective: 'Understand the repository architecture',
    scope: 'PROJECT_SCOPE',
    intentId: 'architecture-overview',
    intentVersion: 'v1',
    status: 'COMPLETED',
    startedAt: '2026-07-22T10:00:00Z',
    completedAt: '2026-07-22T10:01:00Z',
    durationSeconds: 60,
    sourcesAnalyzed: ['core', 'api'],
    targetRevision: 'main',
    repositoryName: null,
  },
  execution: {
    success: true,
    failureCode: null,
    failureMessage: null,
  },
  proposals: {
    total: 2,
    byStatus: { PROPOSED: 1, ACCEPTED: 1 },
    byType: { INSIGHT: 2 },
    items: [
      {
        id: 'proposal-1',
        type: 'INSIGHT',
        status: 'PROPOSED',
        confidence: 0.92,
        title: 'Break up a large service',
        summary: 'The service boundary has grown too broad.',
        evidencePreview: ['Fact#12345678'],
        proposalId: 'proposal-1',
        trustedArtifact: null,
      },
      {
        id: 'proposal-2',
        type: 'INSIGHT',
        status: 'ACCEPTED',
        confidence: 0.85,
        title: 'Retire deprecated endpoint',
        summary: 'The endpoint remains active after migration.',
        evidencePreview: ['Observation#12345678'],
        proposalId: 'proposal-2',
        trustedArtifact: {
          id: 'insight-1',
          type: 'INSIGHT',
          availability: 'AVAILABLE',
          detailAvailable: true,
        },
      },
    ],
  },
  insights: {
    total: 1,
    items: [
      {
        id: 'insight-1',
        type: 'ARCHITECTURE',
        severity: 'WARNING',
        title: 'Layering drift',
        content: 'Infrastructure dependencies reach into domain logic.',
        rationale: 'The service adapters now own orchestration decisions.',
        confidence: 0.88,
        evidenceReferences: ['src/app/service.ts:42'],
        insightId: 'insight-1',
      },
    ],
  },
  deliverables: {
    total: 1,
    items: [
      {
        id: 'deliverable-1',
        type: 'ARCHITECTURE_SUMMARY',
        title: 'Architecture summary',
        audience: 'Engineering leadership',
        status: 'GENERATED',
        generatedAt: '2026-07-22T10:02:00Z',
        sourceInsights: ['insight-1'],
        deliverableId: 'deliverable-1',
      },
    ],
  },
  evidence: {
    facts: {
      count: 3,
      items: [
        {
          layer: 'FACT',
          kind: 'CODE',
          reference: 'pom.xml:1',
          summary: 'Build declaration',
          occurredAt: '2026-07-22T10:00:00Z',
          relatedReferences: [],
        },
      ],
    },
    observations: { count: 1, items: [] },
    priorInsights: { count: 0, items: [] },
    architectureKnowledge: { count: 0, items: [] },
    engineeringEvents: { count: 0, items: [] },
    humanContext: { count: 0, items: [] },
    evolutionContext: { count: 0, items: [] },
    repositoryEvidence: { count: 0, items: [] },
  },
  nextActions: [
    { action: 'REVIEW_PROPOSALS', label: 'Review 1 pending proposal', available: true },
    { action: 'GENERATE_DELIVERABLE', label: 'Generate deliverable', available: true },
    { action: 'VIEW_DIAGNOSTICS', label: 'View diagnostics', available: true },
  ],
};

describe('AnalysisResultPage', () => {
  const params = new BehaviorSubject(convertToParamMap({ id: 'analysis-id' }));
  const getResult = vi.fn();

  beforeEach(async () => {
    vi.useFakeTimers();
    params.next(convertToParamMap({ id: 'analysis-id' }));
    getResult.mockReset().mockReturnValue(of(completedResult));

    await TestBed.configureTestingModule({
      imports: [AnalysisResultPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
        {
          provide: APP_ENVIRONMENT,
          useValue: { backendBaseUrl: '', analysisPollingIntervalMs: 10 },
        },
        { provide: AnalysisService, useValue: { getResult } },
        { provide: DeliverableService, useValue: { generate: vi.fn() } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  async function render() {
    const fixture = TestBed.createComponent(AnalysisResultPage);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    return fixture;
  }

  it('renders the completed canonical result sections', async () => {
    const fixture = await render();
    const text = fixture.nativeElement.textContent as string;

    expect(getResult).toHaveBeenCalledWith('analysis-id');
    expect(text).toContain('Understand the repository architecture');
    expect(text).toContain('Proposals');
    expect(text).toContain('Validated insights');
    expect(text).toContain('Generated deliverables');
    expect(text).toContain('Supporting evidence');
    expect(text).toContain('Next actions');
    expect(text).toContain('Break up a large service');
    expect(text).toContain('Layering drift');
    expect(text).toContain('Architecture summary');
  });

  it('preserves proposal navigation without trusted-artifact links', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    const links = Array.from(element.querySelectorAll('a')).map((anchor) =>
      anchor.textContent?.trim(),
    );

    expect(links).toContain('Review proposal');
    expect(links).toContain('View proposal');
    expect(links).not.toContain('View decision');
    expect(links).not.toContain('View engineering event');
  });

  it('does not render a trusted-artifact action for a PROPOSED proposal', async () => {
    const fixture = await render();
    const cards = fixture.nativeElement.querySelectorAll('.card');
    expect(cards[0]?.textContent).toContain('Review proposal');
    expect(cards[0]?.textContent).not.toContain('View insight');
  });

  it('renders Insight trusted-artifact navigation when the backend exposes it', async () => {
    const fixture = await render();
    expect(fixture.nativeElement.textContent).toContain('View insight');
  });

  it('renders Decision trusted-artifact navigation when the backend exposes it', async () => {
    getResult.mockReturnValue(
      of({
        ...completedResult,
        proposals: {
          ...completedResult.proposals,
          items: [
            {
              id: 'proposal-2',
              type: 'ENGINEERING_DECISION',
              status: 'ACCEPTED',
              confidence: 0.85,
              title: 'Retire deprecated endpoint',
              summary: 'The endpoint remains active after migration.',
              evidencePreview: ['Observation#12345678'],
              proposalId: 'proposal-2',
              trustedArtifact: {
                id: 'decision-1',
                type: 'DECISION',
                availability: 'AVAILABLE',
                detailAvailable: true,
              },
            },
          ],
        },
      }),
    );

    const fixture = await render();
    expect(fixture.nativeElement.textContent).toContain('View decision');
  });

  it('renders Engineering Event trusted-artifact navigation when the backend exposes it', async () => {
    getResult.mockReturnValue(
      of({
        ...completedResult,
        proposals: {
          ...completedResult.proposals,
          items: [
            {
              id: 'proposal-2',
              type: 'ENGINEERING_EVENT',
              status: 'ACCEPTED',
              confidence: 0.85,
              title: 'Retire deprecated endpoint',
              summary: 'The endpoint remains active after migration.',
              evidencePreview: ['Observation#12345678'],
              proposalId: 'proposal-2',
              trustedArtifact: {
                id: 'event-1',
                type: 'ENGINEERING_EVENT',
                availability: 'AVAILABLE',
                detailAvailable: true,
              },
            },
          ],
        },
      }),
    );

    const fixture = await render();
    expect(fixture.nativeElement.textContent).toContain('View engineering event');
  });

  it('renders an unavailable trusted-artifact state without a fabricated link', async () => {
    getResult.mockReturnValue(
      of({
        ...completedResult,
        proposals: {
          ...completedResult.proposals,
          items: [
            {
              id: 'proposal-2',
              type: 'ENGINEERING_DECISION',
              status: 'ACCEPTED',
              confidence: 0.85,
              title: 'Retire deprecated endpoint',
              summary: 'The endpoint remains active after migration.',
              evidencePreview: ['Observation#12345678'],
              proposalId: 'proposal-2',
              trustedArtifact: {
                id: null,
                type: 'DECISION',
                availability: 'UNAVAILABLE',
                detailAvailable: false,
              },
            },
          ],
        },
      }),
    );

    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Trusted Decision unavailable');
    const links = Array.from(element.querySelectorAll('a')).map((anchor) =>
      anchor.textContent?.trim(),
    );
    expect(links).not.toContain('View decision');
  });

  it('renders the failed result state', async () => {
    getResult.mockReturnValue(
      of({
        ...completedResult,
        analysis: { ...completedResult.analysis, status: 'FAILED' },
        execution: {
          success: false,
          failureCode: 'ANALYSIS_FAILED',
          failureMessage: 'Pipeline stopped',
        },
        proposals: { total: 0, byStatus: {}, byType: {}, items: [] },
        insights: { total: 0, items: [] },
        deliverables: { total: 0, items: [] },
        nextActions: [{ action: 'VIEW_DIAGNOSTICS', label: 'View diagnostics', available: true }],
      }),
    );

    const fixture = await render();
    expect(fixture.nativeElement.textContent).toContain('Analysis failed');
    expect(fixture.nativeElement.textContent).toContain('Pipeline stopped');
  });

  it('polls the canonical result while the analysis is in progress and stops on completed', async () => {
    const first = new Subject<AnalysisResult>();
    getResult.mockReturnValueOnce(first).mockReturnValue(of(completedResult));

    const component = TestBed.createComponent(AnalysisResultPage).componentInstance;
    const seen: unknown[] = [];
    component.view$.subscribe((value) => seen.push(value));

    await vi.advanceTimersByTimeAsync(50);
    expect(getResult).toHaveBeenCalledTimes(1);

    first.next({
      ...completedResult,
      analysis: {
        ...completedResult.analysis,
        status: 'IN_PROGRESS',
        completedAt: null,
        durationSeconds: null,
      },
      execution: { success: null, failureCode: null, failureMessage: null },
      proposals: { total: 0, byStatus: {}, byType: {}, items: [] },
      insights: { total: 0, items: [] },
      deliverables: { total: 0, items: [] },
      nextActions: [],
    });
    first.complete();

    await vi.advanceTimersByTimeAsync(10);
    expect(getResult).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(100);
    expect(getResult).toHaveBeenCalledTimes(2);
    expect(seen.length).toBeGreaterThan(1);
  });

  it('stops polling after a failed terminal result', async () => {
    getResult.mockReturnValue(
      of({
        ...completedResult,
        analysis: { ...completedResult.analysis, status: 'FAILED' },
        execution: {
          success: false,
          failureCode: 'ANALYSIS_FAILED',
          failureMessage: 'Pipeline stopped',
        },
        proposals: { total: 0, byStatus: {}, byType: {}, items: [] },
        insights: { total: 0, items: [] },
        deliverables: { total: 0, items: [] },
        nextActions: [{ action: 'VIEW_DIAGNOSTICS', label: 'View diagnostics', available: true }],
      }),
    );

    const component = TestBed.createComponent(AnalysisResultPage).componentInstance;
    component.view$.subscribe();
    await vi.advanceTimersToNextTimerAsync();
    const firstCount = getResult.mock.calls.length;
    await vi.advanceTimersToNextTimerAsync();
    expect(firstCount).toBeGreaterThan(0);
    expect(getResult).toHaveBeenCalledTimes(firstCount);
  });

  it('does not overlap or queue stale polling requests', async () => {
    const pending = new Subject<AnalysisResult>();
    getResult.mockReturnValue(pending);

    const component = TestBed.createComponent(AnalysisResultPage).componentInstance;
    component.view$.subscribe();
    await vi.advanceTimersToNextTimerAsync();
    const firstCount = getResult.mock.calls.length;
    await vi.advanceTimersToNextTimerAsync();

    expect(firstCount).toBeGreaterThan(0);
    expect(getResult).toHaveBeenCalledTimes(firstCount);
  });

  it('cancels stale polling when the route id changes', async () => {
    const first = new Subject<AnalysisResult>();
    const second = new Subject<AnalysisResult>();
    getResult.mockImplementation((id: string) => (id === 'analysis-id' ? first : second));

    const component = TestBed.createComponent(AnalysisResultPage).componentInstance;
    component.view$.subscribe();
    await vi.advanceTimersToNextTimerAsync();
    const initialCount = getResult.mock.calls.length;
    expect(getResult).toHaveBeenCalledWith('analysis-id');

    params.next(convertToParamMap({ id: 'analysis-2' }));
    await vi.advanceTimersToNextTimerAsync();
    const afterRouteChangeCount = getResult.mock.calls.length;
    expect(getResult).toHaveBeenCalledWith('analysis-2');
    expect(afterRouteChangeCount).toBeGreaterThan(initialCount);

    await vi.advanceTimersToNextTimerAsync();
    expect(getResult).toHaveBeenCalledTimes(afterRouteChangeCount);
  });

  it('renders repository scope using the backend repository name', async () => {
    getResult.mockReturnValue(
      of({
        ...completedResult,
        analysis: {
          ...completedResult.analysis,
          scope: 'REPOSITORY_SCOPE',
          repositoryName: 'backend-core',
          sourcesAnalyzed: ['backend-core'],
        },
      }),
    );

    const fixture = await render();
    expect(fixture.nativeElement.textContent).toContain('Repository: backend-core');
  });

  it('renders not-found state', async () => {
    getResult.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const notFound = await render();
    expect(notFound.nativeElement.textContent).toContain('Analysis result not found');
  });

  it('renders error state', async () => {
    getResult.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const failed = await render();
    expect(failed.nativeElement.textContent).toContain('Analysis result unavailable');
  });

  it('does not manually subscribe', () =>
    expect(AnalysisResultPage.toString()).not.toContain('.subscribe('));
});
