import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ProposalReviewPage } from './proposal-review-page';
import { InsightProposalService } from './insight-proposal.service';
import { ProposalReviewerSessionService } from './proposal-reviewer-session.service';

describe('ProposalReviewPage', () => {
  const reviewer = '123e4567-e89b-42d3-a456-426614174000';
  const item = {
    id: 'proposal-id',
    projectId: 'project-id',
    analysisId: 'analysis-id',
    sourceIndex: 0,
    type: 'INSIGHT',
    status: 'PROPOSED',
    payload: { title: 'Architecture', summary: 'Summary', rationale: 'Evidence' },
    confidence: 0.8,
    evidenceReferences: ['pom.xml'],
    facts: [],
    observations: [],
    decision: null,
    insight: null,
    createdAt: '2026-08-09T10:00:00Z',
    decidedAt: null,
  } as const;
  const review = {
    version: 'proposal-review-v1',
    analysisId: 'analysis-id',
    projectId: 'project-id',
    counts: { total: 1, pending: 1, accepted: 0, rejected: 0 },
    page: { number: 0, size: 10, totalPages: 1, hasPrevious: false, hasNext: false },
    items: [item],
  } as const;
  const getReview = vi.fn();
  const accept = vi.fn();
  const reject = vi.fn();
  beforeEach(async () => {
    getReview.mockReset().mockReturnValue(of(review));
    accept.mockReset().mockReturnValue(of({ id: 'v', proposalId: item.id, decision: 'ACCEPTED' }));
    reject.mockReset().mockReturnValue(of({ id: 'v', proposalId: item.id, decision: 'REJECTED' }));
    await TestBed.configureTestingModule({
      imports: [ProposalReviewPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'analysis-id' }) } },
        },
        {
          provide: InsightProposalService,
          useValue: {
            getProposalReview: getReview,
            acceptProposal: accept,
            rejectProposal: reject,
          },
        },
        ProposalReviewerSessionService,
      ],
    }).compileComponents();
  });
  function render() {
    const fixture = TestBed.createComponent(ProposalReviewPage);
    fixture.detectChanges();
    return fixture;
  }
  it('renders queue progress and requires explicit reviewer identity', () => {
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('1 pending');
    expect(fixture.nativeElement.textContent).toContain('Architecture');
    fixture.componentInstance.generateReviewer();
    expect(fixture.componentInstance.form.controls.validatedBy.valid).toBe(true);
  });
  it('prevents duplicate acceptance while pending', () => {
    const pending = new Subject();
    accept.mockReturnValue(pending);
    const fixture = render();
    const component = fixture.componentInstance;
    component.form.controls.validatedBy.setValue(reviewer);
    component.confirmation = 'ACCEPTED';
    component.decide(item);
    component.decide(item);
    expect(accept).toHaveBeenCalledTimes(1);
  });
  it('refreshes a conflict without retrying', () => {
    accept.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    const fixture = render();
    const component = fixture.componentInstance;
    component.form.controls.validatedBy.setValue(reviewer);
    component.confirmation = 'ACCEPTED';
    component.decide(item);
    fixture.detectChanges();
    expect(accept).toHaveBeenCalledTimes(1);
    expect(getReview).toHaveBeenCalledTimes(2);
  });
});
