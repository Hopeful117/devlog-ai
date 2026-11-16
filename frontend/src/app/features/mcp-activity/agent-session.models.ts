export interface AgentSession {
  readonly agentSessionId: string;
  readonly agentProvider: string | null;
  readonly firstObservedAt: string;
  readonly lastObservedAt: string | null;
  readonly operationCount: number;
  readonly respondedCount: number;
  readonly failedCount: number;
  readonly interruptedCount: number;
  readonly activeOperationCount: number;
  readonly lifecycleStatus: string;
}

export interface AgentSessionPage {
  readonly content: readonly AgentSession[];
  readonly totalSessions: number;
  readonly page: number;
  readonly size: number;
}
