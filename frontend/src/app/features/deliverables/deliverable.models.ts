export type DeliverableType =
  | 'PROJECT_DESCRIPTION'
  | 'README'
  | 'ARCHITECTURE_SUMMARY'
  | 'PORTFOLIO_DESCRIPTION'
  | 'TECHNICAL_SUMMARY'
  | 'BLOG_ARTICLE';

export interface CreateDeliverableRequest {
  readonly projectId: string;
  readonly analysisId?: string;
  readonly type: DeliverableType;
  readonly audience: string;
  readonly style: string;
  readonly language: string;
  readonly additionalGuidance?: string;
}

export interface Deliverable {
  readonly id: string;
  readonly projectId: string;
  readonly analysisId: string | null;
  readonly type: DeliverableType;
  readonly audience: string;
  readonly style: string;
  readonly language: string;
  readonly additionalGuidance: string | null;
  readonly title: string;
  readonly content: string;
  readonly promptVersion: string;
  readonly promptDigest: string;
  readonly provider: string;
  readonly modelIdentifier: string;
  readonly generatedAt: string;
  readonly sourceInsightIds: readonly string[];
}
