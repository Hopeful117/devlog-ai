export type McpToolEventStatus = 'ACTIVE' | 'RESPONDED' | 'FAILED' | 'INTERRUPTED';
export type McpToolEventType =
  'MCP_TOOL_STARTED' | 'MCP_TOOL_RESPONDED' | 'MCP_TOOL_FAILED' | 'MCP_TOOL_INTERRUPTED';

export interface McpToolEvent {
  readonly id: string;
  readonly instanceId: string;
  readonly operationId: string;
  readonly toolName: string | null;
  readonly eventType: McpToolEventType;
  readonly status: McpToolEventStatus;
  readonly generation: number;
  readonly durationMs: number | null;
  readonly reasonCode: string | null;
  readonly occurredAt: string;
}

export interface McpToolEventPage {
  readonly content: readonly McpToolEvent[];
  readonly totalElements: number;
  readonly page: number;
  readonly size: number;
}
