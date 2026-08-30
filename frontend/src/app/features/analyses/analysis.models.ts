export type JsonValue =
  string | number | boolean | null | JsonValue[] | { readonly [key: string]: JsonValue };

export type AnalysisStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
export type AnalysisType =
  | 'ARCHITECTURE_REVIEW'
  | 'PROJECT_EVOLUTION'
  | 'TECHNICAL_DEBT'
  | 'SECURITY_REVIEW'
  | 'DOCUMENTATION_REVIEW';
export type AiTaskStatus = 'CREATED' | 'SUBMITTED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type AiTaskType =
  | 'DECISION_PROPOSAL_GENERATION'
  | 'EVENT_PROPOSAL_GENERATION'
  | 'INSIGHT_GENERATION'
  | 'DOCUMENTATION_GENERATION'
  | 'CHALLENGE_PROPOSAL_GENERATION';

export interface Source {
  readonly id: string;
  readonly name: string;
}

export interface UserGuidance {
  readonly focus: string | null;
  readonly audience: string | null;
  readonly levelOfDetail: string | null;
  readonly writingStyle: string | null;
  readonly outputContext: string | null;
  readonly priorities: readonly string[];
}

interface AnalysisFields {
  readonly id: string;
  readonly projectId: string;
  readonly type: AnalysisType;
  readonly intentId: string;
  readonly intentVersion: string;
  readonly status: AnalysisStatus;
  readonly startedAt: string | null;
  readonly completedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly userGuidance: UserGuidance | null;
}

export interface AnalysisSummary extends AnalysisFields {}
export interface AnalysisDetail extends AnalysisFields {}

export interface CreateAnalysisRequest {
  readonly projectId: string;
  readonly intentId: string;
  readonly sourceId?: string;
  readonly targetRevision?: string;
  readonly userGuidance?: UserGuidance;
}

export interface AnalysisWorkflowResult {
  readonly analysisId: string;
  readonly analysisStatus: AnalysisStatus;
  readonly factCount: number;
  readonly observationCount: number;
  readonly aiTaskId: string;
  readonly aiTaskStatus: AiTaskStatus;
  readonly correlationId: string;
}

export interface IntentDefinition {
  readonly id: string;
  readonly version: string;
  readonly objective: string;
  readonly supportedInsightTypes: readonly string[];
  readonly constraints: readonly string[];
  readonly outputSchema: Readonly<Record<string, JsonValue>>;
  readonly promptTemplate: string;
  readonly executionMode: string;
}

export interface AiTaskSummary {
  readonly taskType: AiTaskType;
  readonly status: AiTaskStatus;
  readonly intentId: string;
  readonly intentVersion: string;
  readonly provider: string;
  readonly startedAt: string | null;
  readonly completedAt: string | null;
}

export interface AiTaskDetail {
  readonly id: string;
  readonly analysisId: string;
  readonly correlationId: string;
  readonly taskType: AiTaskType;
  readonly intentId: string | null;
  readonly intentVersion: string | null;
  readonly intentSnapshot: Readonly<Record<string, JsonValue>> | null;
  readonly userGuidanceSnapshot: Readonly<Record<string, JsonValue>> | null;
  readonly promptRequestId: string | null;
  readonly promptVersion: string | null;
  readonly provider: string | null;
  readonly modelIdentifier: string | null;
  readonly promptContentDigest: string | null;
  readonly contextDigest: string | null;
  readonly selectedKnowledgeSnapshot: Readonly<Record<string, JsonValue>> | null;
  readonly selectionVersion: string | null;
  readonly selectionDigest: string | null;
  readonly status: AiTaskStatus;
  readonly contextSnapshot: Readonly<Record<string, JsonValue>> | null;
  readonly externalJobId: string | null;
  readonly attemptCount: number;
  readonly failureCode: string | null;
  readonly failureMessage: string | null;
  readonly createdAt: string;
  readonly submittedAt: string | null;
  readonly startedAt: string | null;
  readonly completedAt: string | null;
}
export interface PipelineStage {
  readonly name: string;
  readonly status: string;
  readonly resourceCount: number;
}
export interface ProfileSummary {
  readonly profileAvailable: boolean;
  readonly profileId: string | null;
  readonly profileVersion: string | null;
  readonly profileCompleteness: 'COMPLETE' | 'PARTIAL' | 'FAILED' | null;
  readonly characteristicCount: number;
}

export interface AnalysisDiagnostics {
  readonly identity: {
    readonly analysisId: string;
    readonly projectId: string;
    readonly analysisType: AnalysisType;
    readonly intentId: string;
    readonly intentVersion: string;
    readonly status: AnalysisStatus;
  };
  readonly revision: {
    readonly requestedRevision: string | null;
    readonly resolvedRevisions: Readonly<Record<string, JsonValue>>;
  };
  readonly timeline: {
    readonly createdAt: string;
    readonly startedAt: string | null;
    readonly completedAt: string | null;
    readonly duration: string | number | null;
  };
  readonly counts: {
    readonly sourceCount: number;
    readonly factCount: number;
    readonly observationCount: number;
    readonly warningCount: number;
    readonly proposalCount: number;
  };
  readonly completeness: {
    readonly collectionComplete: boolean;
    readonly truncated: boolean;
    readonly warningCount: number;
    readonly errorCount: number;
  };
  readonly collectors: {
    readonly collectorCount: number;
    readonly successfulCollectors: number;
    readonly collectorsWithWarnings: number;
    readonly failedCollectors: number;
  };
  readonly aiTask: AiTaskSummary | null;
  readonly pipeline: readonly PipelineStage[];
  readonly technicalMetadata: {
    readonly contextBuilderVersion: string;
    readonly collectorVersions: Readonly<Record<string, JsonValue>>;
    readonly serializedContextSize: number;
  };
  readonly profile: ProfileSummary;
  readonly links: Readonly<Record<string, string>>;
}

export interface CollectionWarning {
  readonly id: string;
  readonly analysisId: string;
  readonly sourceId: string | null;
  readonly collectorType: string;
  readonly collectorVersion: string;
  readonly code: string;
  readonly severity: 'WARNING' | 'ERROR';
  readonly message: string;
  readonly evidenceReference: string | null;
  readonly metadata: Readonly<Record<string, JsonValue>>;
  readonly occurredAt: string;
}

export interface ProjectProfile {
  readonly id: string;
  readonly projectId: string;
  readonly analysisId: string;
  readonly profileVersion: string;
  readonly rendererVersion: string;
  readonly generatedAt: string;
  readonly requestedRevision: string | null;
  readonly resolvedRevisions: Readonly<Record<string, JsonValue>>;
  readonly completeness: {
    readonly status: 'COMPLETE' | 'PARTIAL' | 'FAILED';
    readonly collectionComplete: boolean;
    readonly truncated: boolean;
    readonly warningCount: number;
    readonly errorCount: number;
    readonly successfulCollectorCount: number;
    readonly collectorsWithWarningsCount: number;
    readonly failedCollectorCount: number;
  };
  readonly sections: readonly Readonly<Record<string, JsonValue>>[];
  readonly deterministicSummary: string;
  readonly sourceObservations: readonly Readonly<Record<string, JsonValue>>[];
  readonly characteristicCount: number;
}

export type ProposalStatus = 'PROPOSED' | 'ACCEPTED' | 'REJECTED';
export type ProposalType =
  'INSIGHT' | 'ENGINEERING_DECISION' | 'ENGINEERING_EVENT' | 'CHALLENGE' | 'DOCUMENTATION';
export type InsightType =
  'ARCHITECTURE' | 'DOCUMENTATION' | 'DECISION' | 'ROADMAP' | 'STORY' | 'INSIGHT';
export type InsightSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type DeliverableType =
  | 'PROJECT_DESCRIPTION'
  | 'README'
  | 'ARCHITECTURE_SUMMARY'
  | 'PORTFOLIO_DESCRIPTION'
  | 'TECHNICAL_SUMMARY'
  | 'BLOG_ARTICLE';

export interface AnalysisResult {
  readonly analysis: {
    readonly id: string;
    readonly projectId: string;
    readonly objective: string;
    readonly scope: 'PROJECT_SCOPE' | 'REPOSITORY_SCOPE';
    readonly intentId: string;
    readonly intentVersion: string;
    readonly status: AnalysisStatus;
    readonly startedAt: string | null;
    readonly completedAt: string | null;
    readonly durationSeconds: number | null;
    readonly sourcesAnalyzed: readonly string[];
    readonly targetRevision: string | null;
    readonly repositoryName: string | null;
  };
  readonly execution: {
    readonly success: boolean | null;
    readonly failureCode: string | null;
    readonly failureMessage: string | null;
  };
  readonly proposals: {
    readonly total: number;
    readonly byStatus: Readonly<Record<string, number>>;
    readonly byType: Readonly<Record<string, number>>;
    readonly items: readonly ProposalSummary[];
  };
  readonly insights: {
    readonly total: number;
    readonly items: readonly InsightSummary[];
  };
  readonly deliverables: {
    readonly total: number;
    readonly items: readonly DeliverableSummary[];
  };
  readonly evidence: {
    readonly facts: EvidenceCategory;
    readonly observations: EvidenceCategory;
    readonly priorInsights: EvidenceCategory;
    readonly architectureKnowledge: EvidenceCategory;
    readonly engineeringEvents: EvidenceCategory;
    readonly humanContext: EvidenceCategory;
    readonly evolutionContext: EvidenceCategory;
    readonly repositoryEvidence: EvidenceCategory;
  };
  readonly nextActions: readonly NextAction[];
}

export interface ProposalSummary {
  readonly id: string;
  readonly type: ProposalType;
  readonly status: ProposalStatus;
  readonly confidence: number | null;
  readonly title: string;
  readonly summary: string;
  readonly evidencePreview: readonly string[];
  readonly proposalId: string;
  readonly trustedArtifact: TrustedArtifact | null;
  readonly rationale: string | null;
  readonly insightType: string | null;
  readonly deltaType: string | null;
  readonly context: string | null;
  readonly choice: string | null;
  readonly consequences: string | null;
  readonly category: string | null;
  readonly significance: string | null;
  readonly supportingFactIds: readonly string[];
  readonly supportingObservationIds: readonly string[];
}

export type TrustedArtifactType = 'INSIGHT' | 'DECISION' | 'ENGINEERING_EVENT';
export type TrustedArtifactAvailability = 'AVAILABLE' | 'UNAVAILABLE';

export interface TrustedArtifact {
  readonly id: string | null;
  readonly type: TrustedArtifactType;
  readonly availability: TrustedArtifactAvailability;
  readonly detailAvailable: boolean;
}

export interface InsightSummary {
  readonly id: string;
  readonly type: InsightType;
  readonly severity: InsightSeverity;
  readonly title: string;
  readonly content: string;
  readonly rationale: string;
  readonly confidence: number | null;
  readonly evidenceReferences: readonly string[];
  readonly insightId: string;
}

export interface DeliverableSummary {
  readonly id: string;
  readonly type: DeliverableType;
  readonly title: string;
  readonly audience: string;
  readonly status: string;
  readonly generatedAt: string;
  readonly sourceInsights: readonly string[];
  readonly deliverableId: string;
}

export interface EvidenceCategory {
  readonly count: number;
  readonly items: readonly EvidenceItem[];
}

export interface EvidenceItem {
  readonly layer: string;
  readonly kind: string;
  readonly reference: string;
  readonly summary: string;
  readonly occurredAt: string;
  readonly relatedReferences: readonly string[];
}

export interface NextAction {
  readonly action: string;
  readonly label: string;
  readonly available: boolean;
}
