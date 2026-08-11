import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { TimelineResponse } from './timeline.models';
import { TimelineService } from './timeline.service';

const response: TimelineResponse = {
  projectId: 'a1ee6d55-e034-491a-a6e6-cdad70573b24',
  projectName: 'devlog-ai',
  entries: [],
};

describe('TimelineService', () => {
  let service: TimelineService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: 'http://core.test' } },
      ],
    });
    service = TestBed.inject(TimelineService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the timeline for the project UUID', () => {
    const projectId = 'a1ee6d55-e034-491a-a6e6-cdad70573b24';
    let result: TimelineResponse | undefined;

    service.getTimeline(projectId).subscribe((value) => (result = value));

    const request = http.expectOne(`http://core.test/api/v1/projects/${projectId}/timeline`);
    expect(request.request.method).toBe('GET');
    request.flush(response);
    expect(result).toEqual(response);
  });

  it('encodes the project identifier in the URL', () => {
    service.getTimeline('proj with spaces').subscribe();

    const request = http.expectOne(
      'http://core.test/api/v1/projects/proj%20with%20spaces/timeline',
    );
    expect(request.request.method).toBe('GET');
    request.flush(response);
  });
});
