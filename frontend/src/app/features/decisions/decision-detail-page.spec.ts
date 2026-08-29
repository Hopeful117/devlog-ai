import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { DecisionDetailPage } from './decision-detail-page';
import { DecisionService } from './decision.service';

const base = {
  id: 'decision-id',
  projectId: 'project-id',
  proposalId: 'proposal-id',
  title: 'Adopt a module boundary',
  context: 'The current service owns too many concerns.',
  choice: 'Split orchestration from transport.',
  rationale: 'This reduces coupling and makes delivery safer.',
  consequences: 'Requires a migration plan.',
  createdAt: '2026-07-22T10:00:00Z',
  updatedAt: '2026-07-22T10:00:00Z',
} as const;

describe('DecisionDetailPage', () => {
  const getDecision = vi.fn();

  beforeEach(async () => {
    getDecision.mockReset().mockReturnValue(of(base));
    await TestBed.configureTestingModule({
      imports: [DecisionDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: base.id })) },
        },
        { provide: DecisionService, useValue: { getDecision } },
      ],
    }).compileComponents();
  });

  function render() {
    const fixture = TestBed.createComponent(DecisionDetailPage);
    fixture.detectChanges();
    return fixture;
  }

  it('renders a trusted Decision with proposal navigation', () => {
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain(base.title);
    expect(fixture.nativeElement.textContent).toContain(base.context);
    expect(fixture.nativeElement.textContent).toContain(base.choice);
    expect(fixture.nativeElement.textContent).toContain(base.rationale);
    expect(fixture.nativeElement.textContent).toContain(base.consequences);
    expect(fixture.nativeElement.textContent).toContain(base.proposalId);
  });

  it('omits the proposal section when the Decision has no persisted proposal provenance', () => {
    getDecision.mockReturnValue(of({ ...base, proposalId: null, consequences: null }));
    const fixture = render();
    expect(fixture.nativeElement.textContent).not.toContain('Proposal');
    expect(fixture.nativeElement.textContent).not.toContain(base.proposalId);
    expect(fixture.nativeElement.textContent).not.toContain('Consequences');
  });

  it('shows loading, not-found and error states', () => {
    getDecision.mockReturnValue(new Subject());
    const loading = render();
    expect(loading.nativeElement.textContent).toContain('Loading Decision');

    getDecision.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { message: 'nope' } })),
    );
    const missing = render();
    expect(missing.nativeElement.textContent).toContain('Decision not found');

    getDecision.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const failed = render();
    expect(failed.nativeElement.textContent).toContain('Java Core is unavailable');
  });

  it('does not manually subscribe', () =>
    expect(DecisionDetailPage.toString()).not.toContain('.subscribe('));
});
