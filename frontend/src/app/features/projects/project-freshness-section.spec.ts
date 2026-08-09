import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ProjectFreshnessResponse } from './project-freshness.models';
import { ProjectFreshnessSection } from './project-freshness-section';
import { ProjectFreshnessService } from './project-freshness.service';
import { SourceSummary } from './source.models';

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

const stale: ProjectFreshnessResponse = {
  version: 'project-freshness-v1',
  id: 'check-1',
  projectId: 'project-1',
  checkedAt: '2026-08-09T00:00:00Z',
  status: 'STALE',
  guidance: 'REFRESH_RECOMMENDED',
  source: {
    id: 'source-1',
    name: 'Core',
    defaultBranch: 'main',
    requestedRevision: 'origin/main',
    currentRevision: 'a'.repeat(40),
  },
  baseline: {
    analysisId: 'analysis-1',
    completedAt: '2026-08-08T00:00:00Z',
    analyzedRevision: 'b'.repeat(40),
  },
  review: { total: 6, pending: 6, accepted: 0, rejected: 0 },
};

describe('ProjectFreshnessSection', () => {
  const latest = vi.fn();
  const check = vi.fn();

  async function render() {
    await TestBed.configureTestingModule({
      imports: [ProjectFreshnessSection],
      providers: [
        provideRouter([]),
        { provide: ProjectFreshnessService, useValue: { getLatest: latest, check } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectFreshnessSection);
    fixture.componentRef.setInput('projectId', 'project-1');
    fixture.componentRef.setInput('sources', [source]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    latest.mockReset().mockReturnValue(of(null));
    check.mockReset();
  });
  afterEach(() => TestBed.resetTestingModule());

  it('loads stored state but never checks Git on creation', async () => {
    const fixture = await render();
    expect(latest).toHaveBeenCalledWith('project-1', 'source-1');
    expect(check).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Freshness has not been checked');
  });

  it('suppresses duplicate checks and displays stale review state', async () => {
    const pending = new Subject<ProjectFreshnessResponse>();
    check.mockReturnValue(pending);
    const fixture = await render();
    fixture.componentInstance.view$.subscribe();
    fixture.componentInstance.check();
    fixture.componentInstance.check();
    expect(check).toHaveBeenCalledTimes(1);
    pending.next(stale);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('STALE');
    expect(fixture.nativeElement.textContent).toContain('6 / 6');
    expect(fixture.nativeElement.textContent).toContain('Refresh understanding');
  });

  it('emits a Source selection without launching understanding', async () => {
    const fixture = await render();
    const emitted = vi.fn();
    fixture.componentInstance.refreshRequested.subscribe(emitted);
    fixture.componentInstance.requestRefresh();
    expect(emitted).toHaveBeenCalledWith('source-1');
    expect(check).not.toHaveBeenCalled();
  });
});
