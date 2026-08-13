import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ProjectState } from './project-state.models';
import { ProjectStateService } from './project-state.service';

const state: ProjectState = {
  projectId: 'a1ee6d55-e034-491a-a6e6-cdad70573b24',
  projectName: 'devlog-ai',
  objective: {
    description: null,
    currentMilestone: null,
    activeStory: null,
    openChallenges: [],
    humanContextInputs: [],
  },
  activeWork: { inProgressStories: [], openChallenges: [], proposedProposals: [] },
  recentChanges: { completedStories: [], recentDecisions: [], recentCommits: [] },
  roadmapProgress: { plannedMilestones: [], registeredStories: [] },
  pendingActions: { proposedProposals: [], openChallenges: [], unstartedStories: [] },
  recentKnowledge: { recentKnowledge: [] },
  recentEvolution: { recentEvolution: [] },
};

describe('ProjectStateService', () => {
  let service: ProjectStateService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: 'http://core.test' } },
      ],
    });
    service = TestBed.inject(ProjectStateService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the project state for the project UUID', () => {
    const projectId = 'a1ee6d55-e034-491a-a6e6-cdad70573b24';
    let result: ProjectState | undefined;

    service.getProjectState(projectId).subscribe((value) => (result = value));

    const request = http.expectOne(`http://core.test/api/v1/projects/${projectId}/state`);
    expect(request.request.method).toBe('GET');
    request.flush(state);
    expect(result).toEqual(state);
  });

  it('encodes the project identifier in the URL', () => {
    service.getProjectState('proj with spaces').subscribe();

    const request = http.expectOne('http://core.test/api/v1/projects/proj%20with%20spaces/state');
    expect(request.request.method).toBe('GET');
    request.flush(state);
  });
});
