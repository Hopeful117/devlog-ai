import { JsonValue } from '../analyses/analysis.models';

export type ProposalStatus = 'PROPOSED' | 'ACCEPTED' | 'REJECTED';
export type ProposalType =
  'INSIGHT' | 'ENGINEERING_DECISION' | 'ENGINEERING_EVENT' | 'CHALLENGE' | 'DOCUMENTATION';
export type InsightType =
  | 'ARCHITECTURAL'
  | 'DOCUMENTATION'
  | 'TECHNOLOGY'
  | 'EVOLUTION'
  | 'TECHNICAL_DEBT'
  | 'SECURITY'
  | 'RISK'
  | 'RECOMMENDATION';
export type InsightSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type ConfidenceValue = number | null;
export interface InsightProposalContent {
  readonly insightType: string | null;
  readonly title: string | null;
  readonly summary: string | null;
  readonly rationale: string | null;
}
interface ProposalFields {
  readonly id: string;
  readonly projectId: string;
  readonly analysisId: string;
  readonly type: ProposalType;
  readonly status: ProposalStatus;
  readonly payload: Readonly<Record<string, JsonValue>>;
  readonly insight: InsightProposalContent | null;
  readonly confidence: ConfidenceValue;
  readonly supportingFactIds: readonly string[];
  readonly supportingObservationIds: readonly string[];
  readonly evidenceReferences: readonly string[];
  readonly createdAt: string;
  readonly decidedAt: string | null;
}
export interface InsightProposalSummary extends ProposalFields {}
export interface InsightProposalDetail extends ProposalFields {}
export type ValidationDecision = 'ACCEPTED' | 'REJECTED';
export interface ProposalDecisionRequest {
  readonly proposalId: string;
  readonly decision: ValidationDecision;
  readonly comment: string | null;
  readonly validatedBy: string;
  readonly insightSeverity: InsightSeverity | null;
}
export interface AcceptProposalRequest {
  readonly comment: string | null;
  readonly validatedBy: string;
  readonly insightSeverity: InsightSeverity;
}
export interface RejectProposalRequest {
  readonly comment: string | null;
  readonly validatedBy: string;
}
export interface ProposalDecisionResult {
  readonly id: string;
  readonly proposalId: string;
  readonly decision: ValidationDecision;
  readonly validatedAt: string;
  readonly validatedBy: string;
  readonly comment: string | null;
}
interface InsightFields {
  readonly id: string;
  readonly projectId: string;
  readonly analysisId: string;
  readonly proposalId: string;
  readonly validationId: string;
  readonly type: InsightType;
  readonly severity: InsightSeverity;
  readonly title: string;
  readonly content: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}
export interface InsightSummary extends InsightFields {}
export interface InsightDetail extends InsightFields {}
