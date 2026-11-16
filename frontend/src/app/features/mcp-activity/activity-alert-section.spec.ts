import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivityAlert } from './activity-alert.models';
import { ActivityAlertSection } from './activity-alert-section';
import { ActivityAlertService } from './activity-alert.service';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';

const TEST_ENV = { backendBaseUrl: '', analysisPollingIntervalMs: 999999 };

function alert(overrides: Partial<ActivityAlert> = {}): ActivityAlert {
  return {
    alertId: 'inst1/op1',
    severity: 'ERROR',
    eventType: 'MCP_TOOL_FAILED',
    toolName: 'devlog_get_story_context',
    reasonCode: null,
    durationMs: 4521,
    occurredAt: '2026-11-16T11:42:03Z',
    instanceId: 'inst1',
    operationId: 'op1',
    agentSessionId: 'cc7d159d-a138-4b73-b14e-1e7279d013ab',
    agentProvider: 'claude-code',
    ...overrides,
  };
}

describe('ActivityAlertSection', () => {
  let component: ActivityAlertSection;

  function setup(mockService: Partial<ActivityAlertService> = {}) {
    const service = {
      byProject: () => of({ content: [], totalAlerts: 0, page: 0, size: 20 }),
      ...mockService,
    };
    TestBed.configureTestingModule({
      imports: [ActivityAlertSection],
      providers: [
        { provide: APP_ENVIRONMENT, useValue: TEST_ENV },
        { provide: ActivityAlertService, useValue: service },
      ],
    });
    const fixture = TestBed.createComponent(ActivityAlertSection);
    component = fixture.componentInstance;
    component.projectId = 'proj-1';
  }

  it('toolLabel returns toolName when present', () => {
    setup();
    expect(component.toolLabel(alert({ toolName: 'devlog_get_story_context' }))).toBe('devlog_get_story_context');
  });

  it('toolLabel returns "unknown tool" when toolName is null', () => {
    setup();
    expect(component.toolLabel(alert({ toolName: null }))).toBe('unknown tool');
  });

  it('reasonLabel returns reasonCode when present', () => {
    setup();
    expect(component.reasonLabel(alert({ reasonCode: 'MCP_REAUTH_REQUIRED' }))).toBe('MCP_REAUTH_REQUIRED');
  });

  it('reasonLabel returns "operation failed" for FAILED with null reasonCode', () => {
    setup();
    expect(component.reasonLabel(alert({ eventType: 'MCP_TOOL_FAILED', reasonCode: null }))).toBe('operation failed');
  });

  it('reasonLabel returns "operation interrupted" for INTERRUPTED with null reasonCode', () => {
    setup();
    expect(component.reasonLabel(alert({ eventType: 'MCP_TOOL_INTERRUPTED', reasonCode: null }))).toBe('operation interrupted');
  });

  it('shortSession returns first 8 chars of agentSessionId', () => {
    setup();
    expect(component.shortSession(alert({ agentSessionId: 'cc7d159d-a138-4b73-b14e-1e7279d013ab' }))).toBe('cc7d159d');
  });

  it('shortSession returns null when agentSessionId is null', () => {
    setup();
    expect(component.shortSession(alert({ agentSessionId: null }))).toBeNull();
  });
});
