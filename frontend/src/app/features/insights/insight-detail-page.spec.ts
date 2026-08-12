import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { InsightDetailPage } from './insight-detail-page';
import { InsightService } from './insight.service';
const base = {
  id: 'insight-id',
  projectId: 'project-id',
  analysisId: 'analysis-id',
  proposalId: 'proposal-id',
  validationId: 'validation-id',
  type: 'ARCHITECTURAL',
  severity: 'WARNING',
  title: 'Monolith should be split',
  content: 'The service boundary has drifted.',
  rationale: 'Because the evidence points to a single delivery bottleneck.',
  confidence: 0.82,
  evidenceReferences: ['pom.xml:42'],
  sourceType: 'ARCHITECTURE_DESCRIPTION',
  createdAt: '2026-07-22T10:00:00Z',
  updatedAt: '2026-07-22T10:00:00Z',
} as const;
describe('InsightDetailPage', () => {
  const getInsight = vi.fn();
  beforeEach(async () => {
    getInsight.mockReset().mockReturnValue(of(base));
    await TestBed.configureTestingModule({
      imports: [InsightDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: base.id })) },
        },
        { provide: InsightService, useValue: { getInsight } },
      ],
    }).compileComponents();
  });
  function render() {
    const fixture = TestBed.createComponent(InsightDetailPage);
    fixture.detectChanges();
    return fixture;
  }
  it('renders the preserved semantic richness', () => {
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain(base.rationale);
    expect(fixture.nativeElement.textContent).toContain(String(base.confidence));
    expect(fixture.nativeElement.textContent).toContain(base.evidenceReferences[0]);
    expect(fixture.nativeElement.textContent).toContain(`source ${base.sourceType}`);
  });
  it('omits semantic sections when the Insight is bare', () => {
    getInsight.mockReturnValue(
      of({ ...base, rationale: null, confidence: null, evidenceReferences: [], sourceType: null }),
    );
    const fixture = render();
    expect(fixture.nativeElement.textContent).not.toContain('Rationale');
    expect(fixture.nativeElement.textContent).not.toContain('Confidence');
    expect(fixture.nativeElement.textContent).not.toContain('Evidence');
    expect(fixture.nativeElement.textContent).not.toContain(`source ${base.sourceType}`);
  });
  it('renders evidence as a list when multiple references exist', () => {
    getInsight.mockReturnValue(
      of({ ...base, evidenceReferences: ['pom.xml:1', 'src/app/a.ts:10'] }),
    );
    const fixture = render();
    expect(fixture.nativeElement.querySelectorAll('li')).toHaveLength(2);
  });
  it('shows loading, error and not-found states', () => {
    getInsight.mockReturnValue(new Subject());
    const loading = render();
    expect(loading.nativeElement.textContent).toContain('Loading Insight');

    getInsight.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const failed = render();
    expect(failed.nativeElement.textContent).toContain('Java Core is unavailable');

    getInsight.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { message: 'nope' } })),
    );
    const missing = render();
    expect(missing.nativeElement.textContent).toContain('Insight not found');
  });
  it('does not manually subscribe', () =>
    expect(InsightDetailPage.toString()).not.toContain('.subscribe('));
});
