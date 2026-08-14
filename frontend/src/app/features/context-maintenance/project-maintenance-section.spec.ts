import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

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
  createdAt: '2026-08-14T10:00:00Z',
  updatedAt: '2026-08-14T10:05:00Z',
};

describe('ProjectMaintenanceSection', () => {
  const getByProject = vi.fn();

  async function render() {
    await TestBed.configureTestingModule({
      imports: [ProjectMaintenanceSection],
      providers: [{ provide: MaintenanceFindingService, useValue: { getByProject } }],
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
  });

  afterEach(() => TestBed.resetTestingModule());

  it('renders an explicit empty state', async () => {
    getByProject.mockReturnValue(of([]));
    const fixture = await render();

    expect(getByProject).toHaveBeenCalledWith('project-1');
    expect(fixture.nativeElement.textContent).toContain(
      'No active maintenance findings currently exist for this project.',
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
});
