import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AgentSession } from './agent-session.models';
import { AgentSessionActivitySection } from './agent-session-activity-section';
import { AgentSessionService } from './agent-session.service';
import { McpOperationService } from './mcp-operation.service';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';

const TEST_ENV = { backendBaseUrl: '', analysisPollingIntervalMs: 999999 };

function session(overrides: Partial<AgentSession> = {}): AgentSession {
  return {
    agentSessionId: 'cc7d159d-a138-4b73-b14e-1e7279d013ab',
    agentProvider: 'claude-code_2-1-325_agent',
    firstObservedAt: '2026-01-01T12:00:00Z',
    lastObservedAt: '2026-01-01T12:05:00Z',
    operationCount: 5,
    respondedCount: 4,
    failedCount: 1,
    interruptedCount: 0,
    activeOperationCount: 0,
    ...overrides,
  };
}

describe('AgentSessionActivitySection', () => {
  let component: AgentSessionActivitySection;

  beforeEach(() => {
    const mockSessionService = {
      byProject: () => of({ content: [], totalSessions: 0, page: 0, size: 20 }),
      operationsBySession: () => of({ content: [], totalOperations: 0, page: 0, size: 20 }),
    };
    const mockOperationService = {
      byProject: () => of({ content: [], totalOperations: 0, page: 0, size: 20 }),
    };
    TestBed.configureTestingModule({
      imports: [AgentSessionActivitySection],
      providers: [
        { provide: APP_ENVIRONMENT, useValue: TEST_ENV },
        { provide: AgentSessionService, useValue: mockSessionService },
        { provide: McpOperationService, useValue: mockOperationService },
      ],
    });
    const fixture = TestBed.createComponent(AgentSessionActivitySection);
    component = fixture.componentInstance;
    component.projectId = 'proj-1';
  });

  describe('providerBadge', () => {
    it('returns CC for claude-code', () => {
      expect(component.providerBadge(session({ agentProvider: 'claude-code_2-1-325_agent' }))).toBe(
        'CC',
      );
    });

    it('returns OC for opencode', () => {
      expect(component.providerBadge(session({ agentProvider: 'opencode_1-18-11' }))).toBe('OC');
    });

    it('returns CD for claude-desktop', () => {
      expect(component.providerBadge(session({ agentProvider: 'claude-desktop_1-0' }))).toBe('CD');
    });

    it('returns ? for null provider', () => {
      expect(component.providerBadge(session({ agentProvider: null }))).toBe('?');
    });

    it('returns ? for unrecognized provider', () => {
      expect(component.providerBadge(session({ agentProvider: 'unknown-agent' }))).toBe('?');
    });
  });

  it('abbreviates session id to 8 characters', () => {
    expect(component.shortId(session())).toBe('cc7d159d');
  });

  it('reports active when activeOperationCount > 0', () => {
    expect(component.isActive(session({ activeOperationCount: 2 }))).toBe(true);
  });

  it('reports not active when activeOperationCount is 0', () => {
    expect(component.isActive(session({ activeOperationCount: 0 }))).toBe(false);
  });

  it('closes detail when same session clicked twice', () => {
    const s = session();
    component.detailState = { state: 'loaded', sessionId: s.agentSessionId, operations: [] };
    component.openDetail(s);
    expect(component.detailState.state).toBe('closed');
  });
});
