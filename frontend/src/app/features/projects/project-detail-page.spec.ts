import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';

import { ProjectDetail } from './project.models';
import { ProjectDetailPage } from './project-detail-page';
import { ProjectService } from './project.service';
import { SourceService } from './source.service';
import { AnalysisService } from '../analyses/analysis.service';
import { IntentCatalogService } from '../analyses/intent-catalog.service';
import { DeliverableService } from '../deliverables/deliverable.service';
import { InsightService } from '../insights/insight.service';
import { ProjectUnderstandingService } from './project-understanding.service';

const project: ProjectDetail = {
  id: 'a1ee6d55-e034-491a-a6e6-cdad70573b24',
  name: 'DevLog AI',
  slug: 'devlog-ai',
  description: 'Architecture knowledge platform',
  status: 'ACTIVE',
  createdAt: '2026-07-20T10:00:00Z',
  updatedAt: '2026-07-22T12:00:00Z',
};

describe('ProjectDetailPage', () => {
  const paramMap = new BehaviorSubject(convertToParamMap({ id: 'devlog-ai' }));
  const getProject = vi.fn();
  const updateProject = vi.fn();
  const deleteProject = vi.fn();

  beforeEach(() => {
    paramMap.next(convertToParamMap({ id: 'devlog-ai' }));
    getProject.mockReset();
    updateProject.mockReset();
    deleteProject.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    await TestBed.configureTestingModule({
      imports: [ProjectDetailPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap } },
        { provide: ProjectService, useValue: { getProject, updateProject, deleteProject } },
        {
          provide: SourceService,
          useValue: {
            getSourcesByProject: () => of([]),
            createSource: vi.fn(),
            setSourceActive: vi.fn(),
          },
        },
        {
          provide: AnalysisService,
          useValue: { getAnalysesByProject: () => of([]) },
        },
        { provide: IntentCatalogService, useValue: { getSupportedIntents: () => of([]) } },
        { provide: ProjectUnderstandingService, useValue: { execute: vi.fn() } },
        {
          provide: DeliverableService,
          useValue: { getByProject: () => of([]), generate: vi.fn() },
        },
        { provide: InsightService, useValue: { getInsightsByProject: () => of([]) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectDetailPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('loads the route identifier and renders the project', async () => {
    getProject.mockReturnValue(of(project));
    const element = await render();

    expect(getProject).toHaveBeenCalledWith('devlog-ai');
    expect(element.querySelector('h1')?.textContent).toContain('DevLog AI');
    expect(element.textContent).toContain('Project health');
    expect(element.textContent).toContain('Repository health');
  });

  it('renders the not-found state for a 404', async () => {
    getProject.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
    );
    const element = await render();

    expect(element.querySelector('[role="alert"]')).toBeTruthy();
    expect(element.textContent).toContain('Project not found');
  });

  it('does not implement an imperative subscription', () => {
    expect(ProjectDetailPage.toString()).not.toContain('.subscribe(');
  });

  it('pre-populates and submits the edit form with normalized values', async () => {
    getProject.mockReturnValue(of(project));
    updateProject.mockReturnValue(of({ ...project, name: 'DevLog Updated' }));
    const element = await render();
    const fixture = TestBed.createComponent(ProjectDetailPage);
    fixture.componentInstance.beginEdit(project);
    fixture.componentInstance.editForm.setValue({
      name: '  DevLog Updated  ',
      description: '  Updated description  ',
    });
    fixture.componentInstance.updateState$.subscribe();
    fixture.componentInstance.updateProject(project);

    expect(updateProject).toHaveBeenCalledWith('devlog-ai', {
      name: 'DevLog Updated',
      description: 'Updated description',
    });
    expect(element.textContent).toContain('Edit project');
  });

  it('requires the exact project name before deletion', async () => {
    getProject.mockReturnValue(of(project));
    await render();
    const fixture = TestBed.createComponent(ProjectDetailPage);
    fixture.componentInstance.deleteState$.subscribe();
    fixture.componentInstance.beginDelete();
    fixture.componentInstance.deleteForm.controls.confirmation.setValue('Wrong name');

    fixture.componentInstance.deleteProject(project);

    expect(deleteProject).not.toHaveBeenCalled();
    expect(
      fixture.componentInstance.deleteForm.controls.confirmation.hasError('projectNameMismatch'),
    ).toBe(true);
  });

  it('navigates only after successful deletion', async () => {
    getProject.mockReturnValue(of(project));
    deleteProject.mockReturnValue(of(undefined));
    await render();
    const fixture = TestBed.createComponent(ProjectDetailPage);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.deleteState$.subscribe();
    fixture.componentInstance.deleteForm.controls.confirmation.setValue(project.name);

    fixture.componentInstance.deleteProject(project);

    expect(deleteProject).toHaveBeenCalledWith('devlog-ai');
    expect(navigate).toHaveBeenCalledWith(['/projects']);
  });

  it('keeps the page on deletion failure', async () => {
    getProject.mockReturnValue(of(project));
    const failure = new Subject<void>();
    deleteProject.mockReturnValue(failure);
    await render();
    const fixture = TestBed.createComponent(ProjectDetailPage);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.deleteState$.subscribe();
    fixture.componentInstance.deleteForm.controls.confirmation.setValue(project.name);

    fixture.componentInstance.deleteProject(project);
    failure.error(new HttpErrorResponse({ status: 500 }));

    expect(navigate).not.toHaveBeenCalled();
  });
});
