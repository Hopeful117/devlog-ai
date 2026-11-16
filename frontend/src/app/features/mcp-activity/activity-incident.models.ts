export type IncidentSeverity = 'ERROR' | 'WARN';

export interface ActivityIncident {
  readonly incidentId: string;
  readonly severity: IncidentSeverity;
  readonly toolName: string | null;
  readonly reasonCode: string | null;
  readonly occurrenceCount: number;
  readonly firstOccurredAt: string;
  readonly lastOccurredAt: string;
  readonly recentAlertIds: readonly string[];
}

export interface ActivityIncidentPage {
  readonly content: readonly ActivityIncident[];
  readonly totalIncidents: number;
  readonly errorCount: number;
  readonly warnCount: number;
  readonly windowMinutes: number;
  readonly windowStart: string;
  readonly page: number;
  readonly size: number;
}
