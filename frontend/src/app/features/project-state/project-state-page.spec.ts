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
  recentKnowledge: { recentKnowledge: [] },
  recentEvolution: { recentEvolution: [] },
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

  it('renders the recent knowledge section', async () => {
    const state: ProjectState = {
      ...emptyState,
      recentKnowledge: {
        recentKnowledge: [
          { id: 'k1', type: 'ARCHITECTURE', title: 'Adopted hexagonal layout', createdAt: null },
        ],
      },
    };
    getProject.mockReturnValue(of(project));
    getProjectState.mockReturnValue(of(state));
    const element = await render();

    const section = element.querySelector('#section-knowledge')?.parentElement;
    expect(section?.textContent).toContain('What have we learned recently?');
    expect(section?.textContent).toContain('ARCHITECTURE');
    expect(section?.textContent).toContain('Adopted hexagonal layout');
  });

  it('renders the recent evolution section with commit range', async () => {
    const state: ProjectState = {
      ...emptyState,
      recentEvolution: {
        recentEvolution: [
          {
            id: 'e1',
            category: 'BUG_RESOLUTION',
            title: 'Fixed N+1 in projection',
            baseCommit: '92d3f1eabcd123456789',
            targetCommit: '7ac09b2cdef987654321',
            occurredAt: '2026-08-11T12:00:00Z',
          },
        ],
      },
    };
    getProject.mockReturnValue(of(project));
    getProjectState.mockReturnValue(of(state));
    const element = await render();

    const section = element.querySelector('#section-evolution')?.parentElement;
    expect(section?.textContent).toContain('What recently changed?');
    expect(section?.textContent).toContain('BUG_RESOLUTION');
    expect(section?.textContent).toContain('Fixed N+1 in projection');
    expect(section?.textContent).toContain('92d3f1e');
    expect(section?.textContent).toContain('7ac09b2');
  });

  it('renders empty states for the new sections when empty', async () => {
    getProject.mockReturnValue(of(project));
    getProjectState.mockReturnValue(of(emptyState));
    const element = await render();

    const knowledge = element.querySelector('#section-knowledge')?.parentElement;
    const evolution = element.querySelector('#section-evolution')?.parentElement;
    expect(knowledge?.textContent).toContain('No recent knowledge.');
    expect(evolution?.textContent).toContain('No recent evolution.');
  });

  it('omits sparse proposed proposals that have no meaningful display content', async () => {
    const state: ProjectState = {
      ...emptyState,
      activeWork: {
        ...emptyState.activeWork,
        proposedProposals: [
          {
            id: 'p1',
            type: 'INSIGHT',
            insightType: null,
            title: null,
            summary: null,
            status: 'PROPOSED',
            confidence: 1,
          },
        ],
      },
    };
    getProject.mockReturnValue(of(project));
    getProjectState.mockReturnValue(of(state));
    const element = await render();

    const section = element.querySelector('#section-active')?.parentElement;
    expect(section?.textContent).toContain('No active work.');
    expect(section?.textContent).not.toContain('Proposed proposals');
    expect(section?.textContent).not.toContain('1% confidence');
  });

  it('keeps useful proposal confidence when proposal detail is usable', async () => {
    const state: ProjectState = {
      ...emptyState,
      pendingActions: {
        ...emptyState.pendingActions,
        proposedProposals: [
          {
            id: 'p1',
            type: 'INSIGHT',
            insightType: 'ARCHITECTURE_CHANGE',
            title: 'Normalize project state proposal summaries',
            summary: 'Use proposal titles in the overview.',
            status: 'PROPOSED',
            confidence: 0.72,
          },
        ],
      },
    };
    getProject.mockReturnValue(of(project));
    getProjectState.mockReturnValue(of(state));
    const element = await render();

    const section = element.querySelector('#section-actions')?.parentElement;
    expect(section?.textContent).toContain('Normalize project state proposal summaries');
    expect(section?.textContent).toContain('ARCHITECTURE CHANGE');
    expect(section?.textContent).toContain('72% confidence');
  });

  it('shows pending actions empty state when only sparse proposals are returned', async () => {
    const state: ProjectState = {
      ...emptyState,
      pendingActions: {
        ...emptyState.pendingActions,
        proposedProposals: [
          {
            id: 'p1',
            type: 'INSIGHT',
            insightType: '   ',
            title: '   ',
            summary: null,
            status: 'PROPOSED',
            confidence: 0.24,
          },
        ],
      },
    };
    getProject.mockReturnValue(of(project));
    getProjectState.mockReturnValue(of(state));
    const element = await render();

    const section = element.querySelector('#section-actions')?.parentElement;
    expect(section?.textContent).toContain('No pending actions.');
    expect(section?.textContent).not.toContain('Proposed proposals');
    expect(section?.textContent).not.toContain('24% confidence');
  });

  it('does not implement an imperative subscription', () => {
    expect(ProjectStatePage.toString()).not.toContain('.subscribe(');
  });
});
