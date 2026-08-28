package com.hopeful117.devlogai.analysis.evidence.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AiTaskSelectedEvidenceResponse(
        State state,
        UUID analysisId,
        UUID projectId,
        TaskIdentity task,
        String selectionVersion,
        String selectionDigest,
        SnapshotMetadata snapshotMetadata,
        Categories categories
) {
    public AiTaskSelectedEvidenceResponse {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(projectId, "projectId");
        if (state == State.NO_AI_TASK && task != null) {
            throw new IllegalArgumentException("NO_AI_TASK cannot contain task identity");
        }
        if (state != State.NO_AI_TASK && task == null) {
            throw new IllegalArgumentException(state + " requires task identity");
        }
        if (state == State.AVAILABLE && (snapshotMetadata == null || categories == null)) {
            throw new IllegalArgumentException("AVAILABLE requires projected snapshot data");
        }
        if (state != State.AVAILABLE && (snapshotMetadata != null || categories != null)) {
            throw new IllegalArgumentException(state + " cannot contain projected snapshot data");
        }
    }

    public static AiTaskSelectedEvidenceResponse noAiTask(UUID analysisId, UUID projectId) {
        return new AiTaskSelectedEvidenceResponse(
                State.NO_AI_TASK, analysisId, projectId, null, null, null, null, null);
    }

    public static AiTaskSelectedEvidenceResponse snapshotPending(
            UUID analysisId, UUID projectId, TaskIdentity task) {
        return new AiTaskSelectedEvidenceResponse(
                State.SNAPSHOT_PENDING, analysisId, projectId, task,
                null, null, null, null);
    }

    public static AiTaskSelectedEvidenceResponse snapshotUnavailable(
            UUID analysisId, UUID projectId, TaskIdentity task) {
        return new AiTaskSelectedEvidenceResponse(
                State.SNAPSHOT_UNAVAILABLE, analysisId, projectId, task,
                null, null, null, null);
    }

    public static AiTaskSelectedEvidenceResponse available(
            UUID analysisId,
            UUID projectId,
            TaskIdentity task,
            String selectionVersion,
            String selectionDigest,
            ProjectedSnapshot snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new AiTaskSelectedEvidenceResponse(
                State.AVAILABLE, analysisId, projectId, task,
                selectionVersion, selectionDigest, snapshot.metadata(), snapshot.categories());
    }

    public enum State {
        NO_AI_TASK,
        SNAPSHOT_PENDING,
        SNAPSHOT_UNAVAILABLE,
        AVAILABLE
    }

    public enum Availability {
        RECORDED,
        NOT_RECORDED
    }

    public enum RepositoryContentStatus {
        COMPLETE,
        TRUNCATED,
        SKIPPED,
        UNAVAILABLE
    }

    public record TaskIdentity(
            UUID id,
            AiTaskType taskType,
            AiTaskStatus status,
            Instant createdAt
    ) {
        public TaskIdentity {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(taskType, "taskType");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record ProjectedSnapshot(SnapshotMetadata metadata, Categories categories) {
        public ProjectedSnapshot {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(categories, "categories");
        }
    }

    public record SnapshotMetadata(
            ProjectMetadata project,
            AnalysisMetadata analysis,
            ProjectProfileMetadata projectProfile,
            DiagnosticsMetadata diagnostics,
            SelectionMetadata selection,
            RepositoryContextMetadata repositoryContext
    ) { }

    public record ProjectMetadata(
            UUID id,
            String name,
            String slug,
            String description,
            String status
    ) { }

    public record AnalysisMetadata(
            UUID id,
            String type,
            String intentId,
            String intentVersion,
            String status,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt
    ) { }

    public record ProjectProfileMetadata(
            UUID id,
            UUID projectId,
            UUID analysisId,
            String profileVersion,
            String rendererVersion,
            Instant generatedAt,
            String requestedRevision,
            ProfileCompleteness completeness,
            String deterministicSummary,
            Integer characteristicCount
    ) { }

    public record ProfileCompleteness(
            String status,
            Boolean collectionComplete,
            Boolean truncated,
            Integer warningCount,
            Integer errorCount,
            Integer successfulCollectorCount,
            Integer collectorsWithWarningsCount,
            Integer failedCollectorCount
    ) { }

    public record DiagnosticsMetadata(
            Boolean collectionComplete,
            Boolean truncated,
            Integer warningCount,
            Integer errorCount
    ) { }

    public record SelectionMetadata(
            String selectionVersion,
            List<String> appliedRules,
            Integer selectedKnowledgeCount,
            Integer discardedKnowledgeCount,
            KnowledgeBudget knowledgeBudget,
            String completeness
    ) {
        public SelectionMetadata {
            appliedRules = copyNullable(appliedRules);
        }
    }

    public record KnowledgeBudget(
            Integer maximumFacts,
            Integer maximumObservations,
            Integer maximumInsights,
            Integer maximumArchitectureKnowledge,
            Integer maximumRepositoryEvidence
    ) { }

    public record RepositoryContextMetadata(
            String contextVersion,
            String profile,
            List<String> warnings,
            String contextDigest
    ) {
        public RepositoryContextMetadata {
            warnings = copyNullable(warnings);
        }
    }

    public record Categories(
            FactsSection facts,
            ObservationsSection observations,
            PriorInsightsSection priorInsights,
            ArchitectureKnowledgeSection architectureKnowledge,
            EngineeringEventsSection engineeringEvents,
            HumanContextSection humanContext,
            EvolutionContextSection evolutionContext,
            RepositoryEvidenceSection repositoryEvidence
    ) {
        public Categories {
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(observations, "observations");
            Objects.requireNonNull(priorInsights, "priorInsights");
            Objects.requireNonNull(architectureKnowledge, "architectureKnowledge");
            Objects.requireNonNull(engineeringEvents, "engineeringEvents");
            Objects.requireNonNull(humanContext, "humanContext");
            Objects.requireNonNull(evolutionContext, "evolutionContext");
            Objects.requireNonNull(repositoryEvidence, "repositoryEvidence");
        }
    }

    public record FactsSection(Availability availability, int count, List<FactItem> items) {
        public FactsSection {
            items = sectionItems(availability, count, items);
        }
    }

    public record FactItem(
            UUID id,
            String type,
            String content,
            String source,
            List<String> evidenceReferences,
            Instant detectedAt
    ) {
        public FactItem {
            evidenceReferences = copyNullable(evidenceReferences);
        }
    }

    public record ObservationsSection(
            Availability availability, int count, List<ObservationItem> items) {
        public ObservationsSection {
            items = sectionItems(availability, count, items);
        }
    }

    public record ObservationItem(
            UUID id,
            String type,
            String content,
            String ruleId,
            String ruleVersion,
            List<UUID> supportingFactIds,
            Instant createdAt
    ) {
        public ObservationItem {
            supportingFactIds = copyNullable(supportingFactIds);
        }
    }

    public record PriorInsightsSection(
            Availability availability, int count, List<PriorInsightItem> items) {
        public PriorInsightsSection {
            items = sectionItems(availability, count, items);
        }
    }

    public record PriorInsightItem(String type, String severity, String title, String content) { }

    public record ArchitectureKnowledgeSection(
            Availability availability, int count, List<ArchitectureKnowledgeItem> items) {
        public ArchitectureKnowledgeSection {
            items = sectionItems(availability, count, items);
        }
    }

    public record ArchitectureKnowledgeItem(
            UUID insightId,
            UUID proposalId,
            String normalizedType,
            String severity,
            String sourceType,
            String title,
            String content,
            String rationale,
            List<String> evidenceReferences,
            Instant createdAt
    ) {
        public ArchitectureKnowledgeItem {
            evidenceReferences = copyNullable(evidenceReferences);
        }
    }

    public record EngineeringEventsSection(
            Availability availability, int count, List<EngineeringEventItem> items) {
        public EngineeringEventsSection {
            items = sectionItems(availability, count, items);
        }
    }

    public record EngineeringEventItem(
            UUID id,
            String category,
            String title,
            String summary,
            UUID sourceId,
            String baseCommit,
            String targetCommit,
            Instant occurredAt,
            UUID proposalId
    ) { }

    public record HumanContextSection(
            Availability availability, int count, List<HumanContextItem> items) {
        public HumanContextSection {
            items = sectionItems(availability, count, items);
        }
    }

    public record HumanContextItem(
            UUID id,
            String type,
            String title,
            String contentMarkdown,
            String status,
            Instant updatedAt
    ) { }

    public record EvolutionContextSection(
            Availability availability, int count, List<EvolutionContextItem> items) {
        public EvolutionContextSection {
            items = sectionItems(availability, count, items);
        }
    }

    public record EvolutionContextItem(
            String contextVersion,
            UUID projectId,
            UUID sourceId,
            String baseCommit,
            String targetCommit,
            String comparisonPolicy,
            Boolean mergeCommit,
            Instant targetCommittedAt,
            CommitDiff commitDiff
    ) { }

    public record CommitDiff(
            UUID projectId,
            UUID repositoryId,
            String commitHash,
            String firstParentHash,
            List<String> parentHashes,
            Boolean rootCommit,
            Boolean mergeCommit,
            String commitMessage,
            Instant committedAt,
            List<ChangedFile> changedFiles,
            DiffStatistics statistics,
            List<String> candidateAdrReferences,
            List<String> candidateRoadmapReferences,
            List<String> evidenceReferences,
            Boolean truncated,
            List<String> warnings
    ) {
        public CommitDiff {
            parentHashes = copyNullable(parentHashes);
            changedFiles = copyNullable(changedFiles);
            candidateAdrReferences = copyNullable(candidateAdrReferences);
            candidateRoadmapReferences = copyNullable(candidateRoadmapReferences);
            evidenceReferences = copyNullable(evidenceReferences);
            warnings = copyNullable(warnings);
        }
    }

    public record ChangedFile(
            String changeType,
            String oldPath,
            String newPath,
            Boolean binary,
            Integer insertions,
            Integer deletions,
            String language,
            String category,
            Boolean excludedFromAnalysis,
            String exclusionReason,
            String evidenceReference
    ) { }

    public record DiffStatistics(
            Integer filesChanged,
            Integer insertions,
            Integer deletions,
            Integer binaryFiles
    ) { }

    public record RepositoryEvidenceSection(
            Availability availability, int count, List<RepositoryEvidenceItem> items) {
        public RepositoryEvidenceSection {
            items = sectionItems(availability, count, items);
        }
    }

    public record RepositoryEvidenceItem(
            String layer,
            String kind,
            String reference,
            String summary,
            Instant occurredAt,
            List<String> relatedReferences,
            RepositoryContent content,
            RepositorySymbols symbols
    ) {
        public RepositoryEvidenceItem {
            relatedReferences = copyNullable(relatedReferences);
        }
    }

    public record RepositoryContent(
            RepositoryContentStatus status,
            String text,
            String reason,
            String policyId,
            String policyVersion,
            String revision,
            String allocationPolicyId,
            String allocationPolicyVersion,
            Integer allocationRank
    ) { }

    public record RepositorySymbols(
            String status,
            String reason,
            String policyId,
            String policyVersion,
            String extractorId,
            String extractorVersion,
            String revision,
            Integer allocationRank,
            Boolean truncated,
            Integer returnedSymbolCount,
            Integer availableSymbolCount,
            List<SymbolDeclaration> declarations
    ) {
        public RepositorySymbols {
            declarations = copyNullable(declarations);
        }
    }

    public record SymbolDeclaration(
            String kind,
            String name,
            String owningType,
            List<String> modifiers,
            String returnType,
            List<SymbolParameter> parameters,
            List<String> annotations,
            SymbolLocation location
    ) {
        public SymbolDeclaration {
            modifiers = copyNullable(modifiers);
            parameters = copyNullable(parameters);
            annotations = copyNullable(annotations);
        }
    }

    public record SymbolParameter(String type, String name) { }

    public record SymbolLocation(
            Integer beginLine,
            Integer beginColumn,
            Integer endLine,
            Integer endColumn
    ) { }

    private static <T> List<T> sectionItems(
            Availability availability, int count, List<T> items) {
        Objects.requireNonNull(availability, "availability");
        List<T> copied = List.copyOf(items);
        if (count != copied.size()) {
            throw new IllegalArgumentException("Section count must match item count");
        }
        if (availability == Availability.NOT_RECORDED && count != 0) {
            throw new IllegalArgumentException("NOT_RECORDED section must be empty");
        }
        return copied;
    }

    private static <T> List<T> copyNullable(List<T> items) {
        return items == null ? null : List.copyOf(items);
    }
}
