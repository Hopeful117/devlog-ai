import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { ProjectWorkspaceLayout } from './project-workspace-layout';
import { ProjectService } from '../projects/project.service';

const project = { id: 'p1', name: 'DevLog AI', slug: 'devlog-ai', description: null };

describe('ProjectWorkspaceLayout', () => {
  const paramMap = new BehaviorSubject(convertToParamMap({ id: 'devlog-ai' }));
  const getProject = vi.fn();

  beforeEach(() => {
    paramMap.next(convertToParamMap({ id: 'devlog-ai' }));
    getProject.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    await TestBed.configureTestingModule({
      imports: [ProjectWorkspaceLayout],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap } },
        { provide: ProjectService, useValue: { getProject } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectWorkspaceLayout);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('reads the route identifier and renders the project name', async () => {
    getProject.mockReturnValue(of(project));
    const element = await render();

    expect(getProject).toHaveBeenCalledWith('devlog-ai');
    expect(element.textContent).toContain('DevLog AI');
  });

  it('falls back to "Workspace" when the project cannot be loaded', async () => {
    getProject.mockReturnValue(throwError(() => new Error('boom')));
    const element = await render();

    expect(element.textContent).toContain('Workspace');
  });

  it('renders the sidebar navigation links', async () => {
    getProject.mockReturnValue(of(project));
    const element = await render();
    const nav = element.querySelector('nav[aria-label="Project navigation"]');

    expect(nav?.textContent).toContain('Cockpit');
    expect(nav?.textContent).toContain('Overview');
    expect(nav?.textContent).toContain('Activity');
    expect(nav?.textContent).toContain('Knowledge');
    expect(nav?.textContent).toContain('Documentation');
    expect(nav?.textContent).toContain('Settings');
  });

  it('does not implement an imperative subscription', () => {
    expect(ProjectWorkspaceLayout.toString()).not.toContain('.subscribe(');
  });
});
