import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AnalysisInsightsSection } from './analysis-insights-section';
import { InsightProposalService } from './insight-proposal.service';
import { InsightService } from './insight.service';
import { DeliverableService } from '../deliverables/deliverable.service';
describe('AnalysisInsightsSection', () => {
  const list = vi.fn();
  const getInsights = vi.fn();
  beforeEach(async () => {
    list.mockReset();
    getInsights.mockReset().mockReturnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [AnalysisInsightsSection],
      providers: [
        provideRouter([]),
        { provide: InsightProposalService, useValue: { getProposalsByAnalysis: list } },
        { provide: InsightService, useValue: { getInsightsByAnalysis: getInsights } },
        { provide: DeliverableService, useValue: { generate: vi.fn() } },
      ],
    }).compileComponents();
  });
  function render(result = of<readonly never[]>([])) {
    list.mockReturnValue(result);
    const fixture = TestBed.createComponent(AnalysisInsightsSection);
    fixture.componentInstance.analysisId = 'analysis-id';
    fixture.detectChanges();
    return fixture;
  }
  it('shows proposal loading and empty states', () => {
    const pending = render(new Subject());
    expect(pending.nativeElement.textContent).toContain('Loading proposals');
    pending.destroy();
    const empty = render();
    expect(empty.nativeElement.querySelector('[data-testid="proposals-empty"]')).toBeTruthy();
  });
  it('shows proposal errors', () => {
    const fixture = render(throwError(() => new HttpErrorResponse({ status: 0 })));
    expect(fixture.nativeElement.textContent).toContain('Java Core is unavailable');
  });
  it('explains the deterministic zero-proposal Mock result', () => {
    list.mockReturnValue(of([]));
    const fixture = TestBed.createComponent(AnalysisInsightsSection);
    fixture.componentRef.setInput('analysisId', 'analysis-id');
    fixture.componentRef.setInput('executionStatus', 'COMPLETED');
    fixture.componentRef.setInput('provider', 'mock');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mock provider');
    expect(fixture.nativeElement.textContent).not.toContain('OpenAI was called');
  });
  it('does not manually subscribe', () =>
    expect(AnalysisInsightsSection.toString()).not.toContain('.subscribe('));
  it('shows the Deliverable action only when validated Insights exist', () => {
    getInsights.mockReturnValueOnce(
      of([
        {
          id: 'insight-id',
          projectId: 'project-id',
          title: 'Validated architecture',
          type: 'ARCHITECTURAL',
          severity: 'INFO',
        },
      ]),
    );
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('Generate a Deliverable');
  });
});
