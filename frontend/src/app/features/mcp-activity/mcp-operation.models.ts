export type McpOperationStatus = 'ACTIVE' | 'RESPONDED' | 'FAILED' | 'INTERRUPTED';

export interface McpOperation {
  readonly instanceId: string;
  readonly operationId: string;
  readonly toolName: string | null;
  readonly status: McpOperationStatus;
  readonly generation: number;
  readonly startedAt: string | null;
  readonly completedAt: string | null;
  readonly durationMs: number | null;
  readonly reasonCode: string | null;
  readonly agentSessionId: string | null;
  readonly agentProvider: string | null;
}

export interface McpOperationPage {
  readonly content: readonly McpOperation[];
  readonly totalOperations: number;
  readonly page: number;
  readonly size: number;
}
