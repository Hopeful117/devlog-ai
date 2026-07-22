import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { CreateSourceRequest, SourceDetail } from './source.models';
import { SourceService } from './source.service';

const source: SourceDetail = {
  id: '0bc4252e-bd52-4a98-8337-622f81c4d4fa',
  projectId: 'a1ee6d55-e034-491a-a6e6-cdad70573b24',
  type: 'GIT_REPOSITORY',
  name: 'core',
  repositoryUrl: 'https://example.test/core.git',
  defaultBranch: 'main',
  provider: 'GENERIC_GIT',
  active: true,
  lastSynchronizedAt: null,
  createdAt: '2026-07-20T10:00:00Z',
  updatedAt: '2026-07-22T12:00:00Z',
};

describe('SourceService', () => {
  let service: SourceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: 'http://core.test' } },
      ],
    });
    service = TestBed.inject(SourceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests Sources for the Project UUID', () => {
    let result: readonly SourceDetail[] | undefined;
    service.getSourcesByProject(source.projectId).subscribe((sources) => (result = sources));

    const request = http.expectOne(`http://core.test/api/v1/sources/project/${source.projectId}`);
    expect(request.request.method).toBe('GET');
    request.flush([source]);
    expect(result).toEqual([source]);
  });

  it('creates a Source using the backend request DTO', () => {
    const body: CreateSourceRequest = {
      projectId: source.projectId,
      type: 'GIT_REPOSITORY',
      name: source.name,
      repositoryUrl: source.repositoryUrl,
      defaultBranch: source.defaultBranch,
      provider: source.provider,
    };
    let result: SourceDetail | undefined;
    service.createSource(body).subscribe((created) => (result = created));

    const request = http.expectOne('http://core.test/api/v1/sources');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(source);
    expect(result).toEqual(source);
  });

  it('updates Source activation with the boolean request DTO', () => {
    service.setSourceActive(source.id, false).subscribe();

    const request = http.expectOne(`http://core.test/api/v1/sources/${source.id}/activation`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ active: false });
    request.flush({ ...source, active: false });
  });
});
