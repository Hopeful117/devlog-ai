import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ProposalReviewPage } from './proposal-review-page';
import { InsightProposalService } from './insight-proposal.service';
import { ProposalReviewerSessionService } from './proposal-reviewer-session.service';

describe('ProposalReviewPage', () => {
  const createItem = (overrides: Record<string, unknown> = {}) =>
    ({
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
      ...overrides,
    }) as const;
  const reviewer = '123e4567-e89b-42d3-a456-426614174000';
  const item = createItem();
  const review = {
    version: 'proposal-review-v2',
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
    sessionStorage.clear();
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
  it('renders a sequential current-proposal workflow', () => {
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('Sequential review');
    expect(fixture.nativeElement.textContent).toContain('Current proposal');
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
  it('submits an acceptance with the reviewer identity and resets the form', () => {
    const fixture = render();
    const component = fixture.componentInstance;
    component.form.controls.validatedBy.setValue(reviewer);
    component.form.controls.comment.setValue('  looks good  ');
    component.confirmation = 'ACCEPTED';
    component.decide(item);
    expect(accept).toHaveBeenCalledWith('proposal-id', {
      validatedBy: reviewer,
      comment: 'looks good',
      insightSeverity: 'INFO',
    });
    expect(component.confirmation).toBeNull();
    expect(component.form.controls.comment.value).toBe('');
  });
  it('advances automatically to the next pending proposal on the same page', () => {
    const decided = createItem({
      id: 'proposal-1',
      status: 'ACCEPTED',
      decidedAt: '2026-08-09T10:10:00Z',
    });
    const nextPending = createItem({
      id: 'proposal-2',
      payload: { title: 'Security', summary: 'Summary', rationale: 'Evidence' },
    });
    getReview
      .mockReturnValueOnce(
        of({
          ...review,
          counts: { total: 2, pending: 2, accepted: 0, rejected: 0 },
          items: [item, nextPending],
        }),
      )
      .mockReturnValue(
        of({
          ...review,
          counts: { total: 2, pending: 1, accepted: 1, rejected: 0 },
          items: [decided, nextPending],
        }),
      );

    const fixture = render();
    const component = fixture.componentInstance;
    component.form.controls.validatedBy.setValue(reviewer);
    component.confirmation = 'ACCEPTED';
    component.decide(item);
    fixture.detectChanges();

    expect(component.currentId).toBe('proposal-2');
    expect(fixture.nativeElement.textContent).toContain('Security');
  });
  it('advances to the next page when pending proposals remain later in the queue', () => {
    const decided = createItem({
      id: 'proposal-1',
      status: 'ACCEPTED',
      decidedAt: '2026-08-09T10:10:00Z',
    });
    const nextPagePending = createItem({
      id: 'proposal-2',
      payload: { title: 'Performance', summary: 'Summary', rationale: 'Evidence' },
    });
    getReview
      .mockReturnValueOnce(
        of({
          ...review,
          counts: { total: 2, pending: 1, accepted: 1, rejected: 0 },
          page: { number: 0, size: 10, totalPages: 2, hasPrevious: false, hasNext: true },
          items: [decided],
        }),
      )
      .mockReturnValueOnce(
        of({
          ...review,
          counts: { total: 2, pending: 1, accepted: 1, rejected: 0 },
          page: { number: 1, size: 10, totalPages: 2, hasPrevious: true, hasNext: false },
          items: [nextPagePending],
        }),
      );

    const fixture = render();
    fixture.detectChanges();

    expect(getReview).toHaveBeenNthCalledWith(1, 'analysis-id', 0, 10);
    expect(getReview).toHaveBeenNthCalledWith(2, 'analysis-id', 1, 10);
    expect(fixture.componentInstance.currentId).toBe('proposal-2');
    expect(fixture.nativeElement.textContent).toContain('Performance');
  });
  it('rejects a proposal with the reviewer identity', () => {
    const fixture = render();
    const component = fixture.componentInstance;
    component.form.controls.validatedBy.setValue(reviewer);
    component.confirmation = 'REJECTED';
    component.decide(item);
    expect(reject).toHaveBeenCalledWith('proposal-id', {
      validatedBy: reviewer,
      comment: null,
    });
    expect(reject).toHaveBeenCalledTimes(1);
  });
  it('marks the form touched instead of submitting when invalid', () => {
    const fixture = render();
    const component = fixture.componentInstance;
    component.form.controls.validatedBy.setValue('');
    component.form.controls.validatedBy.markAsUntouched();
    component.confirmation = 'ACCEPTED';
    component.decide(item);
    expect(accept).not.toHaveBeenCalled();
    expect(component.form.controls.validatedBy.touched).toBe(true);
  });
  it('surfaces an error when the review fails to load', () => {
    getReview.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const fixture = render();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
  });
  it('select and current expose the active proposal', () => {
    const fixture = render();
    const component = fixture.componentInstance;
    const other = { ...item, id: 'other-id' };
    component.select(other);
    expect(component.current([other, item])).toBe(other);
  });
  it('shows explicit completion when no pending proposals remain', () => {
    getReview.mockReturnValue(
      of({
        ...review,
        counts: { total: 1, pending: 0, accepted: 1, rejected: 0 },
        items: [createItem({ status: 'ACCEPTED', decidedAt: '2026-08-09T10:10:00Z' })],
      }),
    );
    const fixture = render();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Review complete');
  });
  it('clears the reviewer session', () => {
    const fixture = render();
    const component = fixture.componentInstance;
    component.form.controls.validatedBy.setValue(reviewer);
    component.clearReviewer();
    expect(component.form.controls.validatedBy.value).toBe('');
  });
});
