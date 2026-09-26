import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { StoryService } from './story.service';

describe('StoryService', () => {
  let service: StoryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: '' } },
      ],
    });
    service = TestBed.inject(StoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses encoded GET endpoints for the Story and its Change Briefing', () => {
    service.get('project/id', 'story id/1').subscribe();
    service.getChangeBriefing('project/slug', 'story id/1').subscribe();

    const story = http.expectOne('/api/v1/projects/project%2Fid/stories/story%20id%2F1');
    expect(story.request.method).toBe('GET');
    story.flush({});

    const briefing = http.expectOne(
      '/api/v1/projects/project%2Fslug/stories/story%20id%2F1/change-briefing',
    );
    expect(briefing.request.method).toBe('GET');
    briefing.flush({});
  });

  it('exposes no mutating HTTP operation', () => {
    expect(StoryService.prototype).not.toHaveProperty('create');
    expect(StoryService.prototype).not.toHaveProperty('update');
    expect(StoryService.prototype).not.toHaveProperty('delete');
  });
});
