export type ProjectFreshnessStatus = 'NO_BASELINE' | 'CURRENT' | 'STALE' | 'UNKNOWN';
export type ProjectRefreshGuidance =
  'ESTABLISH_BASELINE' | 'REFRESH_NOT_NEEDED' | 'REFRESH_RECOMMENDED' | 'VERIFY_BASELINE';

export interface ProjectFreshnessResponse {
  readonly version: string;
  readonly id: string;
  readonly projectId: string;
  readonly checkedAt: string;
  readonly status: ProjectFreshnessStatus;
  readonly guidance: ProjectRefreshGuidance;
  readonly source: {
    readonly id: string;
    readonly name: string;
    readonly defaultBranch: string | null;
    readonly requestedRevision: string;
    readonly currentRevision: string;
  };
  readonly baseline: {
    readonly analysisId: string;
    readonly completedAt: string;
    readonly analyzedRevision: string | null;
  } | null;
  readonly review: {
    readonly total: number;
    readonly pending: number;
    readonly accepted: number;
    readonly rejected: number;
  };
}
