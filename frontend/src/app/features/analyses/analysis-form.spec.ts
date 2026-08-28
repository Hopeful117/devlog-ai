import { TestBed } from '@angular/core/testing';
import { AnalysisForm } from './analysis-form';

describe('AnalysisForm', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisForm] }).compileComponents();
  });
  it('requires an Intent and enforces priority limits', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    expect(component.form.invalid).toBe(true);
    component.form.controls.intentKey.setValue('architecture-overview-v1');
    component.form.controls.priorities.setValue(
      Array.from({ length: 11 }, (_, index) => `P${index}`).join('\n'),
    );
    component.submit();
    expect(component.form.controls.priorities.hasError('priorities')).toBe(true);
  });
  it('maps User Guidance and includes a target revision', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    component.projectId = 'project-id';
    const emitted = vi.fn();
    component.launch.subscribe(emitted);
    component.form.patchValue({
      intentKey: 'architecture-overview-v1',
      targetRevision: ' release-1 ',
      focus: ' architecture ',
      audience: 'team',
      priorities: 'Docker\nSpring',
    });
    component.submit();
    expect(emitted).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 'project-id',
        targetRevision: 'release-1',
        userGuidance: expect.objectContaining({
          focus: 'architecture',
          priorities: ['Docker', 'Spring'],
        }),
      }),
    );
  });
  it('omits blank revision and empty guidance', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    component.projectId = 'project-id';
    const emitted = vi.fn();
    component.launch.subscribe(emitted);
    component.form.controls.intentKey.setValue('describe-project-v1');
    component.submit();
    expect(emitted.mock.calls[0][0]).toEqual({
      projectId: 'project-id',
      type: 'ARCHITECTURE_REVIEW',
      intentId: 'describe-project-v1',
    });
  });
});
