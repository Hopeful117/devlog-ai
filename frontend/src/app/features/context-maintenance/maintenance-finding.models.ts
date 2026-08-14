export type MaintenanceContextSurface = 'PROJECT_UNDERSTANDING' | 'PROJECT_PROJECTION';

export type MaintenanceFindingIssueType =
  | 'STALE_PROJECT_UNDERSTANDING'
  | 'PROJECTION_REFRESH_GAP'
  | 'MISSING_PROJECTION_REFRESH';

export type MaintenanceFindingSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

export type MaintenanceFindingStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED';

export type MaintenanceSuggestedActionCategory =
  | 'MONITOR'
  | 'REFRESH'
  | 'INVESTIGATE'
  | 'REVIEW';

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
  readonly createdAt: string;
  readonly updatedAt: string;
}
