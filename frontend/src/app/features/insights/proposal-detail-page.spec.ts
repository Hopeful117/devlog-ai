import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { ProposalDetailPage } from './proposal-detail-page';
import { InsightProposalService } from './insight-proposal.service';
import { InsightService } from './insight.service';
import { ProposalReviewerSessionService } from './proposal-reviewer-session.service';
const reviewer = '123e4567-e89b-42d3-a456-426614174000';
const proposed = {
  id: 'proposal-id',
  projectId: 'project-id',
  analysisId: 'analysis-id',
  type: 'INSIGHT',
  status: 'PROPOSED',
  payload: { title: '<img src=x onerror=alert(1)>' },
  insight: {
    insightType: 'ARCHITECTURE_DESCRIPTION',
    title: '<img src=x onerror=alert(1)>',
    summary: 'Summary',
    rationale: 'Because evidence',
  },
  confidence: 0.8,
  supportingFactIds: ['fact-id'],
  supportingObservationIds: ['observation-id'],
  evidenceReferences: ['pom.xml:1'],
  createdAt: '2026-07-22T10:00:00Z',
  decidedAt: null,
} as const;
const accepted = { ...proposed, status: 'ACCEPTED', decidedAt: '2026-07-22T11:00:00Z' } as const;
const rejected = { ...proposed, status: 'REJECTED', decidedAt: '2026-07-22T11:00:00Z' } as const;
const validation = {
  id: 'validation-id',
  proposalId: proposed.id,
  decision: 'ACCEPTED',
  validatedAt: '2026-07-22T11:00:00Z',
  validatedBy: reviewer,
  comment: 'ok',
} as const;
describe('ProposalDetailPage', () => {
  const getProposal = vi.fn();
  const acceptProposal = vi.fn();
  const rejectProposal = vi.fn();
  const getDecision = vi.fn();
  const getInsightsByAnalysis = vi.fn();
  beforeEach(async () => {
    sessionStorage.clear();
    getProposal.mockReset().mockReturnValue(of(proposed));
    acceptProposal.mockReset().mockReturnValue(of(validation));
    rejectProposal.mockReset().mockReturnValue(of({ ...validation, decision: 'REJECTED' }));
    getDecision.mockReset().mockReturnValue(of(validation));
    getInsightsByAnalysis.mockReset().mockReturnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [ProposalDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: proposed.id })) },
        },
        {
          provide: InsightProposalService,
          useValue: { getProposal, acceptProposal, rejectProposal, getDecision },
        },
        { provide: InsightService, useValue: { getInsightsByAnalysis } },
      ],
    }).compileComponents();
  });
  function render() {
    const fixture = TestBed.createComponent(ProposalDetailPage);
    fixture.detectChanges();
    return fixture;
  }
  function prepare(component: ProposalDetailPage, decision: 'ACCEPTED' | 'REJECTED') {
    component.form.controls.comment.setValue('review');
    component.confirmation = decision;
  }
  it('renders evidence and model content safely', () => {
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('fact-id');
    expect(fixture.nativeElement.textContent).toContain('observation-id');
    expect(fixture.nativeElement.textContent).toContain('<img');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="proposal-status"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="accept-proposal"]')?.textContent,
    ).toContain('Accept proposal');
    expect(
      fixture.nativeElement.querySelector('[data-testid="reject-proposal"]')?.textContent,
    ).toContain('Reject proposal');
    expect(fixture.nativeElement.querySelector('label[for="comment"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('session reviewer identity automatically');
  });
  it('creates and reuses a valid local reviewer UUID automatically', () => {
    const fixture = render();
    expect(fixture.componentInstance.form.controls.validatedBy.valid).toBe(true);
    const generated = fixture.componentInstance.form.controls.validatedBy.value;
    expect(generated).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
    expect(TestBed.inject(ProposalReviewerSessionService).get()).toBe(generated);

    const second = render();
    expect(second.componentInstance.form.controls.validatedBy.value).toBe(generated);
  });
  it('can reset the local reviewer session', () => {
    const fixture = render();
    const first = fixture.componentInstance.form.controls.validatedBy.value;

    fixture.componentInstance.resetLocalReviewerId();
    fixture.detectChanges();

    const second = fixture.componentInstance.form.controls.validatedBy.value;
    expect(second).not.toBe(first);
    expect(second).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });
  it('accepts then returns to the source Analysis proposal list', () => {
    getProposal.mockReturnValueOnce(of(proposed)).mockReturnValue(of(accepted));
    getInsightsByAnalysis.mockReturnValue(
      of([{ id: 'insight-id', proposalId: proposed.id, title: 'Validated', severity: 'WARNING' }]),
    );
    const fixture = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    prepare(fixture.componentInstance, 'ACCEPTED');
    const generated = fixture.componentInstance.form.controls.validatedBy.value;
    fixture.componentInstance.decide(proposed.analysisId);
    fixture.detectChanges();
    expect(acceptProposal).toHaveBeenCalledWith(proposed.id, {
      validatedBy: generated,
      comment: 'review',
      insightSeverity: 'INFO',
    });
    expect(navigate).toHaveBeenCalledWith(['/analyses', proposed.analysisId], {
      fragment: 'insight-proposals',
    });
  });
  it('rejects then returns to the source Analysis proposal list', () => {
    getProposal.mockReturnValueOnce(of(proposed)).mockReturnValue(of(rejected));
    const fixture = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    prepare(fixture.componentInstance, 'REJECTED');
    const generated = fixture.componentInstance.form.controls.validatedBy.value;
    fixture.componentInstance.decide(proposed.analysisId);
    fixture.detectChanges();
    expect(rejectProposal).toHaveBeenCalledWith(proposed.id, {
      validatedBy: generated,
      comment: 'review',
    });
    expect(navigate).toHaveBeenCalledWith(['/analyses', proposed.analysisId], {
      fragment: 'insight-proposals',
    });
  });
  it('reloads the actual state after a 409 without retrying', () => {
    acceptProposal.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'Proposal has already been decided' },
          }),
      ),
    );
    getProposal.mockReturnValueOnce(of(proposed)).mockReturnValue(of(accepted));
    const fixture = render();
    prepare(fixture.componentInstance, 'ACCEPTED');
    fixture.componentInstance.decide(proposed.analysisId);
    fixture.detectChanges();
    expect(acceptProposal).toHaveBeenCalledTimes(1);
    expect(getProposal).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('ACCEPTED');
  });
  it('prevents duplicate decisions while pending', () => {
    const pending = new Subject();
    acceptProposal.mockReturnValue(pending);
    const fixture = render();
    prepare(fixture.componentInstance, 'ACCEPTED');
    fixture.componentInstance.decide(proposed.analysisId);
    fixture.componentInstance.decide(proposed.analysisId);
    expect(acceptProposal).toHaveBeenCalledTimes(1);
  });
  it('hides actions for already decided proposals', () => {
    getProposal.mockReturnValue(of(accepted));
    const fixture = render();
    expect(fixture.nativeElement.textContent).not.toContain('Confirm decision');
    expect(fixture.nativeElement.textContent).toContain('Final decision');
  });
  it('does not manually subscribe', () =>
    expect(ProposalDetailPage.toString()).not.toContain('.subscribe('));
});
