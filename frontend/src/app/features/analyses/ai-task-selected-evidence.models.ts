import { AiTaskStatus, AiTaskType } from './analysis.models';

export type EvidenceAvailability = 'RECORDED' | 'NOT_RECORDED';
export type RepositoryContentStatus = 'COMPLETE' | 'TRUNCATED' | 'SKIPPED' | 'UNAVAILABLE';

export interface SelectedEvidenceTaskIdentity {
  readonly id: string;
  readonly taskType: AiTaskType;
  readonly status: AiTaskStatus;
  readonly createdAt: string;
}

interface SelectedEvidenceResponseBase {
  readonly analysisId: string;
  readonly projectId: string;
}

export interface NoAiTaskSelectedEvidenceResponse extends SelectedEvidenceResponseBase {
  readonly state: 'NO_AI_TASK';
  readonly task: null;
  readonly selectionVersion: null;
  readonly selectionDigest: null;
  readonly snapshotMetadata: null;
  readonly categories: null;
}

export interface SnapshotPendingSelectedEvidenceResponse extends SelectedEvidenceResponseBase {
  readonly state: 'SNAPSHOT_PENDING';
  readonly task: SelectedEvidenceTaskIdentity;
  readonly selectionVersion: null;
  readonly selectionDigest: null;
  readonly snapshotMetadata: null;
  readonly categories: null;
}

export interface SnapshotUnavailableSelectedEvidenceResponse extends SelectedEvidenceResponseBase {
  readonly state: 'SNAPSHOT_UNAVAILABLE';
  readonly task: SelectedEvidenceTaskIdentity;
  readonly selectionVersion: null;
  readonly selectionDigest: null;
  readonly snapshotMetadata: null;
  readonly categories: null;
}

export interface AvailableSelectedEvidenceResponse extends SelectedEvidenceResponseBase {
  readonly state: 'AVAILABLE';
  readonly task: SelectedEvidenceTaskIdentity;
  readonly selectionVersion: string | null;
  readonly selectionDigest: string | null;
  readonly snapshotMetadata: SelectedEvidenceSnapshotMetadata;
  readonly categories: SelectedEvidenceCategories;
}

export type AiTaskSelectedEvidenceResponse =
  | NoAiTaskSelectedEvidenceResponse
  | SnapshotPendingSelectedEvidenceResponse
  | SnapshotUnavailableSelectedEvidenceResponse
  | AvailableSelectedEvidenceResponse;

export interface SelectedEvidenceSnapshotMetadata {
  readonly project: SelectedEvidenceProjectMetadata | null;
  readonly analysis: SelectedEvidenceAnalysisMetadata | null;
  readonly projectProfile: SelectedEvidenceProjectProfileMetadata | null;
  readonly diagnostics: SelectedEvidenceDiagnosticsMetadata | null;
  readonly selection: SelectedEvidenceSelectionMetadata | null;
  readonly repositoryContext: SelectedEvidenceRepositoryContextMetadata | null;
}

export interface SelectedEvidenceProjectMetadata {
  readonly id: string | null;
  readonly name: string | null;
  readonly slug: string | null;
  readonly description: string | null;
  readonly status: string | null;
}

export interface SelectedEvidenceAnalysisMetadata {
  readonly id: string | null;
  readonly type: string | null;
  readonly intentId: string | null;
  readonly intentVersion: string | null;
  readonly status: string | null;
  readonly startedAt: string | null;
  readonly completedAt: string | null;
  readonly createdAt: string | null;
}

export interface SelectedEvidenceProjectProfileMetadata {
  readonly id: string | null;
  readonly projectId: string | null;
  readonly analysisId: string | null;
  readonly profileVersion: string | null;
  readonly rendererVersion: string | null;
  readonly generatedAt: string | null;
  readonly requestedRevision: string | null;
  readonly completeness: SelectedEvidenceProfileCompleteness | null;
  readonly deterministicSummary: string | null;
  readonly characteristicCount: number | null;
}

export interface SelectedEvidenceProfileCompleteness {
  readonly status: string | null;
  readonly collectionComplete: boolean | null;
  readonly truncated: boolean | null;
  readonly warningCount: number | null;
  readonly errorCount: number | null;
  readonly successfulCollectorCount: number | null;
  readonly collectorsWithWarningsCount: number | null;
  readonly failedCollectorCount: number | null;
}

export interface SelectedEvidenceDiagnosticsMetadata {
  readonly collectionComplete: boolean | null;
  readonly truncated: boolean | null;
  readonly warningCount: number | null;
  readonly errorCount: number | null;
}

export interface SelectedEvidenceSelectionMetadata {
  readonly selectionVersion: string | null;
  readonly appliedRules: readonly string[] | null;
  readonly selectedKnowledgeCount: number | null;
  readonly discardedKnowledgeCount: number | null;
  readonly knowledgeBudget: SelectedEvidenceKnowledgeBudget | null;
  readonly completeness: string | null;
}

export interface SelectedEvidenceKnowledgeBudget {
  readonly maximumFacts: number | null;
  readonly maximumObservations: number | null;
  readonly maximumInsights: number | null;
  readonly maximumArchitectureKnowledge: number | null;
  readonly maximumRepositoryEvidence: number | null;
}

export interface SelectedEvidenceRepositoryContextMetadata {
  readonly contextVersion: string | null;
  readonly profile: string | null;
  readonly warnings: readonly string[] | null;
  readonly contextDigest: string | null;
}

export interface SelectedEvidenceCategories {
  readonly facts: FactsSection;
  readonly observations: ObservationsSection;
  readonly priorInsights: PriorInsightsSection;
  readonly architectureKnowledge: ArchitectureKnowledgeSection;
  readonly engineeringEvents: EngineeringEventsSection;
  readonly humanContext: HumanContextSection;
  readonly evolutionContext: EvolutionContextSection;
  readonly repositoryEvidence: RepositoryEvidenceSection;
}

interface EvidenceSection<T> {
  readonly availability: EvidenceAvailability;
  readonly count: number;
  readonly items: readonly T[];
}

export interface FactsSection extends EvidenceSection<FactItem> {}
export interface ObservationsSection extends EvidenceSection<ObservationItem> {}
export interface PriorInsightsSection extends EvidenceSection<PriorInsightItem> {}
export interface ArchitectureKnowledgeSection extends EvidenceSection<ArchitectureKnowledgeItem> {}
export interface EngineeringEventsSection extends EvidenceSection<EngineeringEventItem> {}
export interface HumanContextSection extends EvidenceSection<HumanContextItem> {}
export interface EvolutionContextSection extends EvidenceSection<EvolutionContextItem> {}
export interface RepositoryEvidenceSection extends EvidenceSection<RepositoryEvidenceItem> {}

export interface FactItem {
  readonly id: string | null;
  readonly type: string | null;
  readonly content: string | null;
  readonly source: string | null;
  readonly evidenceReferences: readonly string[] | null;
  readonly detectedAt: string | null;
}

export interface ObservationItem {
  readonly id: string | null;
  readonly type: string | null;
  readonly content: string | null;
  readonly ruleId: string | null;
  readonly ruleVersion: string | null;
  readonly supportingFactIds: readonly string[] | null;
  readonly createdAt: string | null;
}

export interface PriorInsightItem {
  readonly type: string | null;
  readonly severity: string | null;
  readonly title: string | null;
  readonly content: string | null;
}

export interface ArchitectureKnowledgeItem {
  readonly insightId: string | null;
  readonly proposalId: string | null;
  readonly normalizedType: string | null;
  readonly severity: string | null;
  readonly sourceType: string | null;
  readonly title: string | null;
  readonly content: string | null;
  readonly rationale: string | null;
  readonly evidenceReferences: readonly string[] | null;
  readonly createdAt: string | null;
}

export interface EngineeringEventItem {
  readonly id: string | null;
  readonly category: string | null;
  readonly title: string | null;
  readonly summary: string | null;
  readonly sourceId: string | null;
  readonly baseCommit: string | null;
  readonly targetCommit: string | null;
  readonly occurredAt: string | null;
  readonly proposalId: string | null;
}

export interface HumanContextItem {
  readonly id: string | null;
  readonly type: string | null;
  readonly title: string | null;
  readonly contentMarkdown: string | null;
  readonly status: string | null;
  readonly updatedAt: string | null;
}

export interface EvolutionContextItem {
  readonly contextVersion: string | null;
  readonly projectId: string | null;
  readonly sourceId: string | null;
  readonly baseCommit: string | null;
  readonly targetCommit: string | null;
  readonly comparisonPolicy: string | null;
  readonly mergeCommit: boolean | null;
  readonly targetCommittedAt: string | null;
  readonly commitDiff: CommitDiff | null;
}

export interface CommitDiff {
  readonly projectId: string | null;
  readonly repositoryId: string | null;
  readonly commitHash: string | null;
  readonly firstParentHash: string | null;
  readonly parentHashes: readonly string[] | null;
  readonly rootCommit: boolean | null;
  readonly mergeCommit: boolean | null;
  readonly commitMessage: string | null;
  readonly committedAt: string | null;
  readonly changedFiles: readonly ChangedFile[] | null;
  readonly statistics: DiffStatistics | null;
  readonly candidateAdrReferences: readonly string[] | null;
  readonly candidateRoadmapReferences: readonly string[] | null;
  readonly evidenceReferences: readonly string[] | null;
  readonly truncated: boolean | null;
  readonly warnings: readonly string[] | null;
}

export interface ChangedFile {
  readonly changeType: string | null;
  readonly oldPath: string | null;
  readonly newPath: string | null;
  readonly binary: boolean | null;
  readonly insertions: number | null;
  readonly deletions: number | null;
  readonly language: string | null;
  readonly category: string | null;
  readonly excludedFromAnalysis: boolean | null;
  readonly exclusionReason: string | null;
  readonly evidenceReference: string | null;
}

export interface DiffStatistics {
  readonly filesChanged: number | null;
  readonly insertions: number | null;
  readonly deletions: number | null;
  readonly binaryFiles: number | null;
}

export interface RepositoryEvidenceItem {
  readonly layer: string | null;
  readonly kind: string | null;
  readonly reference: string | null;
  readonly summary: string | null;
  readonly occurredAt: string | null;
  readonly relatedReferences: readonly string[] | null;
  readonly content: RepositoryContent | null;
  readonly symbols: RepositorySymbols | null;
}

export interface RepositoryContent {
  readonly status: RepositoryContentStatus | null;
  readonly text: string | null;
  readonly reason: string | null;
  readonly policyId: string | null;
  readonly policyVersion: string | null;
  readonly revision: string | null;
  readonly allocationPolicyId: string | null;
  readonly allocationPolicyVersion: string | null;
  readonly allocationRank: number | null;
}

export interface RepositorySymbols {
  readonly status: string | null;
  readonly reason: string | null;
  readonly policyId: string | null;
  readonly policyVersion: string | null;
  readonly extractorId: string | null;
  readonly extractorVersion: string | null;
  readonly revision: string | null;
  readonly allocationRank: number | null;
  readonly truncated: boolean | null;
  readonly returnedSymbolCount: number | null;
  readonly availableSymbolCount: number | null;
  readonly declarations: readonly SymbolDeclaration[] | null;
}

export interface SymbolDeclaration {
  readonly kind: string | null;
  readonly name: string | null;
  readonly owningType: string | null;
  readonly modifiers: readonly string[] | null;
  readonly returnType: string | null;
  readonly parameters: readonly SymbolParameter[] | null;
  readonly annotations: readonly string[] | null;
  readonly location: SymbolLocation | null;
}

export interface SymbolParameter {
  readonly type: string | null;
  readonly name: string | null;
}

export interface SymbolLocation {
  readonly beginLine: number | null;
  readonly beginColumn: number | null;
  readonly endLine: number | null;
  readonly endColumn: number | null;
}
