package com.hopeful117.devlogai.analysis.evidence.projection;

import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.AnalysisMetadata;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeItem;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.Availability;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.Categories;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ChangedFile;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.CommitDiff;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.DiagnosticsMetadata;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.DiffStatistics;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.EngineeringEventItem;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.EngineeringEventsSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.EvolutionContextItem;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.EvolutionContextSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.FactItem;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.FactsSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.HumanContextItem;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.HumanContextSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.KnowledgeBudget;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ObservationItem;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ObservationsSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.PriorInsightItem;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.PriorInsightsSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ProfileCompleteness;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ProjectMetadata;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ProjectProfileMetadata;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ProjectedSnapshot;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.RepositoryContent;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.RepositoryContentStatus;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.RepositoryContextMetadata;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.RepositoryEvidenceItem;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.RepositorySymbols;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.SelectionMetadata;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.SnapshotMetadata;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.SymbolDeclaration;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.SymbolLocation;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.SymbolParameter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

@Component
public class HistoricalSelectedEvidenceSnapshotProjector {
    private static final Set<String> SUPPORTED_VERSIONS = Set.of(
            "knowledge-selection-v1",
            "knowledge-selection-v2",
            "knowledge-selection-v3",
            "knowledge-selection-v4"
    );

    private final ObjectMapper objectMapper;

    public HistoricalSelectedEvidenceSnapshotProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProjectedSnapshot project(
            UUID taskId,
            String selectionVersion,
            String selectionDigest,
            UUID expectedAnalysisId,
            UUID expectedProjectId,
            Map<String, Object> snapshot
    ) {
        ReadContext context = new ReadContext(taskId, selectionVersion);
        if (selectionVersion == null || selectionVersion.isBlank()
                || !SUPPORTED_VERSIONS.contains(selectionVersion)) {
            throw context.failure("selectionVersion");
        }
        if (snapshot == null) {
            throw context.failure("snapshot");
        }

        Map<?, ?> root;
        try {
            root = objectMapper.convertValue(snapshot, Map.class);
        } catch (RuntimeException exception) {
            throw context.failure("snapshot");
        }

        ProjectMetadata project = projectMetadata(root, context);
        AnalysisMetadata analysis = analysisMetadata(root, context);
        validateIdentity(project == null ? null : project.id(), expectedProjectId,
                "project.id", context);
        validateIdentity(analysis == null ? null : analysis.id(), expectedAnalysisId,
                "analysis.id", context);

        ProjectProfileMetadata profile = projectProfile(root, context);
        DiagnosticsMetadata diagnostics = diagnostics(root, context);
        SelectionMetadata selection = selectionMetadata(root, context);
        validateEmbeddedSelection(root, selection, selectionVersion, selectionDigest, context);

        Map<?, ?> repositoryContext = nullableObject(root, "repositoryContext",
                "repositoryContext", context);
        RepositoryContextMetadata repositoryMetadata = repositoryMetadata(repositoryContext, context);

        Categories categories = new Categories(
                facts(root, context),
                observations(root, context),
                priorInsights(root, context),
                architectureKnowledge(root, context),
                engineeringEvents(root, context),
                humanContext(root, context),
                evolutionContext(root, context),
                repositoryEvidence(root, repositoryContext, context)
        );

        return new ProjectedSnapshot(
                new SnapshotMetadata(project, analysis, profile, diagnostics,
                        selection, repositoryMetadata),
                categories
        );
    }

    private ProjectMetadata projectMetadata(Map<?, ?> root, ReadContext context) {
        Map<?, ?> value = nullableObject(root, "project", "project", context);
        if (value == null) return null;
        return new ProjectMetadata(
                uuid(value, "id", "project.id", context),
                string(value, "name", "project.name", context),
                string(value, "slug", "project.slug", context),
                string(value, "description", "project.description", context),
                string(value, "status", "project.status", context)
        );
    }

    private AnalysisMetadata analysisMetadata(Map<?, ?> root, ReadContext context) {
        Map<?, ?> value = nullableObject(root, "analysis", "analysis", context);
        if (value == null) return null;
        return new AnalysisMetadata(
                uuid(value, "id", "analysis.id", context),
                string(value, "type", "analysis.type", context),
                string(value, "intentId", "analysis.intentId", context),
                string(value, "intentVersion", "analysis.intentVersion", context),
                string(value, "status", "analysis.status", context),
                instant(value, "startedAt", "analysis.startedAt", context),
                instant(value, "completedAt", "analysis.completedAt", context),
                instant(value, "createdAt", "analysis.createdAt", context)
        );
    }

    private ProjectProfileMetadata projectProfile(Map<?, ?> root, ReadContext context) {
        Map<?, ?> value = nullableObject(root, "projectProfile", "projectProfile", context);
        if (value == null) return null;
        Map<?, ?> completeness = nullableObject(value, "completeness",
                "projectProfile.completeness", context);
        return new ProjectProfileMetadata(
                uuid(value, "id", "projectProfile.id", context),
                uuid(value, "projectId", "projectProfile.projectId", context),
                uuid(value, "analysisId", "projectProfile.analysisId", context),
                string(value, "profileVersion", "projectProfile.profileVersion", context),
                string(value, "rendererVersion", "projectProfile.rendererVersion", context),
                instant(value, "generatedAt", "projectProfile.generatedAt", context),
                string(value, "requestedRevision", "projectProfile.requestedRevision", context),
                completeness == null ? null : new ProfileCompleteness(
                        string(completeness, "status", "projectProfile.completeness.status", context),
                        bool(completeness, "collectionComplete",
                                "projectProfile.completeness.collectionComplete", context),
                        bool(completeness, "truncated",
                                "projectProfile.completeness.truncated", context),
                        integer(completeness, "warningCount",
                                "projectProfile.completeness.warningCount", context),
                        integer(completeness, "errorCount",
                                "projectProfile.completeness.errorCount", context),
                        integer(completeness, "successfulCollectorCount",
                                "projectProfile.completeness.successfulCollectorCount", context),
                        integer(completeness, "collectorsWithWarningsCount",
                                "projectProfile.completeness.collectorsWithWarningsCount", context),
                        integer(completeness, "failedCollectorCount",
                                "projectProfile.completeness.failedCollectorCount", context)
                ),
                string(value, "deterministicSummary",
                        "projectProfile.deterministicSummary", context),
                integer(value, "characteristicCount",
                        "projectProfile.characteristicCount", context)
        );
    }

    private DiagnosticsMetadata diagnostics(Map<?, ?> root, ReadContext context) {
        Map<?, ?> value = nullableObject(root, "diagnostics", "diagnostics", context);
        if (value == null) return null;
        return new DiagnosticsMetadata(
                bool(value, "collectionComplete", "diagnostics.collectionComplete", context),
                bool(value, "truncated", "diagnostics.truncated", context),
                integer(value, "warningCount", "diagnostics.warningCount", context),
                integer(value, "errorCount", "diagnostics.errorCount", context)
        );
    }

    private SelectionMetadata selectionMetadata(Map<?, ?> root, ReadContext context) {
        Map<?, ?> value = nullableObject(root, "selectionMetadata", "selectionMetadata", context);
        if (value == null) return null;
        Map<?, ?> budget = nullableObject(value, "knowledgeBudget",
                "selectionMetadata.knowledgeBudget", context);
        return new SelectionMetadata(
                string(value, "selectionVersion", "selectionMetadata.selectionVersion", context),
                strings(value, "appliedRules", "selectionMetadata.appliedRules", context),
                integer(value, "selectedKnowledgeCount",
                        "selectionMetadata.selectedKnowledgeCount", context),
                integer(value, "discardedKnowledgeCount",
                        "selectionMetadata.discardedKnowledgeCount", context),
                budget == null ? null : new KnowledgeBudget(
                        integer(budget, "maximumFacts",
                                "selectionMetadata.knowledgeBudget.maximumFacts", context),
                        integer(budget, "maximumObservations",
                                "selectionMetadata.knowledgeBudget.maximumObservations", context),
                        integer(budget, "maximumInsights",
                                "selectionMetadata.knowledgeBudget.maximumInsights", context),
                        integer(budget, "maximumArchitectureKnowledge",
                                "selectionMetadata.knowledgeBudget.maximumArchitectureKnowledge", context),
                        integer(budget, "maximumRepositoryEvidence",
                                "selectionMetadata.knowledgeBudget.maximumRepositoryEvidence", context)
                ),
                string(value, "completeness", "selectionMetadata.completeness", context)
        );
    }

    private void validateEmbeddedSelection(
            Map<?, ?> root,
            SelectionMetadata metadata,
            String selectionVersion,
            String selectionDigest,
            ReadContext context
    ) {
        if (metadata != null && metadata.selectionVersion() != null
                && !metadata.selectionVersion().equals(selectionVersion)) {
            throw context.failure("selectionMetadata.selectionVersion");
        }
        String embeddedDigest = string(root, "selectionDigest", "selectionDigest", context);
        if (embeddedDigest != null && !embeddedDigest.equals(selectionDigest)) {
            throw context.failure("selectionDigest");
        }
    }

    private RepositoryContextMetadata repositoryMetadata(
            Map<?, ?> value, ReadContext context) {
        if (value == null) return null;
        return new RepositoryContextMetadata(
                string(value, "contextVersion", "repositoryContext.contextVersion", context),
                string(value, "profile", "repositoryContext.profile", context),
                strings(value, "warnings", "repositoryContext.warnings", context),
                string(value, "contextDigest", "repositoryContext.contextDigest", context)
        );
    }

    private FactsSection facts(Map<?, ?> root, ReadContext context) {
        if (!root.containsKey("selectedFacts")) {
            return new FactsSection(Availability.NOT_RECORDED, 0, List.of());
        }
        List<FactItem> items = objects(root, "selectedFacts", "selectedFacts", context,
                (item, path) -> new FactItem(
                        uuid(item, "id", path + ".id", context),
                        string(item, "type", path + ".type", context),
                        string(item, "content", path + ".content", context),
                        string(item, "source", path + ".source", context),
                        strings(item, "evidenceReferences", path + ".evidenceReferences", context),
                        instant(item, "detectedAt", path + ".detectedAt", context)
                ));
        return new FactsSection(Availability.RECORDED, items.size(), items);
    }

    private ObservationsSection observations(Map<?, ?> root, ReadContext context) {
        if (!root.containsKey("selectedObservations")) {
            return new ObservationsSection(Availability.NOT_RECORDED, 0, List.of());
        }
        List<ObservationItem> items = objects(root, "selectedObservations",
                "selectedObservations", context,
                (item, path) -> new ObservationItem(
                        uuid(item, "id", path + ".id", context),
                        string(item, "type", path + ".type", context),
                        string(item, "content", path + ".content", context),
                        string(item, "ruleId", path + ".ruleId", context),
                        string(item, "ruleVersion", path + ".ruleVersion", context),
                        uuids(item, "supportingFactIds", path + ".supportingFactIds", context),
                        instant(item, "createdAt", path + ".createdAt", context)
                ));
        return new ObservationsSection(Availability.RECORDED, items.size(), items);
    }

    private PriorInsightsSection priorInsights(Map<?, ?> root, ReadContext context) {
        if (!root.containsKey("selectedInsights")) {
            return new PriorInsightsSection(Availability.NOT_RECORDED, 0, List.of());
        }
        List<PriorInsightItem> items = objects(root, "selectedInsights", "selectedInsights",
                context, (item, path) -> new PriorInsightItem(
                        string(item, "type", path + ".type", context),
                        string(item, "severity", path + ".severity", context),
                        string(item, "title", path + ".title", context),
                        string(item, "content", path + ".content", context)
                ));
        return new PriorInsightsSection(Availability.RECORDED, items.size(), items);
    }

    private ArchitectureKnowledgeSection architectureKnowledge(
            Map<?, ?> root, ReadContext context) {
        if (!root.containsKey("existingArchitectureKnowledge")) {
            return new ArchitectureKnowledgeSection(Availability.NOT_RECORDED, 0, List.of());
        }
        List<ArchitectureKnowledgeItem> items = objects(root,
                "existingArchitectureKnowledge", "existingArchitectureKnowledge", context,
                (item, path) -> new ArchitectureKnowledgeItem(
                        uuid(item, "insightId", path + ".insightId", context),
                        uuid(item, "proposalId", path + ".proposalId", context),
                        string(item, "normalizedType", path + ".normalizedType", context),
                        string(item, "severity", path + ".severity", context),
                        string(item, "sourceType", path + ".sourceType", context),
                        string(item, "title", path + ".title", context),
                        string(item, "content", path + ".content", context),
                        string(item, "rationale", path + ".rationale", context),
                        strings(item, "evidenceReferences", path + ".evidenceReferences", context),
                        instant(item, "createdAt", path + ".createdAt", context)
                ));
        return new ArchitectureKnowledgeSection(Availability.RECORDED, items.size(), items);
    }

    private EngineeringEventsSection engineeringEvents(Map<?, ?> root, ReadContext context) {
        if (!root.containsKey("selectedEngineeringEvents")) {
            return new EngineeringEventsSection(Availability.NOT_RECORDED, 0, List.of());
        }
        List<EngineeringEventItem> items = objects(root, "selectedEngineeringEvents",
                "selectedEngineeringEvents", context,
                (item, path) -> new EngineeringEventItem(
                        uuid(item, "id", path + ".id", context),
                        string(item, "category", path + ".category", context),
                        string(item, "title", path + ".title", context),
                        string(item, "summary", path + ".summary", context),
                        uuid(item, "sourceId", path + ".sourceId", context),
                        string(item, "baseCommit", path + ".baseCommit", context),
                        string(item, "targetCommit", path + ".targetCommit", context),
                        instant(item, "occurredAt", path + ".occurredAt", context),
                        uuid(item, "proposalId", path + ".proposalId", context)
                ));
        return new EngineeringEventsSection(Availability.RECORDED, items.size(), items);
    }

    private HumanContextSection humanContext(Map<?, ?> root, ReadContext context) {
        if (!root.containsKey("selectedHumanContextInputs")) {
            return new HumanContextSection(Availability.NOT_RECORDED, 0, List.of());
        }
        List<HumanContextItem> items = objects(root, "selectedHumanContextInputs",
                "selectedHumanContextInputs", context,
                (item, path) -> new HumanContextItem(
                        uuid(item, "id", path + ".id", context),
                        string(item, "type", path + ".type", context),
                        string(item, "title", path + ".title", context),
                        string(item, "contentMarkdown", path + ".contentMarkdown", context),
                        string(item, "status", path + ".status", context),
                        instant(item, "updatedAt", path + ".updatedAt", context)
                ));
        return new HumanContextSection(Availability.RECORDED, items.size(), items);
    }

    private EvolutionContextSection evolutionContext(Map<?, ?> root, ReadContext context) {
        if (!root.containsKey("evolutionContext")) {
            return new EvolutionContextSection(Availability.NOT_RECORDED, 0, List.of());
        }
        Map<?, ?> value = nullableObject(root, "evolutionContext", "evolutionContext", context);
        if (value == null) {
            return new EvolutionContextSection(Availability.RECORDED, 0, List.of());
        }
        EvolutionContextItem item = new EvolutionContextItem(
                string(value, "contextVersion", "evolutionContext.contextVersion", context),
                uuid(value, "projectId", "evolutionContext.projectId", context),
                uuid(value, "sourceId", "evolutionContext.sourceId", context),
                string(value, "baseCommit", "evolutionContext.baseCommit", context),
                string(value, "targetCommit", "evolutionContext.targetCommit", context),
                string(value, "comparisonPolicy", "evolutionContext.comparisonPolicy", context),
                bool(value, "mergeCommit", "evolutionContext.mergeCommit", context),
                instant(value, "targetCommittedAt", "evolutionContext.targetCommittedAt", context),
                commitDiff(nullableObject(value, "commitDiff",
                        "evolutionContext.commitDiff", context), context)
        );
        return new EvolutionContextSection(Availability.RECORDED, 1, List.of(item));
    }

    private CommitDiff commitDiff(Map<?, ?> value, ReadContext context) {
        if (value == null) return null;
        Map<?, ?> statistics = nullableObject(value, "statistics",
                "evolutionContext.commitDiff.statistics", context);
        return new CommitDiff(
                uuid(value, "projectId", "evolutionContext.commitDiff.projectId", context),
                uuid(value, "repositoryId", "evolutionContext.commitDiff.repositoryId", context),
                string(value, "commitHash", "evolutionContext.commitDiff.commitHash", context),
                string(value, "firstParentHash",
                        "evolutionContext.commitDiff.firstParentHash", context),
                strings(value, "parentHashes", "evolutionContext.commitDiff.parentHashes", context),
                bool(value, "rootCommit", "evolutionContext.commitDiff.rootCommit", context),
                bool(value, "mergeCommit", "evolutionContext.commitDiff.mergeCommit", context),
                string(value, "commitMessage", "evolutionContext.commitDiff.commitMessage", context),
                instant(value, "committedAt", "evolutionContext.commitDiff.committedAt", context),
                optionalObjects(value, "changedFiles", "evolutionContext.commitDiff.changedFiles",
                        context, (item, path) -> new ChangedFile(
                                string(item, "changeType", path + ".changeType", context),
                                string(item, "oldPath", path + ".oldPath", context),
                                string(item, "newPath", path + ".newPath", context),
                                bool(item, "binary", path + ".binary", context),
                                integer(item, "insertions", path + ".insertions", context),
                                integer(item, "deletions", path + ".deletions", context),
                                string(item, "language", path + ".language", context),
                                string(item, "category", path + ".category", context),
                                bool(item, "excludedFromAnalysis",
                                        path + ".excludedFromAnalysis", context),
                                string(item, "exclusionReason", path + ".exclusionReason", context),
                                string(item, "evidenceReference",
                                        path + ".evidenceReference", context)
                        )),
                statistics == null ? null : new DiffStatistics(
                        integer(statistics, "filesChanged",
                                "evolutionContext.commitDiff.statistics.filesChanged", context),
                        integer(statistics, "insertions",
                                "evolutionContext.commitDiff.statistics.insertions", context),
                        integer(statistics, "deletions",
                                "evolutionContext.commitDiff.statistics.deletions", context),
                        integer(statistics, "binaryFiles",
                                "evolutionContext.commitDiff.statistics.binaryFiles", context)
                ),
                strings(value, "candidateAdrReferences",
                        "evolutionContext.commitDiff.candidateAdrReferences", context),
                strings(value, "candidateRoadmapReferences",
                        "evolutionContext.commitDiff.candidateRoadmapReferences", context),
                strings(value, "evidenceReferences",
                        "evolutionContext.commitDiff.evidenceReferences", context),
                bool(value, "truncated", "evolutionContext.commitDiff.truncated", context),
                strings(value, "warnings", "evolutionContext.commitDiff.warnings", context)
        );
    }

    private RepositoryEvidenceSection repositoryEvidence(
            Map<?, ?> root, Map<?, ?> repositoryContext, ReadContext context) {
        if (!root.containsKey("repositoryContext")) {
            return new RepositoryEvidenceSection(Availability.NOT_RECORDED, 0, List.of());
        }
        if (repositoryContext == null) {
            return new RepositoryEvidenceSection(Availability.RECORDED, 0, List.of());
        }
        List<RepositoryEvidenceItem> items = objects(repositoryContext, "evidence",
                "repositoryContext.evidence", context,
                (item, path) -> new RepositoryEvidenceItem(
                        string(item, "layer", path + ".layer", context),
                        string(item, "kind", path + ".kind", context),
                        string(item, "reference", path + ".reference", context),
                        string(item, "summary", path + ".summary", context),
                        instant(item, "occurredAt", path + ".occurredAt", context),
                        strings(item, "relatedReferences", path + ".relatedReferences", context),
                        repositoryContent(nullableObject(item, "content", path + ".content", context),
                                path + ".content", context),
                        repositorySymbols(nullableObject(item, "symbols", path + ".symbols", context),
                                path + ".symbols", context)
                ));
        return new RepositoryEvidenceSection(Availability.RECORDED, items.size(), items);
    }

    private RepositoryContent repositoryContent(
            Map<?, ?> value, String path, ReadContext context) {
        if (value == null) return null;
        String status = string(value, "status", path + ".status", context);
        RepositoryContentStatus typedStatus = null;
        if (status != null) {
            try {
                typedStatus = RepositoryContentStatus.valueOf(status);
            } catch (IllegalArgumentException exception) {
                throw context.failure(path + ".status");
            }
        }
        return new RepositoryContent(
                typedStatus,
                string(value, "text", path + ".text", context),
                string(value, "reason", path + ".reason", context),
                string(value, "policyId", path + ".policyId", context),
                string(value, "policyVersion", path + ".policyVersion", context),
                string(value, "revision", path + ".revision", context),
                string(value, "allocationPolicyId", path + ".allocationPolicyId", context),
                string(value, "allocationPolicyVersion",
                        path + ".allocationPolicyVersion", context),
                integer(value, "allocationRank", path + ".allocationRank", context)
        );
    }

    private RepositorySymbols repositorySymbols(
            Map<?, ?> value, String path, ReadContext context) {
        if (value == null) return null;
        return new RepositorySymbols(
                string(value, "status", path + ".status", context),
                string(value, "reason", path + ".reason", context),
                string(value, "policyId", path + ".policyId", context),
                string(value, "policyVersion", path + ".policyVersion", context),
                string(value, "extractorId", path + ".extractorId", context),
                string(value, "extractorVersion", path + ".extractorVersion", context),
                string(value, "revision", path + ".revision", context),
                integer(value, "allocationRank", path + ".allocationRank", context),
                bool(value, "truncated", path + ".truncated", context),
                integer(value, "returnedSymbolCount", path + ".returnedSymbolCount", context),
                integer(value, "availableSymbolCount", path + ".availableSymbolCount", context),
                optionalObjects(value, "declarations", path + ".declarations", context,
                        (item, itemPath) -> new SymbolDeclaration(
                                string(item, "kind", itemPath + ".kind", context),
                                string(item, "name", itemPath + ".name", context),
                                string(item, "owningType", itemPath + ".owningType", context),
                                strings(item, "modifiers", itemPath + ".modifiers", context),
                                string(item, "returnType", itemPath + ".returnType", context),
                                optionalObjects(item, "parameters", itemPath + ".parameters", context,
                                        (parameter, parameterPath) -> new SymbolParameter(
                                                string(parameter, "type",
                                                        parameterPath + ".type", context),
                                                string(parameter, "name",
                                                        parameterPath + ".name", context)
                                        )),
                                strings(item, "annotations", itemPath + ".annotations", context),
                                symbolLocation(nullableObject(item, "location",
                                        itemPath + ".location", context), itemPath + ".location", context)
                        ))
        );
    }

    private SymbolLocation symbolLocation(Map<?, ?> value, String path, ReadContext context) {
        if (value == null) return null;
        return new SymbolLocation(
                integer(value, "beginLine", path + ".beginLine", context),
                integer(value, "beginColumn", path + ".beginColumn", context),
                integer(value, "endLine", path + ".endLine", context),
                integer(value, "endColumn", path + ".endColumn", context)
        );
    }

    private void validateIdentity(
            UUID recorded, UUID expected, String path, ReadContext context) {
        if (recorded != null && !recorded.equals(expected)) {
            throw context.failure(path);
        }
    }

    private Map<?, ?> nullableObject(
            Map<?, ?> source, String key, String path, ReadContext context) {
        if (!source.containsKey(key) || source.get(key) == null) return null;
        if (!(source.get(key) instanceof Map<?, ?> value)) {
            throw context.failure(path);
        }
        return value;
    }

    private String string(Map<?, ?> source, String key, String path, ReadContext context) {
        if (!source.containsKey(key) || source.get(key) == null) return null;
        if (!(source.get(key) instanceof String value)) {
            throw context.failure(path);
        }
        return value;
    }

    private UUID uuid(Map<?, ?> source, String key, String path, ReadContext context) {
        String value = string(source, key, path, context);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw context.failure(path);
        }
    }

    private Instant instant(Map<?, ?> source, String key, String path, ReadContext context) {
        String value = string(source, key, path, context);
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw context.failure(path);
        }
    }

    private Boolean bool(Map<?, ?> source, String key, String path, ReadContext context) {
        if (!source.containsKey(key) || source.get(key) == null) return null;
        if (!(source.get(key) instanceof Boolean value)) {
            throw context.failure(path);
        }
        return value;
    }

    private Integer integer(Map<?, ?> source, String key, String path, ReadContext context) {
        if (!source.containsKey(key) || source.get(key) == null) return null;
        if (!(source.get(key) instanceof Number value)) {
            throw context.failure(path);
        }
        try {
            BigDecimal decimal = new BigDecimal(value.toString());
            return decimal.intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw context.failure(path);
        }
    }

    private List<String> strings(
            Map<?, ?> source, String key, String path, ReadContext context) {
        if (!source.containsKey(key) || source.get(key) == null) return null;
        if (!(source.get(key) instanceof List<?> values)) {
            throw context.failure(path);
        }
        List<String> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof String string)) {
                throw context.failure(path + "[" + index + "]");
            }
            result.add(string);
        }
        return List.copyOf(result);
    }

    private List<UUID> uuids(
            Map<?, ?> source, String key, String path, ReadContext context) {
        List<String> values = strings(source, key, path, context);
        if (values == null) return null;
        List<UUID> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            try {
                result.add(UUID.fromString(values.get(index)));
            } catch (IllegalArgumentException exception) {
                throw context.failure(path + "[" + index + "]");
            }
        }
        return List.copyOf(result);
    }

    private <T> List<T> objects(
            Map<?, ?> source,
            String key,
            String path,
            ReadContext context,
            BiFunction<Map<?, ?>, String, T> mapper
    ) {
        if (!source.containsKey(key) || !(source.get(key) instanceof List<?> values)) {
            throw context.failure(path);
        }
        return mapObjects(values, path, context, mapper);
    }

    private <T> List<T> optionalObjects(
            Map<?, ?> source,
            String key,
            String path,
            ReadContext context,
            BiFunction<Map<?, ?>, String, T> mapper
    ) {
        if (!source.containsKey(key) || source.get(key) == null) return null;
        if (!(source.get(key) instanceof List<?> values)) {
            throw context.failure(path);
        }
        return mapObjects(values, path, context, mapper);
    }

    private <T> List<T> mapObjects(
            List<?> values,
            String path,
            ReadContext context,
            BiFunction<Map<?, ?>, String, T> mapper
    ) {
        List<T> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            if (!(values.get(index) instanceof Map<?, ?> item)) {
                throw context.failure(path + "[" + index + "]");
            }
            result.add(mapper.apply(item, path + "[" + index + "]"));
        }
        return List.copyOf(result);
    }

    private record ReadContext(UUID taskId, String selectionVersion) {
        private HistoricalSnapshotReadException failure(String path) {
            return new HistoricalSnapshotReadException(
                    "Selected evidence snapshot read failed task=%s version=%s path=%s"
                            .formatted(taskId, selectionVersion, path));
        }
    }

    public static final class HistoricalSnapshotReadException extends IllegalStateException {
        private HistoricalSnapshotReadException(String message) {
            super(message);
        }
    }
}
