import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject } from 'rxjs';

import { AnalysisSummary } from '../analyses/analysis.models';
import { SourceSummary } from './source.models';
import { ProjectUnderstandingSection } from './project-understanding-section';
import { ProjectUnderstandingResponse } from './project-understanding.models';
import { ProjectUnderstandingService } from './project-understanding.service';

const source: SourceSummary = {
  id: 'source-1',
  projectId: 'project-1',
  type: 'GIT_REPOSITORY',
  name: 'Core',
  repositoryUrl: 'https://example.test/core.git',
  defaultBranch: 'main',
  provider: 'GENERIC_GIT',
  active: true,
  lastSynchronizedAt: null,
  createdAt: '2026-08-09T00:00:00Z',
  updatedAt: '2026-08-09T00:00:00Z',
};

const prior: AnalysisSummary = {
  id: 'analysis-old',
  projectId: 'project-1',
  type: 'ARCHITECTURE_REVIEW',
  intentId: 'describe-project',
  intentVersion: 'v1',
  status: 'COMPLETED',
  startedAt: null,
  completedAt: null,
  createdAt: '2026-08-09T00:00:00Z',
  updatedAt: '2026-08-09T00:00:00Z',
  userGuidance: null,
};

describe('ProjectUnderstandingSection', () => {
  const execute = vi.fn();

  async function render(
    sources: readonly SourceSummary[],
    analyses: readonly AnalysisSummary[] = [],
  ) {
    await TestBed.configureTestingModule({
      imports: [ProjectUnderstandingSection],
      providers: [
        provideRouter([]),
        { provide: ProjectUnderstandingService, useValue: { execute } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectUnderstandingSection);
    fixture.componentRef.setInput('projectId', 'project-1');
    fixture.componentRef.setInput('sources', sources);
    fixture.componentRef.setInput('analyses', analyses);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => TestBed.resetTestingModule());

  beforeEach(() => execute.mockReset());

  it('shows unavailable guidance without an active Git Source', async () => {
    const fixture = await render([]);
    expect(fixture.nativeElement.textContent).toContain('Connect and activate a Git repository');
  });

  it('selects the only Source and uses the first-run label', async () => {
    const fixture = await render([source]);
    expect(fixture.componentInstance.form.controls.sourceId.value).toBe(source.id);
    expect(fixture.nativeElement.textContent).toContain('Understand project');
  });

  it('uses the refresh label only for a canonical earlier execution', async () => {
    const fixture = await render([source], [prior]);
    expect(fixture.nativeElement.textContent).toContain('Refresh understanding');
  });

  it('trims the revision, blocks repeated clicks, and navigates after creation', async () => {
    const pending = new Subject<ProjectUnderstandingResponse>();
    execute.mockReturnValue(pending);
    const fixture = await render([source]);
    fixture.componentInstance.state$.subscribe();
    fixture.componentInstance.form.controls.targetRevision.setValue('  feature/story  ');
    fixture.componentInstance.submit();
    fixture.componentInstance.submit();
    expect(execute).toHaveBeenCalledTimes(1);
    expect(execute).toHaveBeenCalledWith('project-1', {
      sourceId: 'source-1',
      targetRevision: 'feature/story',
    });
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    pending.next({
      analysisId: 'analysis-1',
      status: 'IN_PROGRESS',
      sourceId: 'source-1',
      targetRevision: 'feature/story',
      intentId: 'describe-project',
      intentVersion: 'v1',
      outcome: 'CREATED',
      sourceSnapshot: {},
    });
    expect(navigate).toHaveBeenCalledWith(['/analyses', 'analysis-1']);
  });

  it('navigates to a reused execution', async () => {
    execute.mockReturnValue(
      of({
        analysisId: 'analysis-existing',
        status: 'IN_PROGRESS',
        sourceId: 'source-1',
        targetRevision: null,
        intentId: 'describe-project',
        intentVersion: 'v1',
        outcome: 'REUSED',
        sourceSnapshot: {},
      }),
    );
    const fixture = await render([source]);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.state$.subscribe();
    fixture.componentInstance.submit();
    expect(navigate).toHaveBeenCalledWith(['/analyses', 'analysis-existing']);
  });
});
