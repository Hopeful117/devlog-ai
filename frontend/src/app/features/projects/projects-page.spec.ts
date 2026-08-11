import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
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

async function render(
  projects$: ReturnType<ProjectService['getProjects']>,
  createProject = vi.fn(),
) {
  await TestBed.configureTestingModule({
    imports: [ProjectsPage],
    providers: [
      provideRouter([]),
      {
        provide: ProjectService,
        useValue: { getProjects: () => projects$, createProject },
      },
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

  it('creates a trimmed project and navigates to its workspace', async () => {
    const created = { ...project, name: 'New Project', slug: 'new-project' };
    const createProject = vi.fn().mockReturnValue(of(created));
    await render(of([]), createProject);
    const fixture = TestBed.createComponent(ProjectsPage);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.creationState$.subscribe();
    fixture.componentInstance.form.setValue({
      name: '  New Project  ',
      description: '  A fresh project  ',
    });

    fixture.componentInstance.createProject();

    expect(createProject).toHaveBeenCalledWith({
      name: 'New Project',
      description: 'A fresh project',
    });
    expect(navigate).toHaveBeenCalledWith(['/projects', 'new-project']);
  });

  it('does not call the service when the name is blank', async () => {
    const createProject = vi.fn();
    await render(of([]), createProject);
    const fixture = TestBed.createComponent(ProjectsPage);
    fixture.componentInstance.creationState$.subscribe();
    fixture.componentInstance.form.setValue({ name: '   ', description: '' });

    fixture.componentInstance.createProject();

    expect(createProject).not.toHaveBeenCalled();
    expect(fixture.componentInstance.form.controls.name.hasError('required')).toBe(true);
  });

  it('surfaces a creation error and stays idle', async () => {
    const createProject = vi.fn().mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
    );
    await render(of([]), createProject);
    const fixture = TestBed.createComponent(ProjectsPage);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    let errorState: unknown;
    fixture.componentInstance.creationState$.subscribe((s) => (errorState = s));
    fixture.componentInstance.form.setValue({ name: 'New Project', description: '' });

    fixture.componentInstance.createProject();

    expect(createProject).toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    expect(errorState).toMatchObject({ state: 'error' });
  });
});
