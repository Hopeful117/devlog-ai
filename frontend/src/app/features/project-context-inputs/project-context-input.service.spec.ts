import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import {
  CreateProjectHumanContextInputRequest,
  ProjectHumanContextInput,
} from './project-context-input.models';
import { ProjectContextInputService } from './project-context-input.service';

const projectId = 'a1ee6d55-e034-491a-a6e6-cdad70573b24';
const input: ProjectHumanContextInput = {
  id: '0bc4252e-bd52-4a98-8337-622f81c4d4fa',
  projectId,
  title: 'Medium-term goal',
  contentMarkdown: 'Improve semantic usefulness for humans and agents.',
  type: 'GOAL',
  status: 'ACTIVE',
  createdAt: '2026-08-13T10:00:00Z',
  updatedAt: '2026-08-13T10:00:00Z',
};

describe('ProjectContextInputService', () => {
  let service: ProjectContextInputService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: 'http://core.test' } },
      ],
    });
    service = TestBed.inject(ProjectContextInputService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests project context inputs by project id', () => {
    let result: readonly ProjectHumanContextInput[] | undefined;
    service.getByProject(projectId).subscribe((inputs) => (result = inputs));

    const request = http.expectOne(`http://core.test/api/v1/projects/${projectId}/context-inputs`);
    expect(request.request.method).toBe('GET');
    request.flush([input]);
    expect(result).toEqual([input]);
  });

  it('creates a project context input', () => {
    const body: CreateProjectHumanContextInputRequest = {
      title: input.title,
      contentMarkdown: input.contentMarkdown,
      type: input.type,
    };

    service.create(projectId, body).subscribe();

    const request = http.expectOne(`http://core.test/api/v1/projects/${projectId}/context-inputs`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(input);
  });

  it('archives a project context input', () => {
    service.archive(projectId, input.id).subscribe();

    const request = http.expectOne(
      `http://core.test/api/v1/projects/${projectId}/context-inputs/${input.id}/archive`,
    );
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({});
    request.flush({ ...input, status: 'ARCHIVED' });
  });
});
