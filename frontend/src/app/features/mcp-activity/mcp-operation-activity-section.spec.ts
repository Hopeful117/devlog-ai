import { McpOperation } from './mcp-operation.models';
import { McpOperationActivitySection } from './mcp-operation-activity-section';
import { TestBed } from '@angular/core/testing';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';

describe('McpOperationActivitySection', () => {
  let component: McpOperationActivitySection;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [McpOperationActivitySection],
      providers: [
        {
          provide: APP_ENVIRONMENT,
          useValue: { backendBaseUrl: 'http://localhost', analysisPollingIntervalMs: 30000 },
        },
      ],
    });
    const fixture = TestBed.createComponent(McpOperationActivitySection);
    component = fixture.componentInstance;
    component.projectId = 'test-project';
  });

  describe('isStale', () => {
    it('returns false for a RESPONDED operation', () => {
      const op = operation('RESPONDED', null);
      expect(component.isStale(op)).toBe(false);
    });

    it('returns false for a FAILED operation', () => {
      const op = operation('FAILED', null);
      expect(component.isStale(op)).toBe(false);
    });

    it('returns false for ACTIVE started within 5 minutes', () => {
      const recentStart = new Date(Date.now() - 2 * 60 * 1000).toISOString();
      const op = operation('ACTIVE', recentStart);
      expect(component.isStale(op)).toBe(false);
    });

    it('returns true for ACTIVE started more than 5 minutes ago', () => {
      const oldStart = new Date(Date.now() - 6 * 60 * 1000).toISOString();
      const op = operation('ACTIVE', oldStart);
      expect(component.isStale(op)).toBe(true);
    });

    it('returns false for ACTIVE with null startedAt', () => {
      const op = operation('ACTIVE', null);
      expect(component.isStale(op)).toBe(false);
    });

    it('returns false for ACTIVE started exactly at the threshold boundary (not over)', () => {
      const boundaryStart = new Date(Date.now() - 5 * 60 * 1000 + 1).toISOString();
      const op = operation('ACTIVE', boundaryStart);
      expect(component.isStale(op)).toBe(false);
    });
  });

  describe('statusClass', () => {
    it('returns status--responded for RESPONDED', () => {
      expect(component.statusClass(operation('RESPONDED', null))).toBe('status--responded');
    });

    it('returns status--failed for FAILED', () => {
      expect(component.statusClass(operation('FAILED', null))).toBe('status--failed');
    });

    it('returns status--interrupted for INTERRUPTED', () => {
      expect(component.statusClass(operation('INTERRUPTED', null))).toBe('status--interrupted');
    });

    it('returns status--active for ACTIVE within threshold', () => {
      const recentStart = new Date(Date.now() - 1000).toISOString();
      expect(component.statusClass(operation('ACTIVE', recentStart))).toBe('status--active');
    });

    it('returns status--stale for ACTIVE beyond threshold', () => {
      const oldStart = new Date(Date.now() - 6 * 60 * 1000).toISOString();
      expect(component.statusClass(operation('ACTIVE', oldStart))).toBe('status--stale');
    });
  });

  describe('statusLabel', () => {
    it('returns ✓ for RESPONDED', () => {
      expect(component.statusLabel(operation('RESPONDED', null))).toBe('✓');
    });

    it('returns ✗ for FAILED', () => {
      expect(component.statusLabel(operation('FAILED', null))).toBe('✗');
    });

    it('returns ~ for INTERRUPTED', () => {
      expect(component.statusLabel(operation('INTERRUPTED', null))).toBe('~');
    });

    it('returns … for ACTIVE within threshold', () => {
      const recentStart = new Date(Date.now() - 1000).toISOString();
      expect(component.statusLabel(operation('ACTIVE', recentStart))).toBe('…');
    });

    it('returns ? for stale ACTIVE', () => {
      const oldStart = new Date(Date.now() - 6 * 60 * 1000).toISOString();
      expect(component.statusLabel(operation('ACTIVE', oldStart))).toBe('?');
    });
  });

  describe('displayTime', () => {
    it('returns completedAt when present', () => {
      const op: McpOperation = {
        ...operation('RESPONDED', '2026-01-01T12:00:00Z'),
        completedAt: '2026-01-01T12:00:01Z',
      };
      expect(component.displayTime(op)).toBe('2026-01-01T12:00:01Z');
    });

    it('falls back to startedAt when completedAt is null', () => {
      const op = operation('ACTIVE', '2026-01-01T12:00:00Z');
      expect(component.displayTime(op)).toBe('2026-01-01T12:00:00Z');
    });

    it('returns null when both are null', () => {
      const op = operation('ACTIVE', null);
      expect(component.displayTime(op)).toBeNull();
    });
  });
});

function operation(status: McpOperation['status'], startedAt: string | null): McpOperation {
  return {
    instanceId: 'inst-1',
    operationId: 'op-1',
    toolName: 'get_engineering_context',
    status,
    generation: 1,
    startedAt,
    completedAt: null,
    durationMs: null,
    reasonCode: null,
  };
}
