import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ActivityIncident, ActivityIncidentPage } from './activity-incident.models';
import { ActivityIncidentIndicator } from './activity-incident-indicator';
import { ActivityIncidentService } from './activity-incident.service';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';

const TEST_ENV = { backendBaseUrl: '', analysisPollingIntervalMs: 999999 };

function incident(overrides: Partial<ActivityIncident> = {}): ActivityIncident {
  return {
    incidentId: 'abc123def456789a',
    severity: 'ERROR',
    toolName: 'get_engineering_context',
    reasonCode: 'MCP_PROCESS_FAILED',
    occurrenceCount: 3,
    firstOccurredAt: '2026-11-16T11:00:00Z',
    lastOccurredAt: '2026-11-16T11:04:30Z',
    recentAlertIds: ['inst1/op1', 'inst1/op2'],
    ...overrides,
  };
}

function page(overrides: Partial<ActivityIncidentPage> = {}): ActivityIncidentPage {
  return {
    content: [],
    totalIncidents: 0,
    errorCount: 0,
    warnCount: 0,
    windowMinutes: 60,
    windowStart: '2026-11-16T11:00:00Z',
    page: 0,
    size: 20,
    ...overrides,
  };
}

describe('ActivityIncidentIndicator', () => {
  let component: ActivityIncidentIndicator;
  const navigate = vi.fn().mockResolvedValue(true);

  function setup(mockService: Partial<ActivityIncidentService> = {}) {
    const service = {
      byProject: () => of(page()),
      ...mockService,
    };
    TestBed.configureTestingModule({
      imports: [ActivityIncidentIndicator],
      providers: [
        { provide: APP_ENVIRONMENT, useValue: TEST_ENV },
        { provide: ActivityIncidentService, useValue: service },
        { provide: Router, useValue: { navigate } },
        { provide: ActivatedRoute, useValue: {} },
      ],
    });
    const fixture = TestBed.createComponent(ActivityIncidentIndicator);
    component = fixture.componentInstance;
    component.projectId = 'proj-1';
  }

  afterEach(() => {
    navigate.mockClear();
    TestBed.resetTestingModule();
  });

  it('emits null summary on service error (silent degradation)', async () => {
    setup({ byProject: () => throwError(() => new Error('network')) });
    const summary = await firstValueFrom(component.summary$);
    expect(summary).toBeNull();
  });

  it('emits summary with zero total when no incidents', async () => {
    setup({ byProject: () => of(page({ totalIncidents: 0, errorCount: 0, warnCount: 0 })) });
    const summary = await firstValueFrom(component.summary$);
    expect(summary?.total).toBe(0);
  });

  it('emits correct errorCount and warnCount', async () => {
    setup({
      byProject: () =>
        of(page({ totalIncidents: 3, errorCount: 2, warnCount: 1, content: [incident()] })),
    });
    const summary = await firstValueFrom(component.summary$);
    expect(summary?.errorCount).toBe(2);
    expect(summary?.warnCount).toBe(1);
  });

  it('emits total matching totalIncidents from response', async () => {
    setup({ byProject: () => of(page({ totalIncidents: 5, errorCount: 5, warnCount: 0 })) });
    const summary = await firstValueFrom(component.summary$);
    expect(summary?.total).toBe(5);
  });

  it('navigateToAlerts calls router.navigate with view=alerts queryParam', () => {
    setup();
    component.navigateToAlerts();
    expect(navigate).toHaveBeenCalledWith(['../activity'], {
      relativeTo: {},
      queryParams: { view: 'alerts' },
    });
  });

  it('destroys cleanly without error', () => {
    setup();
    expect(() => component.ngOnDestroy()).not.toThrow();
  });

  it('summary reflects WARN-only incident correctly', async () => {
    setup({ byProject: () => of(page({ totalIncidents: 1, errorCount: 0, warnCount: 1 })) });
    const summary = await firstValueFrom(component.summary$);
    expect(summary?.errorCount).toBe(0);
    expect(summary?.warnCount).toBe(1);
  });
});
