import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProjectEngineeringEventsSection } from './project-engineering-events-section';
import { EngineeringEventService } from './engineering-event.service';
import { EngineeringEventPage } from './engineering-event.models';

const sources = [
  { id: 's1', name: 'Main', active: true },
  { id: 's2', name: 'Inactive', active: false },
];

function page(items: unknown[]): EngineeringEventPage {
  return {
    version: 'v1',
    items: items as never,
    page: 0,
    size: 5,
    totalElements: items.length,
    totalPages: 1,
    hasPrevious: false,
    hasNext: false,
  };
}

describe('ProjectEngineeringEventsSection', () => {
  const byProject = vi.fn();
  const execute = vi.fn();

  beforeEach(() => {
    byProject.mockReset();
    execute.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    await TestBed.configureTestingModule({
      imports: [ProjectEngineeringEventsSection],
      providers: [
        provideRouter([]),
        { provide: EngineeringEventService, useValue: { byProject, execute } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectEngineeringEventsSection);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('sources', sources);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('loads and renders the latest validated events', async () => {
    byProject.mockReturnValue(
      of(
        page([
          {
            id: 'e1',
            title: 'Introduced caching',
            category: 'TECHNOLOGY_CHANGE',
            occurredAt: '2026-08-01T10:00:00Z',
          },
        ]),
      ),
    );
    const fixture = await render();

    expect(byProject).toHaveBeenCalledWith('p1', 0, 5);
    expect(fixture.nativeElement.textContent).toContain('Introduced caching');
  });

  it('defaults the source selection to the first active source', async () => {
    byProject.mockReturnValue(of(page([])));
    const fixture = await render();

    expect(fixture.componentInstance.form.controls.sourceId.value).toBe('s1');
  });

  it('renders an error state when events fail to load', async () => {
    byProject.mockReturnValue(throwError(() => new Error('boom')));
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('does not execute when the form is invalid', async () => {
    byProject.mockReturnValue(of(page([])));
    const fixture = await render();

    fixture.componentInstance.form.controls.targetCommit.setValue('');
    fixture.componentInstance.execute();

    expect(execute).not.toHaveBeenCalled();
    expect(fixture.componentInstance.form.touched).toBe(true);
  });

  it('executes with a lowercased commit and shows the success outcome', async () => {
    byProject.mockReturnValue(of(page([])));
    execute.mockReturnValue(
      of({
        outcome: 'CREATED',
        analysisId: 'a1',
        baseCommit: 'ABCDEF1234567890',
        targetCommit: '1234567890ABCDEF',
      }),
    );
    const fixture = await render();
    fixture.componentInstance.execution$.subscribe();
    fixture.componentInstance.form.controls.sourceId.setValue('s2');
    fixture.componentInstance.form.controls.targetCommit.setValue('A'.repeat(40));
    fixture.componentInstance.execute();
    fixture.detectChanges();

    expect(execute).toHaveBeenCalledWith('p1', 's2', 'a'.repeat(40));
    expect(fixture.nativeElement.textContent).toContain('CREATED');
  });

  it('renders an error message when execution fails', async () => {
    byProject.mockReturnValue(of(page([])));
    execute.mockReturnValue(throwError(() => new Error('analysis down')));
    const fixture = await render();
    fixture.componentInstance.execution$.subscribe();
    fixture.componentInstance.form.controls.targetCommit.setValue('a'.repeat(40));
    fixture.componentInstance.execute();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('does not implement an imperative subscription', () => {
    expect(ProjectEngineeringEventsSection.toString()).not.toContain('.subscribe(');
  });
});
