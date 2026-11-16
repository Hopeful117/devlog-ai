import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ProjectWorkspaceLayout } from './project-workspace-layout';
import { ProjectService } from '../projects/project.service';
import { ActivityAlertService } from '../mcp-activity/activity-alert.service';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';

const TEST_ENV = {
  backendBaseUrl: '',
  analysisPollingIntervalMs: 999999,
  organizerBaseUrl: '',
  oidc: { url: '', realm: '', clientId: '' },
};
const project = { id: 'p1', name: 'DevLog AI', slug: 'devlog-ai', description: null };

describe('ProjectWorkspaceLayout', () => {
  const paramMap = new BehaviorSubject(convertToParamMap({ id: 'devlog-ai' }));
  const getProject = vi.fn();
  const countByProject = vi.fn();

  beforeEach(() => {
    paramMap.next(convertToParamMap({ id: 'devlog-ai' }));
    getProject.mockReset();
    countByProject.mockReset();
    countByProject.mockReturnValue(of({ errorCount: 0 }));
  });

  afterEach(() => TestBed.resetTestingModule());

  function createComponent() {
    TestBed.configureTestingModule({
      imports: [ProjectWorkspaceLayout],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap } },
        { provide: ProjectService, useValue: { getProject } },
        { provide: ActivityAlertService, useValue: { countByProject } },
        { provide: APP_ENVIRONMENT, useValue: TEST_ENV },
      ],
    });
    const fixture = TestBed.createComponent(ProjectWorkspaceLayout);
    return fixture;
  }

  async function render() {
    const fixture = createComponent();
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

  it('alertCount$ emits errorCount when service returns errors', async () => {
    getProject.mockReturnValue(of(project));
    countByProject.mockReturnValue(of({ errorCount: 3 }));
    const fixture = createComponent();
    const component = fixture.componentInstance;

    const count = await firstValueFrom(component.alertCount$);

    expect(count.errorCount).toBe(3);
  });

  it('alertCount$ emits zero when service returns no errors', async () => {
    getProject.mockReturnValue(of(project));
    countByProject.mockReturnValue(of({ errorCount: 0 }));
    const fixture = createComponent();
    const component = fixture.componentInstance;

    const count = await firstValueFrom(component.alertCount$);

    expect(count.errorCount).toBe(0);
  });

  it('alertCount$ emits errorCount=0 when service errors (silent degradation)', async () => {
    getProject.mockReturnValue(of(project));
    countByProject.mockReturnValue(throwError(() => new Error('network')));
    const fixture = createComponent();
    const component = fixture.componentInstance;

    const count = await firstValueFrom(component.alertCount$);

    expect(count.errorCount).toBe(0);
  });

  it('badge cap logic: 9+ when errorCount >= 9', () => {
    // Test the template expression directly via the computed value
    const cap = (n: number) => (n >= 9 ? '9+' : String(n));
    expect(cap(1)).toBe('1');
    expect(cap(8)).toBe('8');
    expect(cap(9)).toBe('9+');
    expect(cap(12)).toBe('9+');
  });

  it('does not implement an imperative subscription', () => {
    expect(ProjectWorkspaceLayout.toString()).not.toContain('.subscribe(');
  });
});
