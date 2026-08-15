import '@angular/compiler';
import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';

import { MaintenanceFinding } from './maintenance-finding.models';
import { MaintenanceFindingService } from './maintenance-finding.service';
import { ProjectMaintenanceSection } from './project-maintenance-section';

const finding: MaintenanceFinding = {
  id: '4f8eb9d5-ef84-482c-b6e2-fc0f8820a3c7',
  projectId: 'project-1',
  contextSurface: 'PROJECT_PROJECTION',
  issueType: 'PROJECTION_REFRESH_GAP',
  severity: 'HIGH',
  status: 'OPEN',
  suggestedAction: 'REVIEW',
  humanReviewRequired: true,
  summary: 'Projection freshness is lagging behind repository changes.',
  details: 'A manual review is required before relying on the current projection.',
  actionHistory: [],
  assessments: [],
  createdAt: '2026-08-14T10:00:00Z',
  updatedAt: '2026-08-14T10:05:00Z',
};

const duplicateFinding: MaintenanceFinding = {
  ...finding,
  id: 'debt-1',
  contextSurface: 'PROJECT_UNDERSTANDING',
  issueType: 'TRUSTED_KNOWLEDGE_EXACT_DUPLICATE',
  status: 'OPEN',
  summary: 'Trusted knowledge exact duplicate debt detected for cluster "adr".',
};

const humanContextFinding: MaintenanceFinding = {
  ...finding,
  id: 'human-1',
  contextSurface: 'INTERNAL_HUMAN_CONTEXT',
  issueType: 'STALE_HUMAN_CONTEXT_INPUT',
  suggestedAction: 'REVIEW',
  summary: "Active human context input 'Medium-term objective' may be stale or superseded.",
};

const staleUnderstandingFinding: MaintenanceFinding = {
  ...finding,
  id: 'stale-1',
  contextSurface: 'PROJECT_UNDERSTANDING',
  issueType: 'STALE_PROJECT_UNDERSTANDING',
  severity: 'HIGH',
  suggestedAction: 'REFRESH',
  summary: 'Project understanding is stale for source Github.',
};

const missingProjectionFinding: MaintenanceFinding = {
  ...finding,
  id: 'missing-1',
  contextSurface: 'PROJECT_PROJECTION',
  issueType: 'MISSING_PROJECTION_REFRESH',
  severity: 'MEDIUM',
  suggestedAction: 'INVESTIGATE',
  summary: 'Projection refresh missing.',
};

describe('ProjectMaintenanceSection', () => {
  const getByProject = vi.fn();
  const acknowledge = vi.fn();
  const dismiss = vi.fn();
  const resolve = vi.fn();

  async function render() {
    await TestBed.configureTestingModule({
      imports: [ProjectMaintenanceSection],
      providers: [
        {
          provide: MaintenanceFindingService,
          useValue: { getByProject, acknowledge, dismiss, resolve },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectMaintenanceSection);
    fixture.componentRef.setInput('projectId', 'project-1');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    getByProject.mockReset();
    acknowledge.mockReset();
    dismiss.mockReset();
    resolve.mockReset();
  });

  afterEach(() => TestBed.resetTestingModule());

  it('renders an explicit empty state', async () => {
    getByProject.mockReturnValue(of([]));
    const fixture = await render();

    expect(getByProject).toHaveBeenCalledWith('project-1');
    expect(fixture.nativeElement.textContent).toContain(
      'No active maintenance findings. Run an evaluation to check context health.',
    );
  });

  it('renders review-needed findings with bounded details', async () => {
    getByProject.mockReturnValue(of([finding]));
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('Context maintenance');
    expect(fixture.nativeElement.textContent).toContain('Projection freshness is lagging');
    expect(fixture.nativeElement.textContent).toContain('Human review required');
    expect(fixture.nativeElement.textContent).toContain('Action: review');
  });

  it('renders an error state gracefully', async () => {
    getByProject.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
    );
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain(
      'Maintenance guidance remains unavailable for this project.',
    );
  });

  it('renders remediation actions for duplicate debt and posts acknowledgement', async () => {
    getByProject.mockReturnValue(of([duplicateFinding]));
    acknowledge.mockReturnValue(
      of({ ...duplicateFinding, status: 'ACKNOWLEDGED', actionHistory: [] }),
    );
    const fixture = await render();

    const button = fixture.debugElement
      .queryAll(By.css('button'))
      .find((candidate) => candidate.nativeElement.textContent.includes('Acknowledge'));

    expect(button).toBeDefined();
    button!.nativeElement.click();
    fixture.detectChanges();

    expect(acknowledge).toHaveBeenCalledWith('project-1', 'debt-1', {
      actedBy: '00000000-0000-0000-0000-000000000001',
      comment: '',
    });
  });

  it('renders human context findings with the shared remediation workflow', async () => {
    getByProject.mockReturnValue(of([humanContextFinding]));
    resolve.mockReturnValue(of({ ...humanContextFinding, status: 'RESOLVED', actionHistory: [] }));
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('Human context');
    expect(fixture.nativeElement.textContent).toContain('may be stale or superseded');

    fixture.componentInstance.setComment('human-1', 'Archived after review');
    fixture.componentInstance.resolve(humanContextFinding);

    expect(resolve).toHaveBeenCalledWith('project-1', 'human-1', {
      actedBy: '00000000-0000-0000-0000-000000000001',
      comment: 'Archived after review',
    });
  });

  it('renders automatic maintenance history clearly', async () => {
    const openFindingWithAutoResolve = {
      ...humanContextFinding,
      id: 'auto-1',
      status: 'OPEN' as const,
      actionHistory: [
        {
          id: 'action-1',
          actionType: 'AUTO_RESOLVE' as const,
          actedBy: '00000000-0000-0000-0000-000000000002',
          actedAt: '2026-08-14T14:00:00Z',
          comment:
            'Automatically resolved because the deterministic maintenance condition no longer applies.',
        },
      ],
    };
    getByProject.mockReturnValue(of([openFindingWithAutoResolve]));
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain(
      'auto resolve by 00000000-0000-0000-0000-000000000002',
    );
    expect(fixture.nativeElement.textContent).toContain(
      'Automatically resolved because the deterministic maintenance condition no longer applies.',
    );
  });

  it('renders dismiss button for stale project understanding finding', async () => {
    getByProject.mockReturnValue(of([staleUnderstandingFinding]));
    dismiss.mockReturnValue(
      of({ ...staleUnderstandingFinding, status: 'DISMISSED', actionHistory: [] }),
    );
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('Understanding');
    expect(fixture.nativeElement.textContent).toContain('Project understanding is stale');

    const dismissButton = fixture.debugElement
      .queryAll(By.css('button'))
      .find((candidate) => candidate.nativeElement.textContent.includes('Dismiss'));

    expect(dismissButton).toBeDefined();
  });

  it('renders dismiss button for missing projection refresh finding', async () => {
    getByProject.mockReturnValue(of([missingProjectionFinding]));
    dismiss.mockReturnValue(
      of({ ...missingProjectionFinding, status: 'DISMISSED', actionHistory: [] }),
    );
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('Projection');
    expect(fixture.nativeElement.textContent).toContain('Projection refresh missing');

    const dismissButton = fixture.debugElement
      .queryAll(By.css('button'))
      .find((candidate) => candidate.nativeElement.textContent.includes('Dismiss'));

    expect(dismissButton).toBeDefined();
  });
});
