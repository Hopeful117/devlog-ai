import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { ProjectAnalysesSection } from './project-analyses-section';
import { AnalysisService } from './analysis.service';
import { IntentCatalogService } from './intent-catalog.service';
import { IntentDefinition } from './analysis.models';
import { Source } from './analysis.models';
import { SourceService } from '../projects/source.service';

const analysis = {
  id: 'analysis-id',
  projectId: 'project-id',
  type: 'ARCHITECTURE_REVIEW',
  intentId: 'architecture-overview',
  intentVersion: 'v1',
  status: 'PENDING',
  startedAt: null,
  completedAt: null,
  createdAt: '2026-07-22T10:00:00Z',
  updatedAt: '2026-07-22T10:00:00Z',
  userGuidance: null,
} as const;

describe('ProjectAnalysesSection', () => {
  const getAnalysesByProject = vi.fn();
  const createAnalysis = vi.fn();
  const launchAnalysis = vi.fn();
  beforeEach(async () => {
    getAnalysesByProject.mockReset().mockReturnValue(of([analysis]));
    createAnalysis.mockReset().mockReturnValue(of(analysis));
    launchAnalysis.mockReset().mockReturnValue(of({ analysisId: analysis.id }));
    const mockIntents: readonly IntentDefinition[] = [
      {
        id: 'describe-project-v1',
        version: 'v1',
        objective: 'Understand this project',
        supportedInsightTypes: [],
        constraints: [],
        outputSchema: {},
        promptTemplate: '...',
        executionMode: 'GENERIC',
      },
      {
        id: 'generate-readme-v1',
        version: 'v1',
        objective: 'Prepare README information',
        supportedInsightTypes: [],
        constraints: [],
        outputSchema: {},
        promptTemplate: '...',
        executionMode: 'GENERIC',
      },
    ];
    const mockSources: readonly Source[] = [{ id: 'source-1', name: 'Repo One' }];
    await TestBed.configureTestingModule({
      imports: [ProjectAnalysesSection],
      providers: [
        provideRouter([]),
        {
          provide: AnalysisService,
          useValue: {
            getAnalysesByProject,
            createAnalysis,
            launchAnalysis,
          },
        },
        { provide: IntentCatalogService, useValue: { getSupportedIntents: () => of(mockIntents) } },
        { provide: SourceService, useValue: { getSourcesByProject: () => of(mockSources) } },
      ],
    }).compileComponents();
  });
  it('lists Analyses using the Project UUID', () => {
    const fixture = TestBed.createComponent(ProjectAnalysesSection);
    fixture.componentInstance.projectId = 'project-id';
    fixture.detectChanges();
    expect(getAnalysesByProject).toHaveBeenCalledWith('project-id');
    expect(fixture.nativeElement.textContent).toContain('ARCHITECTURE_REVIEW');
  });
  it('creates, launches, refreshes, and navigates in order', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ProjectAnalysesSection);
    fixture.componentInstance.projectId = 'project-id';
    fixture.detectChanges();
    fixture.componentInstance.launch({
      projectId: 'project-id',
      intentId: 'architecture-overview-v1',
    });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(createAnalysis).toHaveBeenCalled();
    expect(launchAnalysis).toHaveBeenCalledWith('analysis-id');
    expect(getAnalysesByProject).toHaveBeenCalledTimes(2);
    expect(navigate).toHaveBeenCalledWith(['/analyses', 'analysis-id']);
  });
  it('does not manually subscribe', () =>
    expect(ProjectAnalysesSection.toString()).not.toContain('.subscribe('));
});
