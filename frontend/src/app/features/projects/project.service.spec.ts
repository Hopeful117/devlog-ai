import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ProjectDetail, ProjectSummary } from './project.models';
import { ProjectService } from './project.service';

const summary: ProjectSummary = {
  id: 'a1ee6d55-e034-491a-a6e6-cdad70573b24',
  name: 'DevLog AI',
  slug: 'devlog-ai',
  description: 'Architecture knowledge platform',
  status: 'ACTIVE',
  createdAt: '2026-07-20T10:00:00Z',
  updatedAt: '2026-07-22T12:00:00Z',
};

describe('ProjectService', () => {
  let service: ProjectService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: 'http://core.test' } },
      ],
    });
    service = TestBed.inject(ProjectService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests all projects', () => {
    let result: readonly ProjectSummary[] | undefined;
    service.getProjects().subscribe((projects) => (result = projects));

    const request = http.expectOne('http://core.test/api/v1/projects');
    expect(request.request.method).toBe('GET');
    request.flush([summary]);

    expect(result).toEqual([summary]);
  });

  it('requests one project by the backend slug identifier', () => {
    let result: ProjectDetail | undefined;
    service.getProject('devlog ai').subscribe((project) => (result = project));

    const request = http.expectOne('http://core.test/api/v1/projects/devlog%20ai');
    expect(request.request.method).toBe('GET');
    request.flush(summary);

    expect(result).toEqual(summary);
  });
});
