import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProjectSummary } from './project.models';
import { ProjectService } from './project.service';
import { ProjectsPage } from './projects-page';

const project: ProjectSummary = {
  id: 'a1ee6d55-e034-491a-a6e6-cdad70573b24',
  name: 'DevLog AI',
  slug: 'devlog-ai',
  description: 'Architecture knowledge platform',
  status: 'ACTIVE',
  createdAt: '2026-07-20T10:00:00Z',
  updatedAt: '2026-07-22T12:00:00Z',
};

async function render(projects$: ReturnType<ProjectService['getProjects']>) {
  await TestBed.configureTestingModule({
    imports: [ProjectsPage],
    providers: [
      provideRouter([]),
      { provide: ProjectService, useValue: { getProjects: () => projects$ } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(ProjectsPage);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('ProjectsPage', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('renders projects returned by the service', async () => {
    const element = await render(of([project]));

    expect(element.textContent).toContain('DevLog AI');
    expect(element.textContent).toContain('ACTIVE');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/projects/devlog-ai');
  });

  it('renders the empty state', async () => {
    const element = await render(of([]));

    expect(element.querySelector('[data-testid="projects-empty"]')).toBeTruthy();
    expect(element.textContent).toContain('No projects yet');
  });

  it('renders an unavailable-backend error state', async () => {
    const response = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });
    const element = await render(throwError(() => response));

    expect(element.querySelector('[role="alert"]')).toBeTruthy();
    expect(element.textContent).toContain('Java Core is unavailable');
  });

  it('does not implement an imperative subscription', () => {
    expect(ProjectsPage.toString()).not.toContain('.subscribe(');
  });
});
