import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { InsightProposalService } from './insight-proposal.service';
import { InsightService } from './insight.service';
describe('Insight review services', () => {
  let proposals: InsightProposalService;
  let insights: InsightService;
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
    proposals = TestBed.inject(InsightProposalService);
    insights = TestBed.inject(InsightService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('lists and reads proposals by Analysis', () => {
    proposals.getProposalsByAnalysis('a').subscribe();
    proposals.getProposal('p').subscribe();
    for (const url of ['/api/v1/proposals/analysis/a', '/api/v1/proposals/p']) {
      const request = http.expectOne(url);
      expect(request.request.method).toBe('GET');
      request.flush(url.endsWith('/a') ? [] : {});
    }
  });
  it('reads the bounded proposal review page with exact query parameters', () => {
    proposals.getProposalReview('analysis/id', 2, 10).subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/analyses/analysis%2Fid/proposal-review' &&
        candidate.params.get('page') === '2' &&
        candidate.params.get('size') === '10',
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
  it('sends the exact Accept validation payload', () => {
    proposals
      .acceptProposal('p', {
        validatedBy: 'reviewer',
        comment: 'Approved',
        insightSeverity: 'WARNING',
      })
      .subscribe();
    const request = http.expectOne('/api/v1/validations');
    expect(request.request.body).toEqual({
      proposalId: 'p',
      decision: 'ACCEPTED',
      validatedBy: 'reviewer',
      comment: 'Approved',
      insightSeverity: 'WARNING',
    });
    request.flush({});
  });
  it('sends the exact Reject validation payload without severity', () => {
    proposals.rejectProposal('p', { validatedBy: 'reviewer', comment: 'Unsupported' }).subscribe();
    const request = http.expectOne('/api/v1/validations');
    expect(request.request.body).toEqual({
      proposalId: 'p',
      decision: 'REJECTED',
      validatedBy: 'reviewer',
      comment: 'Unsupported',
      insightSeverity: null,
    });
    request.flush({});
  });
  it('lists Insights by Analysis and filtered Project', () => {
    insights.getInsightsByAnalysis('a').subscribe();
    insights.getInsightsByProject('p', 'ARCHITECTURAL', 'CRITICAL').subscribe();
    for (const url of [
      '/api/v1/insights/analysis/a',
      '/api/v1/insights/project/p/type/ARCHITECTURAL/severity/CRITICAL',
    ]) {
      const request = http.expectOne(url);
      request.flush([]);
    }
  });
  it('builds every Insight project filter suffix combination', () => {
    insights.getInsightsByProject('p', 'ARCHITECTURAL').subscribe();
    insights.getInsightsByProject('p', undefined, 'CRITICAL').subscribe();
    insights.getInsightsByProject('p').subscribe();
    for (const url of [
      '/api/v1/insights/project/p/type/ARCHITECTURAL',
      '/api/v1/insights/project/p/severity/CRITICAL',
      '/api/v1/insights/project/p',
    ]) {
      const request = http.expectOne(url);
      expect(request.request.method).toBe('GET');
      request.flush([]);
    }
  });
});
