import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { ProjectService } from '../projects/project.service';
import { TimelinePage } from './timeline-page';
import { TimelineResponse } from './timeline.models';
import { TimelineService } from './timeline.service';

const project = { id: 'p1', name: 'DevLog AI', description: 'Docs platform' };

const emptyResponse: TimelineResponse = {
  projectId: 'p1',
  projectName: 'DevLog AI',
  entries: [],
};

const entry = {
  id: 'e1',
  type: 'KNOWLEDGE_EVENT' as const,
  timestamp: '2026-08-11T12:00:00Z',
  title: 'Adopted hexagonal layout',
  detail: 'ARCHITECTURE',
};

describe('TimelinePage', () => {
  const paramMap = new BehaviorSubject(convertToParamMap({ id: 'devlog-ai' }));
  const getProject = vi.fn();
  const getTimeline = vi.fn();

  beforeEach(() => {
    paramMap.next(convertToParamMap({ id: 'devlog-ai' }));
    getProject.mockReset();
    getTimeline.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    await TestBed.configureTestingModule({
      imports: [TimelinePage],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap } },
        { provide: ProjectService, useValue: { getProject } },
        { provide: TimelineService, useValue: { getTimeline } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TimelinePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('loads the route identifier and requests the timeline', async () => {
    getProject.mockReturnValue(of(project));
    getTimeline.mockReturnValue(of(emptyResponse));
    const element = await render();

    expect(getTimeline).toHaveBeenCalledWith('p1');
    expect(element.querySelector('#timeline-title')?.textContent).toContain('DevLog AI');
  });

  it('renders entries with type badge, title, detail and timestamp', async () => {
    getProject.mockReturnValue(of(project));
    getTimeline.mockReturnValue(of({ ...emptyResponse, entries: [entry] }));
    const element = await render();

    expect(element.textContent).toContain('KNOWLEDGE_EVENT');
    expect(element.textContent).toContain('Adopted hexagonal layout');
    expect(element.textContent).toContain('ARCHITECTURE');
    expect(element.textContent).toContain('2026-08-11T12:00:00Z');
  });

  it('renders an empty state when there are no entries', async () => {
    getProject.mockReturnValue(of(project));
    getTimeline.mockReturnValue(of(emptyResponse));
    const element = await render();

    expect(element.textContent).toContain('No timeline entries yet.');
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

    expect(element.textContent).toContain('Project timeline unavailable');
  });

  it('renders the error state when the timeline fetch fails after the project loads', async () => {
    getProject.mockReturnValue(of(project));
    getTimeline.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
    );
    const element = await render();

    expect(element.textContent).toContain('Project timeline unavailable');
  });

  it('does not implement an imperative subscription', () => {
    expect(TimelinePage.toString()).not.toContain('.subscribe(');
  });
});
