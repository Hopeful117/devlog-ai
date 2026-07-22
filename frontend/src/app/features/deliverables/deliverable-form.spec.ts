import { TestBed } from '@angular/core/testing';
import { DeliverableForm } from './deliverable-form';

describe('DeliverableForm', () => {
  it('maps bounded communication guidance without repository or prompt fields', () => {
    const fixture = TestBed.configureTestingModule({ imports: [DeliverableForm] }).createComponent(
      DeliverableForm,
    );
    fixture.componentInstance.projectId = 'project-id';
    const generate = vi.spyOn(fixture.componentInstance.generate, 'emit');
    fixture.componentInstance.form.patchValue({
      type: 'README',
      audience: ' Developers ',
      style: ' Clear ',
      language: 'en',
      additionalGuidance: ' Installation first ',
    });
    fixture.componentInstance.submit();
    expect(generate).toHaveBeenCalledWith({
      projectId: 'project-id',
      type: 'README',
      audience: 'Developers',
      style: 'Clear',
      language: 'en',
      additionalGuidance: 'Installation first',
    });
    expect(JSON.stringify(generate.mock.calls[0][0])).not.toContain('prompt');
  });
  it('enforces the ADR guidance limit', () => {
    const fixture = TestBed.configureTestingModule({ imports: [DeliverableForm] }).createComponent(
      DeliverableForm,
    );
    fixture.componentInstance.form.controls.additionalGuidance.setValue('x'.repeat(1001));
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });
  it('propagates the Analysis scope when launched beside validated Insights', () => {
    const fixture = TestBed.configureTestingModule({ imports: [DeliverableForm] }).createComponent(
      DeliverableForm,
    );
    fixture.componentInstance.projectId = 'project-id';
    fixture.componentInstance.analysisId = 'analysis-id';
    const generate = vi.spyOn(fixture.componentInstance.generate, 'emit');
    fixture.componentInstance.submit();
    expect(generate.mock.calls[0][0]).toMatchObject({
      projectId: 'project-id',
      analysisId: 'analysis-id',
    });
  });
});
