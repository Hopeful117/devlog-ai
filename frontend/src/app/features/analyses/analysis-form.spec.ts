import { TestBed } from '@angular/core/testing';
import { AnalysisForm } from './analysis-form';
import { Source } from './analysis.models';

describe('AnalysisForm', () => {
  const mockSources: readonly Source[] = [
    { id: 'source-1', name: 'Repo One' },
    { id: 'source-2', name: 'Repo Two' },
  ];
  const mockObjectives = [
    {
      label: 'Understand this project',
      description: 'Get a comprehensive overview of the project across all repositories.',
      intentId: 'describe-project-v1',
      scope: 'PROJECT_SCOPE' as const,
    },
    {
      label: 'Prepare README information',
      description:
        'Generate structured information needed for a README file for a specific repository.',
      intentId: 'generate-readme-v1',
      scope: 'REPOSITORY_SCOPE' as const,
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisForm] }).compileComponents();
  });

  it('requires an objective and enforces priority limits', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    component.objectives = mockObjectives;
    component.sources = mockSources;
    expect(component.form.invalid).toBe(true);
    component.form.controls.objective.setValue('describe-project-v1');
    component.form.controls.priorities.setValue(
      Array.from({ length: 11 }, (_, index) => `P${index}`).join('\n'),
    );
    component.submit();
    expect(component.form.controls.priorities.hasError('priorities')).toBe(true);
  });

  it('maps User Guidance and includes a target revision for repository scope', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    component.projectId = 'project-id';
    component.objectives = mockObjectives;
    component.sources = mockSources;
    const emitted = vi.fn();
    component.launch.subscribe(emitted);
    component.form.patchValue({
      objective: 'generate-readme-v1',
      sourceId: 'source-1',
      targetRevision: ' release-1 ',
      focus: ' architecture ',
      audience: 'team',
      priorities: 'Docker\nSpring',
    });
    component.submit();
    expect(emitted).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 'project-id',
        intentId: 'generate-readme-v1',
        sourceId: 'source-1',
        targetRevision: 'release-1',
        userGuidance: expect.objectContaining({
          focus: 'architecture',
          priorities: ['Docker', 'Spring'],
        }),
      }),
    );
  });

  it('omits blank revision and empty guidance for project scope', () => {
    const component = TestBed.createComponent(AnalysisForm).componentInstance;
    component.projectId = 'project-id';
    component.objectives = mockObjectives;
    component.sources = mockSources;
    const emitted = vi.fn();
    component.launch.subscribe(emitted);
    component.form.controls.objective.setValue('describe-project-v1');
    component.submit();
    expect(emitted.mock.calls[0][0]).toEqual({
      projectId: 'project-id',
      intentId: 'describe-project-v1',
    });
  });
});
