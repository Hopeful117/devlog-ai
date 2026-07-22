import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { ProjectDetail } from './project.models';
import { ProjectDetailPage } from './project-detail-page';
import { ProjectService } from './project.service';
import { SourceService } from './source.service';
import { AnalysisService } from '../analyses/analysis.service';
import { IntentCatalogService } from '../analyses/intent-catalog.service';
import { DeliverableService } from '../deliverables/deliverable.service';

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

  beforeEach(() => {
    paramMap.next(convertToParamMap({ id: 'devlog-ai' }));
    getProject.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    await TestBed.configureTestingModule({
      imports: [ProjectDetailPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap } },
        { provide: ProjectService, useValue: { getProject } },
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
        {
          provide: DeliverableService,
          useValue: { getByProject: () => of([]), generate: vi.fn() },
        },
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
    expect(element.textContent).toContain(project.id);
    expect(element.textContent).toContain('Sources');
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
});
