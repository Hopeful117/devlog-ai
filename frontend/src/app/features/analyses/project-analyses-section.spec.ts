import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { ProjectAnalysesSection } from './project-analyses-section';
import { AnalysisService } from './analysis.service';
import { IntentCatalogService } from './intent-catalog.service';
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
  const getSourcesByProject = vi.fn();
  beforeEach(async () => {
    getAnalysesByProject.mockReset().mockReturnValue(of([analysis]));
    createAnalysis.mockReset().mockReturnValue(of(analysis));
    launchAnalysis.mockReset().mockReturnValue(of({ analysisId: analysis.id }));
    getSourcesByProject.mockReset().mockReturnValue(of([]));
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
        { provide: IntentCatalogService, useValue: { getSupportedIntents: () => of([]) } },
        { provide: SourceService, useValue: { getSourcesByProject } },
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

const genericIntents = [
  {
    id: 'describe-project',
    version: 'v1',
    executionMode: 'GENERIC',
    objective: 'Describe the project',
    supportedInsightTypes: [],
    constraints: [],
    outputSchema: {},
    promptTemplate: '',
  },
  {
    id: 'generate-readme',
    version: 'v1',
    executionMode: 'GENERIC',
    objective: 'Generate README',
    supportedInsightTypes: [],
    constraints: [],
    outputSchema: {},
    promptTemplate: '',
  },
  {
    id: 'architecture-overview',
    version: 'v1',
    executionMode: 'GENERIC',
    objective: 'Architecture overview',
    supportedInsightTypes: [],
    constraints: [],
    outputSchema: {},
    promptTemplate: '',
  },
  {
    id: 'analyze-engineering-decision',
    version: 'v1',
    executionMode: 'GENERIC',
    objective: 'Analyze decisions',
    supportedInsightTypes: [],
    constraints: [],
    outputSchema: {},
    promptTemplate: '',
  },
  {
    id: 'analyze-engineering-event',
    version: 'v1',
    executionMode: 'DEDICATED_ENGINEERING_EVENT',
    objective: 'Engineering events',
    supportedInsightTypes: [],
    constraints: [],
    outputSchema: {},
    promptTemplate: '',
  },
];

describe('ProjectAnalysesSection — objective mapping contract', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectAnalysesSection],
      providers: [
        provideRouter([]),
        {
          provide: AnalysisService,
          useValue: {
            getAnalysesByProject: vi.fn().mockReturnValue(of([analysis])),
            createAnalysis: vi.fn().mockReturnValue(of(analysis)),
            launchAnalysis: vi.fn().mockReturnValue(of({ analysisId: analysis.id })),
          },
        },
        {
          provide: IntentCatalogService,
          useValue: { getSupportedIntents: () => of(genericIntents) },
        },
        {
          provide: SourceService,
          useValue: { getSourcesByProject: vi.fn().mockReturnValue(of([])) },
        },
      ],
    }).compileComponents();
  });

  it('RED: maps 4 generic intents to objectives via versioned key', async () => {
    const fixture = TestBed.createComponent(ProjectAnalysesSection);
    fixture.componentInstance.projectId = 'project-id';
    fixture.componentInstance.showForm = true;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('select');
    expect(select).not.toBeNull();
    const options = Array.from(select.querySelectorAll('option')) as Element[];
    const labels = options.map((o) => o.textContent?.trim());
    expect(labels).toContain('Understand this project');
    expect(labels).toContain('Prepare README information');
    expect(labels).toContain('Review the architecture');
    expect(labels).toContain('Analyze engineering decisions');
  });
});
