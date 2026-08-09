import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { ProjectFreshnessService } from './project-freshness.service';

describe('ProjectFreshnessService', () => {
  let service: ProjectFreshnessService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: 'http://core.test' } },
      ],
    });
    service = TestBed.inject(ProjectFreshnessService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the last explicit check without triggering a check', () => {
    service.getLatest('project/id', 'source-id').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === 'http://core.test/api/v1/projects/project%2Fid/freshness-checks/latest' &&
        candidate.params.get('sourceId') === 'source-id',
    );
    expect(request.request.method).toBe('GET');
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('checks freshness through the exact individual Source payload', () => {
    service.check('project-id', 'source-id').subscribe();
    const request = http.expectOne('http://core.test/api/v1/projects/project-id/freshness-checks');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ sourceId: 'source-id' });
    request.flush({});
  });
});
