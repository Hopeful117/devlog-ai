export type EngineeringEventCategory =
  | 'FEATURE_INTRODUCTION'
  | 'BUG_RESOLUTION'
  | 'ARCHITECTURE_CHANGE'
  | 'TECHNOLOGY_CHANGE'
  | 'ENGINEERING_IMPROVEMENT'
  | 'INFRASTRUCTURE_CHANGE';

export interface EngineeringEvent {
  readonly version: string;
  readonly id: string;
  readonly projectId: string;
  readonly analysisId: string;
  readonly proposalId: string;
  readonly validationId: string;
  readonly sourceId: string;
  readonly category: EngineeringEventCategory;
  readonly title: string;
  readonly summary: string;
  readonly significance: string;
  readonly baseCommit: string;
  readonly targetCommit: string;
  readonly comparisonPolicy: 'FIRST_PARENT';
  readonly mergeCommit: boolean;
  readonly occurredAt: string;
  readonly createdAt: string;
  readonly confidence: number;
  readonly supportingFactIds: readonly string[];
  readonly supportingObservationIds: readonly string[];
  readonly evidenceReferences: readonly string[];
}

export interface EngineeringEventPage {
  readonly version: string;
  readonly items: readonly EngineeringEvent[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly hasPrevious: boolean;
  readonly hasNext: boolean;
}

export interface EngineeringEventExecution {
  readonly version: string;
  readonly analysisId: string;
  readonly status: string;
  readonly projectId: string;
  readonly sourceId: string;
  readonly baseCommit: string;
  readonly targetCommit: string;
  readonly comparisonPolicy: 'FIRST_PARENT';
  readonly mergeCommit: boolean;
  readonly intentId: string;
  readonly intentVersion: string;
  readonly outcome: 'CREATED' | 'REUSED';
}
