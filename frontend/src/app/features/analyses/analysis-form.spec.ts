import { TestBed } from '@angular/core/testing';
import { AnalysisForm } from './analysis-form';

describe('AnalysisForm', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisForm] }).compileComponents();
  });
  it('requires an objective and enforces priority limits', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    expect(component.form.invalid).toBe(true);
    component.form.controls.objective.setValue('architecture-overview-v1');
    component.form.controls.priorities.setValue(
      Array.from({ length: 11 }, (_, index) => `P${index}`).join('\n'),
    );
    component.submit();
    expect(component.form.controls.priorities.hasError('priorities')).toBe(true);
  });
  it('maps User Guidance and includes a target revision', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    component.projectId = 'project-id';
    component.objectives = [
      {
        label: 'Review architecture',
        description: 'Analyze architecture',
        intentId: 'architecture-overview-v1',
        scope: 'PROJECT_SCOPE',
      },
    ];
    const emitted = vi.fn();
    component.launch.subscribe(emitted);
    component.form.patchValue({
      objective: 'architecture-overview-v1',
      targetRevision: ' release-1 ',
      focus: ' architecture ',
      audience: 'team',
      priorities: 'Docker\nSpring',
    });
    component.submit();
    expect(emitted).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 'project-id',
        intentId: 'architecture-overview-v1',
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
    component.objectives = [
      {
        label: 'Understand project',
        description: 'Get overview',
        intentId: 'describe-project-v1',
        scope: 'PROJECT_SCOPE',
      },
    ];
    const emitted = vi.fn();
    component.launch.subscribe(emitted);
    component.form.controls.objective.setValue('describe-project-v1');
    component.submit();
    expect(emitted.mock.calls[0][0]).toEqual({
      projectId: 'project-id',
      intentId: 'describe-project-v1',
    });
  });

  // --- Story 0099 contract tests (GREEN after restoration) ---

  it('should not expose AnalysisType selector', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    expect('type' in component.form.controls).toBe(false);
  });

  it('should expose objective control instead of intentKey', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    expect('intentKey' in component.form.controls).toBe(false);
  });

  it('should not emit type field in request payload', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    component.projectId = 'project-id';
    component.objectives = [
      {
        label: 'Understand project',
        description: 'Get overview',
        intentId: 'describe-project-v1',
        scope: 'PROJECT_SCOPE',
      },
    ];
    const emitted = vi.fn();
    component.launch.subscribe(emitted);
    component.form.controls.objective.setValue('describe-project-v1');
    component.submit();
    const payload = emitted.mock.calls[0][0];
    expect('type' in payload).toBe(false);
  });

  it('should accept objectives input instead of intents', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    expect(component.objectives).toBeDefined();
  });
});
