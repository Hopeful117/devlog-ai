import { AnalysisStatus, UserGuidance } from '../analyses/analysis.models';

export interface ProjectUnderstandingRequest {
  readonly sourceId: string;
  readonly targetRevision?: string;
  readonly userGuidance?: UserGuidance;
}

export interface ProjectUnderstandingResponse {
  readonly analysisId: string;
  readonly status: AnalysisStatus;
  readonly sourceId: string;
  readonly targetRevision: string | null;
  readonly intentId: string;
  readonly intentVersion: string;
  readonly outcome: 'CREATED' | 'REUSED';
  readonly sourceSnapshot: Readonly<Record<string, string | null>>;
}
