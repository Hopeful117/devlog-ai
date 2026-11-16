export type ActivityAlertSeverity = 'ERROR' | 'WARN';

export interface ActivityAlert {
  readonly alertId: string;
  readonly severity: ActivityAlertSeverity;
  readonly eventType: string;
  readonly toolName: string | null;
  readonly reasonCode: string | null;
  readonly durationMs: number | null;
  readonly occurredAt: string;
  readonly instanceId: string;
  readonly operationId: string;
  readonly agentSessionId: string | null;
  readonly agentProvider: string | null;
}

export interface ActivityAlertPage {
  readonly content: readonly ActivityAlert[];
  readonly totalAlerts: number;
  readonly page: number;
  readonly size: number;
}
