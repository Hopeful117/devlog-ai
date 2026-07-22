import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AnalysisDeliverablePanel } from './analysis-deliverable-panel';
import { DeliverableService } from './deliverable.service';

describe('AnalysisDeliverablePanel', () => {
  it('opens beside validated Insights and generates an Analysis-scoped Deliverable', () => {
    const generate = vi.fn(() => of({ id: 'deliverable-id' }));
    TestBed.configureTestingModule({
      imports: [AnalysisDeliverablePanel],
      providers: [provideRouter([]), { provide: DeliverableService, useValue: { generate } }],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AnalysisDeliverablePanel);
    fixture.componentRef.setInput('projectId', 'project-id');
    fixture.componentRef.setInput('analysisId', 'analysis-id');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    fixture.componentInstance.generate({
      projectId: 'project-id',
      analysisId: 'analysis-id',
      type: 'README',
      audience: 'Engineers',
      style: 'Concise',
      language: 'en',
    });
    fixture.detectChanges();
    expect(generate).toHaveBeenCalledWith(expect.objectContaining({ analysisId: 'analysis-id' }));
    expect(navigate).toHaveBeenCalledWith(['/deliverables', 'deliverable-id']);
  });
  it('contains no manual subscription', () =>
    expect(AnalysisDeliverablePanel.toString()).not.toContain('.subscribe('));
});
