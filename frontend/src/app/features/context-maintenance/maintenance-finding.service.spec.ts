import '@angular/compiler';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { MaintenanceFinding } from './maintenance-finding.models';
import { MaintenanceFindingService } from './maintenance-finding.service';

const projectId = 'a1ee6d55-e034-491a-a6e6-cdad70573b24';
const finding: MaintenanceFinding = {
  id: '4f8eb9d5-ef84-482c-b6e2-fc0f8820a3c7',
  projectId,
  contextSurface: 'PROJECT_PROJECTION',
  issueType: 'PROJECTION_REFRESH_GAP',
  severity: 'HIGH',
  status: 'OPEN',
  suggestedAction: 'REVIEW',
  humanReviewRequired: true,
  summary: 'Projection freshness is lagging behind repository changes.',
  details: 'A manual review is required before relying on the current projection.',
  actionHistory: [],
  createdAt: '2026-08-14T10:00:00Z',
  updatedAt: '2026-08-14T10:05:00Z',
};

describe('MaintenanceFindingService', () => {
  let service: MaintenanceFindingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_ENVIRONMENT, useValue: { backendBaseUrl: 'http://core.test' } },
      ],
    });
    service = TestBed.inject(MaintenanceFindingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests maintenance findings by project id', () => {
    let result: readonly MaintenanceFinding[] | undefined;
    service.getByProject(projectId).subscribe((findings) => (result = findings));

    const request = http.expectOne(
      `http://core.test/api/v1/projects/${projectId}/maintenance-findings`,
    );
    expect(request.request.method).toBe('GET');
    request.flush([finding]);
    expect(result).toEqual([finding]);
  });

  it('posts an acknowledgement action for a finding', () => {
    let result: MaintenanceFinding | undefined;
    service
      .acknowledge(projectId, finding.id, {
        actedBy: '00000000-0000-0000-0000-000000000001',
        comment: 'Reviewed and acknowledged',
      })
      .subscribe((response) => (result = response));

    const request = http.expectOne(
      `http://core.test/api/v1/projects/${projectId}/maintenance-findings/${finding.id}/acknowledgements`,
    );
    expect(request.request.method).toBe('POST');
    request.flush({ ...finding, status: 'ACKNOWLEDGED' });
    expect(result?.status).toBe('ACKNOWLEDGED');
  });
});
