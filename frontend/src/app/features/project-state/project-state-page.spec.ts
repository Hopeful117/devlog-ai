import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { ProjectStatePage } from './project-state-page';
import { ProjectStateService } from './project-state.service';
import { ProjectService } from '../projects/project.service';
import { ProjectState } from './project-state.models';

const emptyState: ProjectState = {
  projectId: 'p1',
  projectName: 'DevLog AI',
  objective: { description: null, currentMilestone: null, activeStory: null, openChallenges: [] },
  activeWork: { inProgressStories: [], openChallenges: [], proposedProposals: [] },
  recentChanges: { completedStories: [], recentDecisions: [], recentCommits: [] },
  roadmapProgress: { plannedMilestones: [], registeredStories: [] },
  pendingActions: { proposedProposals: [], openChallenges: [], unstartedStories: [] },
};

const project = { id: 'p1', name: 'DevLog AI', description: 'Docs platform' };

describe('ProjectStatePage', () => {
  const paramMap = new BehaviorSubject(convertToParamMap({ id: 'devlog-ai' }));
  const getProject = vi.fn();
  const getProjectState = vi.fn();

  beforeEach(() => {
    paramMap.next(convertToParamMap({ id: 'devlog-ai' }));
    getProject.mockReset();
    getProjectState.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    await TestBed.configureTestingModule({
      imports: [ProjectStatePage],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap } },
        { provide: ProjectService, useValue: { getProject } },
        { provide: ProjectStateService, useValue: { getProjectState } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectStatePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('loads the route identifier and renders the project name', async () => {
    getProject.mockReturnValue(of(project));
    getProjectState.mockReturnValue(of(emptyState));
    const element = await render();

    expect(getProject).toHaveBeenCalledWith('devlog-ai');
    expect(element.querySelector('#overview-title')?.textContent).toContain('DevLog AI');
  });

  it('renders story numbers and no null placeholders', async () => {
    const state: ProjectState = {
      ...emptyState,
      objective: {
        ...emptyState.objective,
        activeStory: { id: 's1', number: 42, title: 'Overview guard', status: 'IN_PROGRESS' },
      },
    };
    getProject.mockReturnValue(of(project));
    getProjectState.mockReturnValue(of(state));
    const element = await render();

    expect(element.textContent).toContain('#42');
    expect(element.textContent).toContain('Overview guard');
    expect(element.textContent).not.toContain('null #');
  });

  it('renders the not-found state for a 404', async () => {
    getProject.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
    );
    const element = await render();

    expect(element.querySelector('[role="alert"]')).toBeTruthy();
    expect(element.textContent).toContain('Project not found');
  });

  it('renders the error state on a non-404 project failure', async () => {
    getProject.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
    );
    const element = await render();

    expect(element.textContent).toContain('Project state unavailable');
  });

  it('renders the error state when the projection fetch fails after the project loads', async () => {
    getProject.mockReturnValue(of(project));
    getProjectState.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
    );
    const element = await render();

    expect(element.textContent).toContain('Project state unavailable');
  });

  it('does not implement an imperative subscription', () => {
    expect(ProjectStatePage.toString()).not.toContain('.subscribe(');
  });
});