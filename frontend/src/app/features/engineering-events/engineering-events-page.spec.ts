import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { EngineeringEventsPage } from './engineering-events-page';
import { EngineeringEventService } from './engineering-event.service';
import { EngineeringEventPage } from './engineering-event.models';
import { ProjectService } from '../projects/project.service';

const project = { id: 'p1', name: 'DevLog AI', slug: 'devlog-ai', description: null };

function page(items: unknown[]): EngineeringEventPage {
  return {
    version: 'v1',
    items: items as never,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: 1,
    hasPrevious: false,
    hasNext: false,
  };
}

describe('EngineeringEventsPage', () => {
  const parentParamMap = new BehaviorSubject(convertToParamMap({ id: 'devlog-ai' }));
  const getProject = vi.fn();
  const byProject = vi.fn();

  beforeEach(() => {
    parentParamMap.next(convertToParamMap({ id: 'devlog-ai' }));
    getProject.mockReset();
    byProject.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    await TestBed.configureTestingModule({
      imports: [EngineeringEventsPage],
      providers: [
        { provide: ActivatedRoute, useValue: { parent: { paramMap: parentParamMap } } },
        { provide: ProjectService, useValue: { getProject } },
        { provide: EngineeringEventService, useValue: { byProject } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(EngineeringEventsPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('loads the parent project and renders the event list', async () => {
    getProject.mockReturnValue(of(project));
    byProject.mockReturnValue(
      of(
        page([
          {
            id: 'e1',
            title: 'Introduced caching',
            summary: 'Redis cache',
            category: 'TECHNOLOGY_CHANGE',
            occurredAt: '2026-08-01T10:00:00Z',
          },
          {
            id: 'e2',
            title: 'Fixed login',
            summary: 'Auth fix',
            category: 'BUG_RESOLUTION',
            occurredAt: '2026-08-02T10:00:00Z',
          },
        ]),
      ),
    );
    const element = await render();

    expect(getProject).toHaveBeenCalledWith('devlog-ai');
    expect(byProject).toHaveBeenCalledWith('p1');
    expect(element.textContent).toContain('Introduced caching');
    expect(element.textContent).toContain('Fixed login');
    expect(element.textContent).toContain('technology change'.toUpperCase());
  });

  it('renders the empty state when no events exist', async () => {
    getProject.mockReturnValue(of(project));
    byProject.mockReturnValue(of(page([])));
    const element = await render();

    expect(element.textContent).toContain('No validated Engineering Event.');
  });

  it('renders an error state when the event fetch fails', async () => {
    getProject.mockReturnValue(of(project));
    byProject.mockReturnValue(throwError(() => new Error('boom')));
    const element = await render();

    expect(element.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('does not implement an imperative subscription', () => {
    expect(EngineeringEventsPage.toString()).not.toContain('.subscribe(');
  });
});
