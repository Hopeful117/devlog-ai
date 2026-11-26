export type TaskStatus =
  | "UNKNOWN"
  | "SUBMITTED"
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "REJECTED"
  | "POLL_TIMEOUT";

export type PlatformOutcome =
  | "ATTEMPTED"
  | "RESULT_REPORTED"
  | "ERROR"
  | "INCOMPLETE";

export type PresenceState =
  | "IDLE"
  | "WORKING"
  | "READY"
  | "ERROR"
  | "UNKNOWN";

export interface ProjectionCall {
  callID: string;
  tool: string;
  startedAt: number;
  endedAt?: number;
  platformOutcome: PlatformOutcome;
  mcpOutcome: "UNKNOWN" | "ERROR";
  mcpOperationId?: string;
  taskId?: string;
  taskStatus: TaskStatus;
}

export interface ProjectionSnapshot {
  sessionID: string;
  connectivity: "UNKNOWN";
  calls: ProjectionCall[];
}

export function isDevLogToolName(name: unknown): boolean;
export function decodeDevLogResult(result: unknown): {
  mcpOutcome: "UNKNOWN" | "ERROR";
  mcpOperationId?: string;
  taskId?: string;
  taskStatus: TaskStatus;
};
export function createProjectionStore(options?: {
  location?: string;
  now?: () => number;
  maxSessions?: number;
  maxHistoryPerSession?: number;
  maxActivePerSession?: number;
  maxActiveCalls?: number;
  retentionMs?: number;
}): {
  before(event: unknown): void;
  after(event: unknown): void;
  snapshot(sessionID: string): ProjectionSnapshot;
  clear(): void;
  counts(): { sessions: number; activeCalls: number };
};
export function derivePresence(calls: ProjectionCall[]): PresenceState;
export const projectionLimits: Readonly<{
  maxSessions: number;
  maxHistoryPerSession: number;
  maxActivePerSession: number;
  maxActiveCalls: number;
  retentionMs: number;
}>;
