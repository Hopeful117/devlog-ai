export type MaintenanceContextSurface =
  'PROJECT_UNDERSTANDING' | 'PROJECT_PROJECTION' | 'INTERNAL_HUMAN_CONTEXT';

export type MaintenanceFindingIssueType =
  | 'STALE_PROJECT_UNDERSTANDING'
  | 'PROJECTION_REFRESH_GAP'
  | 'MISSING_PROJECTION_REFRESH'
  | 'STALE_HUMAN_CONTEXT_INPUT'
  | 'TRUSTED_KNOWLEDGE_EXACT_DUPLICATE'
  | 'TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE'
  | 'TRUSTED_KNOWLEDGE_OVERLAP_REVIEW';

export type MaintenanceFindingSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

export type MaintenanceFindingStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'DISMISSED';

export type MaintenanceSuggestedActionCategory = 'MONITOR' | 'REFRESH' | 'INVESTIGATE' | 'REVIEW';

export type MaintenanceFindingActionType = 'ACKNOWLEDGE' | 'DISMISS' | 'RESOLVE' | 'AUTO_RESOLVE';

export interface MaintenanceFindingAction {
  readonly id: string;
  readonly actionType: MaintenanceFindingActionType;
  readonly actedBy: string;
  readonly actedAt: string;
  readonly comment: string | null;
}

export interface MaintenanceFindingActionRequest {
  readonly actedBy: string;
  readonly comment: string;
}

export interface MaintenanceFinding {
  readonly id: string;
  readonly projectId: string;
  readonly contextSurface: MaintenanceContextSurface;
  readonly issueType: MaintenanceFindingIssueType;
  readonly severity: MaintenanceFindingSeverity;
  readonly status: MaintenanceFindingStatus;
  readonly suggestedAction: MaintenanceSuggestedActionCategory;
  readonly humanReviewRequired: boolean;
  readonly summary: string;
  readonly details: string | null;
  readonly actionHistory: readonly MaintenanceFindingAction[];
  readonly createdAt: string;
  readonly updatedAt: string;
}
