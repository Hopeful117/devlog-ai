import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { EngineeringEventService } from './engineering-event.service';

describe('EngineeringEventService', () => {
  let service: EngineeringEventService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: 'http://core.test' } },
    ] });
    service = TestBed.inject(EngineeringEventService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('executes only through the explicit Source and target commit payload', () => {
    service.execute('project/id', 'source-id', 'a'.repeat(40)).subscribe();
    const request = http.expectOne(
      'http://core.test/api/v1/projects/project%2Fid/engineering-event-executions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ sourceId: 'source-id', targetCommit: 'a'.repeat(40) });
    request.flush({});
  });

  it('loads the paged Project history and immutable detail', () => {
    service.byProject('project-id', 1, 5).subscribe();
    service.get('event/id').subscribe();
    expect(http.expectOne(
      'http://core.test/api/v1/projects/project-id/engineering-events?page=1&size=5'
    ).request.method).toBe('GET');
    expect(http.expectOne(
      'http://core.test/api/v1/engineering-events/event%2Fid'
    ).request.method).toBe('GET');
  });
});
