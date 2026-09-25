package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.*;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.reference.AiReferenceRegistryFactory;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.knowledge.selection.KnowledgeSelectionService;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledgePromptProjectionService;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.storycontextanalysis.history.HistoricalKnowledgeCandidateService;
import com.hopeful117.devlogai.storycontextanalysis.history.HistoricalKnowledgeCandidateService.HistoricalKnowledgeCandidates;
import com.hopeful117.devlogai.storycontextanalysis.repository.StoryContextAnalysisRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.exception.SourceSelectionException;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.collection.workspace.ResolvedSourceRevision;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryRevisionScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyzeStoryContextUseCase {

    private static final String INTENT_ID = "engineering-story-context-analysis";
    private static final String INTENT_VERSION = "v1";

    private final ProjectRepository projectRepository;
    private final EngineeringStoryRepository storyRepository;
    private final EngineeringContextFacade engineeringContextFacade;
    private final IntentCatalog intentCatalog;
    private final AiTaskService aiTaskService;
    private final AIEngineClient aiEngineClient;
    private final AiTaskRepository aiTaskRepository;
    private final StoryContextAnalysisRepository storyContextAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final KnowledgeSelectionService knowledgeSelectionService;
    private final SelectedKnowledgePromptProjectionService promptProjectionService;
    private final ProjectProfileService projectProfileService;
    private final AnalysisContextService analysisContextService;
    private final AnalysisRepository analysisRepository;
    private final AnalysisExecutionDiagnosticRepository diagnosticRepository;
    private final HistoricalKnowledgeCandidateService historicalKnowledgeCandidateService;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private static final TaskSnapshotEvidenceResolver evidenceResolver = new TaskSnapshotEvidenceResolver();

    public UUID execute(
            String projectSlug,
            UUID storyId,
            List<String> files,
            Map<String, Object> guidance
    ) {
        Project project = projectRepository.findBySlug(projectSlug)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectSlug));
        EngineeringStory story = storyRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("EngineeringStory", storyId));
        if (!story.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("Story does not belong to project");
        }

        var intentDef = intentCatalog.resolve(INTENT_ID, INTENT_VERSION);

        Analysis executionAnalysis = analysisRepository.save(Analysis.builder()
                .project(project)
                .type(AnalysisType.STORY_CONTEXT_ANALYSIS)
                .intentId(INTENT_ID)
                .intentVersion(INTENT_VERSION)
                .status(AnalysisStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build());

        CanonicalEngineeringContext canonicalContext = engineeringContextFacade.getCanonicalEngineeringContext(
                projectSlug,
                INTENT_ID,
                files != null ? files : List.of(),
                storyId
        );
        // Compatibility with older facade test doubles/implementations during the additive migration.
        if (canonicalContext == null) {
            EngineeringContext legacy = engineeringContextFacade.getEngineeringContext(
                    projectSlug, INTENT_ID, files != null ? files : List.of(), storyId);
            canonicalContext = new CanonicalEngineeringContext(legacy,
                    legacy.metadata().contextDigest(), "engineering-context-v1", storyId,
                    legacy.evidence().stream().map(e -> e.reference())
                            .filter(Objects::nonNull).toList());
        }

        UserGuidance userGuidance = mapGuidance(guidance);
        Map<String, Object> selectedKnowledgeSnapshot = StoryContextAgentProjection.project(
                canonicalContext, story, objectMapper);

        // The Core canonical construction owns the digest; this use case only propagates it.
        String contextDigest = canonicalContext.contextDigest();
        if (contextDigest == null || contextDigest.isBlank()) {
            throw new IllegalStateException("Canonical context digest is required before submission");
        }

        // Authorize only the canonical repository evidence projected into this prompt.
        Map<String, Object> groundingContract = buildGroundingContract(canonicalContext, projectSlug, guidance);

        // Create AiTask with selected knowledge and grounding contract
        AiTask aiTask = aiTaskService.createForStoryContextAnalysisEntity(
                executionAnalysis.getId(),
                AiTaskType.STORY_CONTEXT_ANALYSIS,
                INTENT_ID,
                INTENT_VERSION,
                intentDef.promptTemplate(),
                selectedKnowledgeSnapshot,
                contextDigest,
                groundingContract,
                guidance,
                new com.hopeful117.devlogai.ai.reference.AiReferenceRegistry(List.of())
        );
        String projectionDigest = digestProjection(selectedKnowledgeSnapshot);
        aiTask.setContextDigest(contextDigest);
        aiTask.setSelectionDigest(null);
        aiTask.setProjectionDigest(projectionDigest);
        // Freeze the complete execution contract before SUBMITTED. Callback data is
        // an echo only and must never be able to complete or repair this snapshot.
        Map<String, Object> taskContextSnapshot = buildExecutionSnapshot(
                aiTask, canonicalContext, selectedKnowledgeSnapshot, projectionDigest,
                projectSlug, storyId, files, guidance, groundingContract);
        aiTask.setContextSnapshot(taskContextSnapshot);
        aiTaskRepository.save(aiTask);

        // Submit task before sending to Python (ensures SUBMITTED status)
        aiTaskService.submit(aiTask.getId(), new com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest(null));

        // Build PromptRequest with selected knowledge and grounding contract
        Map<String, Object> promptMetadata = new LinkedHashMap<>();
        promptMetadata.put("projectSlug", projectSlug);
        promptMetadata.put("storyId", storyId.toString());
        promptMetadata.put("contextDigest", contextDigest);
        if (aiTask.getSelectionDigest() != null) promptMetadata.put("selectionDigest", aiTask.getSelectionDigest());
        promptMetadata.put("projectionDigest", projectionDigest);
        promptMetadata.put("contractVersion", "story-context-projection-v1");
        PromptRequest promptRequest = new PromptRequest(
                UUID.randomUUID(),
                aiTask.getCorrelationId(),
                project.getId(),
                aiTask.getId(),
                AiTaskType.STORY_CONTEXT_ANALYSIS,
                intentDef,
                userGuidance,
                selectedKnowledgeSnapshot,
                intentDef.outputSchema(),
                groundingContract,
                promptMetadata
        );

        aiEngineClient.submit(promptRequest);

        return aiTask.getId();
    }

    private Map<String, Object> buildExecutionSnapshot(
            AiTask task, CanonicalEngineeringContext canonical, Map<String, Object> projection,
            String projectionDigest, String projectSlug, UUID storyId, List<String> files,
            Map<String, Object> guidance, Map<String, Object> groundingContract) {
        Map<String, Object> snapshot = new LinkedHashMap<>(
                task.getContextSnapshot() == null ? Map.of() : task.getContextSnapshot());
        snapshot.put("contextDigest", canonical.contextDigest());
        snapshot.put("projectionDigest", projectionDigest);
        snapshot.put("selectionDigest", null); // SCA has no compatibility selection
        snapshot.put("contextVersion", canonical.contextVersion());
        snapshot.put("projectionVersion", "story-context-projection-v1");
        snapshot.put("scope", Map.of("projectSlug", projectSlug, "storyId", storyId.toString(),
                "files", files == null ? List.of() : List.copyOf(files)));
        snapshot.put("requestEcho", valueMap(canonical.requestEcho()));
        snapshot.put("freshness", canonical.freshness());
        snapshot.put("contextFreshness", canonical.freshness()); // legacy compatibility alias
        snapshot.put("revisions", revisions(canonical));
        snapshot.put("policy", Map.of("contractVersion", "story-context-projection-v1",
                "selection", "CORE_CANONICAL_ONLY", "retrieval", "NONE",
                "allowListVersion", "typed-grounding-v1"));
        snapshot.put("budgets", budgets(canonical));
        snapshot.put("accounting", canonical.accounting());
        snapshot.put("truncation", truncation(canonical));
        snapshot.put("warnings", warnings(canonical));
        snapshot.put("referenceMapping", task.getAiReferenceMappingSnapshot() == null
                ? Map.of("contractVersion", "AI_REFERENCE_MAPPING_V1", "mappingDigest", "EMPTY",
                "bindings", List.of(), "architectureKnowledgeReferences", List.of())
                : task.getAiReferenceMappingSnapshot());
        snapshot.put("allowListVersion", "typed-grounding-v1");
        snapshot.put("groundingContract", groundingContract);
        snapshot.put("canonicalContext", valueMap(canonical));
        snapshot.put("projection", projection); // exact payload whose digest is persisted above
        snapshot.put("guidance", guidance == null ? Map.of() : new LinkedHashMap<>(guidance));
        return snapshot;
    }

    private Map<String, Object> revisions(CanonicalEngineeringContext canonical) {
        Map<String, Object> revisions = new LinkedHashMap<>();
        revisions.put("freshness", canonical.freshness());
        if (canonical.repositoryContext() != null) {
            revisions.put("evidence", canonical.repositoryContext().evidence().stream()
                    .map(e -> e.content() == null ? null : e.content().revision())
                    .filter(Objects::nonNull).distinct().toList());
        } else revisions.put("evidence", List.of());
        return revisions;
    }

    private Map<String, Object> budgets(CanonicalEngineeringContext canonical) {
        if (canonical.repositoryContext() == null || canonical.repositoryContext().budget() == null) return Map.of();
        return objectMapper.convertValue(canonical.repositoryContext().budget(), Map.class);
    }

    private Map<String, Object> truncation(CanonicalEngineeringContext canonical) {
        RepositoryContext context = canonical.repositoryContext();
        return context == null ? Map.of() : Map.of("truncated", context.truncated(),
                "discardedCount", context.discardedCount(), "candidateCount", context.candidateCount());
    }

    private List<String> warnings(CanonicalEngineeringContext canonical) {
        return canonical.repositoryContext() == null ? List.of() : canonical.repositoryContext().warnings();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> valueMap(Object value) {
        return value == null ? Map.of() : objectMapper.convertValue(value, Map.class);
    }

    private void projectBaselineDiagnostics(Analysis executionAnalysis, UUID baselineAnalysisId) {
        diagnosticRepository.findById(baselineAnalysisId).ifPresent(baseline ->
                diagnosticRepository.save(AnalysisExecutionDiagnostic.builder()
                        .analysis(executionAnalysis)
                        .sourceCount(baseline.getSourceCount())
                        .factCount(baseline.getFactCount())
                        .observationCount(baseline.getObservationCount())
                        .warningCount(baseline.getWarningCount())
                        .errorCount(baseline.getErrorCount())
                        .collectorCount(baseline.getCollectorCount())
                        .successfulCollectors(baseline.getSuccessfulCollectors())
                        .collectorsWithWarnings(baseline.getCollectorsWithWarnings())
                        .failedCollectors(baseline.getFailedCollectors())
                        .collectionComplete(baseline.isCollectionComplete())
                        .truncated(baseline.isTruncated())
                        .resolvedRevisions(baseline.getResolvedRevisions())
                        .collectorVersions(baseline.getCollectorVersions())
                        .collectedAt(baseline.getCollectedAt())
                        .build()));
    }

    private Map<String, Object> buildGroundingContract(
            RepositoryContext context,
            Map<String, Object> guidance
    ) {
        Set<String> allowedRefs = new LinkedHashSet<>();
        for (var evidence : context.evidence()) {
            // Use canonical reference for grounding (RepositoryEvidence.reference)
            if (evidence.reference() != null) {
                allowedRefs.add(evidence.reference());
            }
        }
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("allowedEvidenceReferences", new ArrayList<>(allowedRefs));
        boolean causalAnswerRequired = guidance != null
                && Boolean.TRUE.equals(guidance.get("causalAnswerRequired"));
        contract.put("causalAnswerRequired", causalAnswerRequired);
        if (causalAnswerRequired && guidance != null && guidance.get("causalRelationship") instanceof Map<?, ?> relationship) {
            Object source = relationship.get("source");
            Object target = relationship.get("target");
            if (source instanceof String sourceValue && !sourceValue.isBlank()
                    && target instanceof String targetValue && !targetValue.isBlank()) {
                String relationAsked = guidance.get("relationAsked") instanceof String value && !value.isBlank()
                        ? value : "CAUSAL";
                contract.put("causalContractVersion", "V2");
                contract.put("causalQuestion", Map.of(
                        "source", sourceValue,
                        "target", targetValue,
                        "relationAsked", relationAsked,
                        "answerRequired", true));
                // Keep the old shape in the snapshot for historical diagnostics only.
                contract.put("causalRelationship", Map.of("source", sourceValue, "target", targetValue));
            }
        }
        if (causalAnswerRequired && !contract.containsKey("causalQuestion")) {
            throw new IllegalArgumentException("causalAnswerRequired requires a valid causalRelationship");
        }
        return contract;
    }

    private Map<String, Object> buildGroundingContract(
            CanonicalEngineeringContext canonical, String projectSlug, Map<String, Object> guidance) {
        Map<String, Object> contract = new LinkedHashMap<>();
        List<EvidenceRef> typed = canonical.authorizedReferences();
        String revision = canonical.repositoryContext() == null ? "UNSPECIFIED" : canonical.repositoryContext().evidence().stream()
                .map(e -> e.content() == null ? null : e.content().revision())
                .filter(v -> v != null && !v.isBlank()).findFirst().orElse("UNSPECIFIED");
        contract.put("allowedGroundingReferences", typed.stream().map(ref -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "REPOSITORY_EVIDENCE"); entry.put("ref", ref.reference());
            entry.put("coreReference", ref.reference()); entry.put("taskReference", ref.reference());
            entry.put("scope", Map.of("project", projectSlug, "revision", revision));
            return entry;
        }).toList());
        // Legacy shape is retained only for old Python consumers and is not authoritative.
        contract.put("allowedEvidenceReferences", typed.stream().map(EvidenceRef::reference)
                .collect(java.util.stream.Collectors.toUnmodifiableList()));
        boolean required = guidance != null && Boolean.TRUE.equals(guidance.get("causalAnswerRequired"));
        contract.put("causalAnswerRequired", required);
        if (required && guidance != null && guidance.get("causalRelationship") instanceof Map<?, ?> relationship) {
            Object source = relationship.get("source"), target = relationship.get("target");
            if (source instanceof String s && !s.isBlank() && target instanceof String t && !t.isBlank()) {
                contract.put("causalContractVersion", "V2");
                contract.put("causalQuestion", Map.of("source", s, "target", t,
                        "relationAsked", guidance.getOrDefault("relationAsked", "CAUSAL"), "answerRequired", true));
                contract.put("causalRelationship", Map.of("source", s, "target", t));
            }
        }
        if (required && !contract.containsKey("causalQuestion"))
            throw new IllegalArgumentException("causalAnswerRequired requires a valid causalRelationship");
        return contract;
    }

    private String digestProjection(Map<String, Object> projection) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("version", "sca-projection-digest-v1"); envelope.put("projection", projection);
        return sha256(canonicalJson(envelope));
    }

    private String sha256(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to digest canonical value", e);
        }
    }

    private String canonicalJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().sorted(java.util.Comparator.comparing(e -> String.valueOf(e.getKey())))
                    .map(e -> quote(String.valueOf(e.getKey())) + ":" + canonicalJson(e.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> values) {
            return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                    .map(this::canonicalJson).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        if (value.getClass().isRecord()) return canonicalJson(objectMapper.convertValue(value, Map.class));
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Unable to canonicalize digest value", e); }
    }

    private String quote(String value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureFreshnessSnapshot(EngineeringContext context) {
        if (context.metadata() == null || context.metadata().freshness() == null) {
            return null;
        }
        return objectMapper.convertValue(context.metadata().freshness(), Map.class);
    }

    /**
     * Adapts the baseline AnalysisContext for SCA by:
     * - Keeping Facts/Observations from baseline
     * - Setting SCA intent in analysis snapshot
     * - Including only the current Story
     * - Preserving other context (Insights, Events, Relations, Human Inputs)
     */
    private AnalysisContext adaptContextForSCA(
            AnalysisContext baselineContext,
            Analysis executionAnalysis,
            EngineeringStory story,
            HistoricalKnowledgeCandidates historicalCandidates
    ) {
        // Create SCA analysis snapshot with correct intent
        AnalysisContext.AnalysisSnapshot scaAnalysisSnapshot = new AnalysisContext.AnalysisSnapshot(
                executionAnalysis.getId(),
                executionAnalysis.getType(),
                INTENT_ID,
                INTENT_VERSION,
                executionAnalysis.getStatus(),
                executionAnalysis.getStartedAt(),
                executionAnalysis.getCompletedAt(),
                executionAnalysis.getCreatedAt()
        );

        // Include only the current story
        var currentStorySnapshot = new com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringStorySnapshot(
                story.getId(),
                story.getProject().getId(),
                story.getStoryNumber(),
                story.getTitle(),
                story.getStatus().name(),
                story.getStoryPath(),
                story.getBaseCommit(),
                story.getTargetCommit(),
                story.getCreatedAt(),
                story.getCompletedAt()
        );

        return new AnalysisContext(
                baselineContext.project(),
                scaAnalysisSnapshot,
                baselineContext.projectProfile(),
                mergeById(baselineContext.facts(), historicalCandidates.facts(),
                        AnalysisContext.FactSnapshot::id),
                mergeById(baselineContext.observations(), historicalCandidates.observations(),
                        AnalysisContext.ObservationSnapshot::id),
                baselineContext.recentKnowledgeEvents(),
                baselineContext.relatedAnalyses(),
                baselineContext.architectureArtifacts(),
                baselineContext.relatedDecisions(),
                baselineContext.recentMilestones(),
                baselineContext.validatedProposals(),
                baselineContext.evolutionContext(),
                baselineContext.validatedEngineeringEvents(),
                baselineContext.openChallenges(),
                baselineContext.knowledgeRelations(),
                List.of(currentStorySnapshot),
                baselineContext.humanContextInputs(),
                baselineContext.engineeringRelationships()
        );
    }

    private <T> List<T> mergeById(
            List<T> baseline,
            List<T> historical,
            java.util.function.Function<T, UUID> id
    ) {
        LinkedHashMap<UUID, T> merged = new LinkedHashMap<>();
        baseline.forEach(value -> merged.putIfAbsent(id.apply(value), value));
        historical.forEach(value -> merged.putIfAbsent(id.apply(value), value));
        return List.copyOf(merged.values());
    }

    /**
     * Creates a minimal SCA context when no baseline analysis exists.
     * KnowledgeSelectionService will return empty selections but with valid structure.
     */
    private AnalysisContext createMinimalSCAContext(
            Project project,
            Analysis executionAnalysis,
            EngineeringContext engineeringContext,
            EngineeringStory story,
            IntentDefinition intentDef,
            Map<String, Object> guidance
    ) {
        var projectSnapshot = new AnalysisContext.ProjectSnapshot(
                project.getId(), project.getName(), project.getSlug(), null, project.getStatus()
        );

        var analysisSnapshot = new AnalysisContext.AnalysisSnapshot(
                executionAnalysis.getId(),
                executionAnalysis.getType(),
                INTENT_ID,
                INTENT_VERSION,
                executionAnalysis.getStatus(),
                executionAnalysis.getStartedAt(),
                executionAnalysis.getCompletedAt(),
                executionAnalysis.getCreatedAt()
        );

        var currentStorySnapshot = new com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringStorySnapshot(
                story.getId(),
                story.getProject().getId(),
                story.getStoryNumber(),
                story.getTitle(),
                story.getStatus().name(),
                story.getStoryPath(),
                story.getBaseCommit(),
                story.getTargetCommit(),
                story.getCreatedAt(),
                story.getCompletedAt()
        );

        // Get project profile for context
        ProjectProfileResponse profile = projectProfileService.getLatestByProject(project.getId());

        UserGuidance userGuidance = mapGuidance(guidance);

        return new AnalysisContext(
                projectSnapshot,
                analysisSnapshot,
                profile,
                List.of(), // facts
                List.of(), // observations
                List.of(), // recentKnowledgeEvents
                List.of(), // relatedAnalyses
                List.of(), // architectureArtifacts
                List.of(), // relatedDecisions
                List.of(), // recentMilestones
                List.of(), // validatedProposals
                null, // evolutionContext
                List.of(), // validatedEngineeringEvents
                List.of(), // openChallenges
                List.of(), // knowledgeRelations
                List.of(currentStorySnapshot),
                 List.of(), // humanContextInputs
                 List.of() // engineeringRelationships
        );
    }

    private UserGuidance mapGuidance(Map<String, Object> guidance) {
        if (guidance == null || guidance.isEmpty()) {
            return null;
        }
        String focus = (String) guidance.get("focus");
        @SuppressWarnings("unchecked")
        List<String> priorities = (List<String>) guidance.getOrDefault("priorities", List.of());
        @SuppressWarnings("unchecked")
        List<String> questions = (List<String>) guidance.getOrDefault("questions", List.of());
        String outputContext = (String) guidance.get("outputContext");
        String perspective = (String) guidance.get("perspective");

        return new UserGuidance(
                focus,
                "kiko",
                perspective,
                outputContext,
                INTENT_ID,
                priorities
        );
    }

    /**
     * Resolves a single immutable RepositoryRevisionScope per SCA execution
     * (ADR-063 §42.2, §42.4).
     *
     * Resolution order:
     * 1. EngineeringStory.targetCommit (if non-null)
     * 2. Analysis.targetRevision (from baseline Analysis)
     * 3. Source.currentRevision (latest known)
     * 4. HEAD (fallback via WorkspaceManager)
     */
    private RepositoryRevisionScope resolveRevisionScope(
            Project project,
            EngineeringStory story,
            UUID baselineAnalysisId
    ) {
        List<Source> sources = sourceRepository
                .findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId());
        if (sources.isEmpty()) {
            throw new SourceSelectionException(
                    SourceSelectionException.Reason.SOURCE_UNAVAILABLE,
                    project.getId(),
                    "No active source found for project " + project.getId());
        }
        if (sources.size() > 1) {
            throw new SourceSelectionException(
                    SourceSelectionException.Reason.AMBIGUOUS_SOURCE,
                    project.getId(),
                    "Repository source selection is ambiguous for project "
                            + project.getId() + "; found " + sources.size() + " active sources");
        }
        Source source = sources.getFirst();

        String targetRevision = null;
        String revisionSource = null;

        // 1. Story.targetCommit
        if (story.getTargetCommit() != null && !story.getTargetCommit().isBlank()) {
            targetRevision = story.getTargetCommit();
            revisionSource = RepositoryRevisionScope.SOURCE_STORY_TARGET;
        }

        // 2. Analysis.targetRevision
        if (targetRevision == null && baselineAnalysisId != null) {
            Optional<Analysis> analysisOpt = analysisRepository.findById(baselineAnalysisId);
            if (analysisOpt.isPresent()) {
                Analysis analysis = analysisOpt.get();
                if (analysis.getTargetRevision() != null && !analysis.getTargetRevision().isBlank()) {
                    targetRevision = analysis.getTargetRevision();
                    revisionSource = RepositoryRevisionScope.SOURCE_ANALYSIS_TARGET;
                }
            }
        }

        // 3. Source.currentRevision / 4. HEAD
        if (targetRevision == null) {
            try {
                ResolvedSourceRevision resolved = workspaceManager.resolveCurrentRevision(source);
                targetRevision = resolved.resolvedRevision();
                revisionSource = RepositoryRevisionScope.SOURCE_CURRENT_REVISION;
            } catch (Exception e) {
                log.warn("Failed to resolve current revision for source {}: {}",
                        source.getId(), e.getMessage());
                // Fallback: synchronize to HEAD
                try {
                    var workspace = workspaceManager.synchronize(source, null);
                    targetRevision = workspace.resolvedRevision();
                    revisionSource = RepositoryRevisionScope.SOURCE_HEAD;
                } catch (Exception ex) {
                    throw new IllegalStateException(
                            "Unable to resolve repository revision for source " + source.getId(), ex);
                }
            }
        }

        // Validate revision exists by synchronizing workspace
        var workspace = workspaceManager.synchronize(source, targetRevision);
        return new RepositoryRevisionScope(
                project.getId(),
                source.getId(),
                workspace.resolvedRevision(),
                workspace.path(),
                revisionSource
        );
    }

    @Transactional
    public void handleCallback(UUID correlationId, AiTaskResultRequest request) {
        AiTask task = aiTaskRepository.findByCorrelationIdForUpdate(correlationId)
                .orElseThrow(() -> new EntityNotFoundException("AI task correlation", correlationId));

        if (task.getStatus().isTerminal()) {
            log.info("Duplicate callback for completed task correlationId={}", correlationId);
            return;
        }

        if (request.status() == AiTaskResultStatus.FAILED) {
            // Failed callbacks carry no result, but their metadata is still an
            // identity assertion. Validate it before changing task state.
            validateCoreIssuedIdentities(task, request.promptExecution());
            task.setStatus(AiTaskStatus.FAILED);
            task.setFailureCode(request.error().code());
            task.setFailureMessage(request.error().message());
            task.setCompletedAt(request.completedAt());
            aiTaskRepository.save(task);
            return;
        }

        validateCoreIssuedIdentities(task, request.promptExecution());

        var analysisResult = request.analysisResult();
        if (analysisResult == null) {
            throw new IllegalStateException("Story Context Analysis callback must include analysisResult");
        }

        // Authoritative Java validation of AI output
        analysisResult = validateStoryContextAnalysisResult(analysisResult, task);

        UUID storyId = UUID.fromString(
                task.getContextSnapshot().get("storyId").toString()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> contextFreshness = task.getContextSnapshot() != null
                ? (Map<String, Object>) task.getContextSnapshot().get("contextFreshness")
                : null;

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .story(storyRepository.findById(storyId).orElseThrow())
                .aiTask(task)
                .analysisSnapshot(objectMapper.convertValue(analysisResult, Map.class))
                .contextDigest(task.getContextDigest())
                .promptExecutionMetadata(objectMapper.convertValue(request.promptExecution(), Map.class))
                .contextFreshness(contextFreshness)
                .build();

        storyContextAnalysisRepository.save(analysis);

        task.setStatus(AiTaskStatus.COMPLETED);
        task.setCompletedAt(request.completedAt());
        task.setPromptVersion(request.promptExecution().promptVersion());
        task.setProvider(request.promptExecution().provider());
        task.setModelIdentifier(request.promptExecution().modelIdentifier());
        task.setPromptContentDigest(request.promptExecution().promptContentDigest());
        // Core-issued identities remain authoritative; Python metadata is only an echo.
        task.setContextDigest(task.getContextDigest());
        task.setProjectionDigest(task.getProjectionDigest());
        aiTaskRepository.save(task);
    }

    public void validateCoreIssuedIdentities(AiTask task, PromptExecutionMetadata metadata) {
        if (metadata == null) {
            throw new IllegalStateException("Story Context Analysis callback is missing prompt identities");
        }
        Map<String, Object> snapshot = task.getContextSnapshot() == null ? Map.of() : task.getContextSnapshot();
        String expectedContext = task.getContextDigest();
        String expectedProjection = task.getProjectionDigest();
        String expectedSelection = task.getSelectionDigest();
        if (expectedProjection == null || !expectedProjection.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Story Context Analysis task is missing a valid projection digest");
        }
        if (metadata.projectionDigest() == null
                || !metadata.projectionDigest().matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Story Context Analysis callback is missing a valid projection digest");
        }
        if (!Objects.equals(expectedContext, metadata.contextDigest())) {
            throw new IllegalStateException("Context digest mismatch in callback");
        }
        if (!Objects.equals(expectedProjection, metadata.projectionDigest())) {
            throw new IllegalStateException("Projection digest mismatch in callback");
        }
        if (!Objects.equals(expectedSelection, metadata.selectionDigest())) {
            throw new IllegalStateException("Selection digest mismatch in callback");
        }
        boolean hasIdentitySnapshot = snapshot.containsKey("contextDigest")
                || snapshot.containsKey("projectionDigest") || snapshot.containsKey("selectionDigest");
        if (hasIdentitySnapshot && (!Objects.equals(expectedContext, snapshot.get("contextDigest"))
                || !Objects.equals(expectedProjection, snapshot.get("projectionDigest"))
                || !Objects.equals(expectedSelection, snapshot.get("selectionDigest")))) {
            throw new IllegalStateException("AI task snapshot identity is incomplete or inconsistent");
        }
    }

    /**
     * Authoritative Java validation of Story Context Analysis result.
     * Validates grounding, trust, relationships, classification, and digest consistency.
     * Per Story 0112 D14 and ADR-067: Java/Core is sole grounding authority.
     */
    static StoryContextAnalysisResult validateStoryContextAnalysisResult(StoryContextAnalysisResult result, AiTask task) {
        // Extract grounding contract from task context
        @SuppressWarnings("unchecked")
        Map<String, Object> groundingContract = task.getContextSnapshot() != null
                ? (Map<String, Object>) task.getContextSnapshot().get("groundingContract")
                : Map.of();

        Set<String> allowedRefSet = allowedGroundingReferences(groundingContract);

        // Validate context digest consistency
        String expectedDigest = task.getContextDigest();
        String actualDigest = result.provenance().contextDigest();
        if (expectedDigest != null && !expectedDigest.equals(actualDigest)) {
            throw new IllegalStateException("Context digest mismatch: expected " + expectedDigest + ", got " + actualDigest);
        }

        // Validate all finding types that have GroundingMetadata
        validateGroundedFindings(result.architectureFindings(), allowedRefSet, true);
        validateGroundedFindings(result.decisionFindings(), allowedRefSet, true);
        validateGroundedFindings(result.evidenceFindings(), allowedRefSet, false);
        validateGroundedFindings(result.historicalContext(), allowedRefSet, true);
        validateGroundedFindings(result.constraintFindings(), allowedRefSet, false);
        validateGroundedFindings(result.impactedComponentFindings(), allowedRefSet, true);
        if ("V2".equals(groundingContract.get("causalContractVersion"))) {
            result = validateV2CausalAssessment(result, task, groundingContract);
        } else {
            validateCausalClaims(result.causalClaims(), allowedRefSet, groundingContract);
        }

        // Validate uncertainties
        for (StoryContextAnalysisResult.Uncertainty uncertainty : result.uncertainties()) {
            for (EvidenceRef evidenceRef : uncertainty.relatedEvidence()) {
                if (!allowedRefSet.contains(evidenceRef.reference())) {
                    throw new IllegalStateException(
                            "Uncertainty references unauthorized evidence: " + evidenceRef.reference()
                    );
                }
            }
        }

        // Validate output classification
        for (StoryContextAnalysisResult.OutputClassification.ClassificationEntry entry : result.outputClassification().entries()) {
            if (!Set.of("FACTUAL_EXTRACTION", "AI_INTERPRETATION", "RECOMMENDATION").contains(entry.classification().name())) {
                throw new IllegalStateException("Invalid output classification: " + entry.classification());
            }
        }

        // Validate confidence
        if (!Set.of("HIGH", "MEDIUM", "LOW").contains(result.confidence().name())) {
            throw new IllegalStateException("Invalid confidence level: " + result.confidence());
        }

        // Forbidden outputs check: no proposals should be generated for this intent
        // (Story 0112: ValidatableProposal is forbidden in V1)
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> allowedGroundingReferences(Map<String, Object> groundingContract) {
        Object typed = groundingContract.get("allowedGroundingReferences");
        if (typed instanceof List<?> entries) {
            Set<String> refs = new LinkedHashSet<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    throw new IllegalStateException("Typed grounding reference must be an object");
                }
                Object type = map.get("type");
                Object ref = map.get("ref");
                Object scope = map.get("scope");
                if (!"REPOSITORY_EVIDENCE".equals(type) || !(ref instanceof String value)
                        || value.isBlank() || !(scope instanceof Map<?, ?> scopeMap)
                        || !(scopeMap.get("project") instanceof String project) || project.isBlank()
                        || !(scopeMap.get("revision") instanceof String revision) || revision.isBlank()
                        || !Objects.equals(ref, map.get("coreReference"))
                        || !Objects.equals(ref, map.get("taskReference"))) {
                    throw new IllegalStateException("Invalid typed grounding reference");
                }
                refs.add(value);
            }
            return refs;
        }
        Object legacy = groundingContract.getOrDefault("allowedEvidenceReferences", List.of());
        if (!(legacy instanceof List<?> values)) {
            throw new IllegalStateException("Grounding allow-list is missing");
        }
        return values.stream().filter(String.class::isInstance).map(String.class::cast)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked")
    private static StoryContextAnalysisResult validateV2CausalAssessment(
            StoryContextAnalysisResult result,
            AiTask task,
            Map<String, Object> groundingContract
    ) {
        boolean required = Boolean.TRUE.equals(groundingContract.get("causalAnswerRequired"));
        StoryContextAnalysisResult.CausalAssessment assessment = result.causalAssessment();
        if (required && assessment == null) {
            throw new IllegalStateException("causal answer is required; causalAssessment must not be null");
        }
        if (assessment == null) return result;
        StoryContextAnalysisResult.CausalQuestion expected = causalQuestion(groundingContract);
        if (!sameQuestion(expected, assessment.question())) {
            throw new IllegalStateException("causalAssessment question does not match the Core-owned CausalQuestion");
        }
        if (!result.causalClaims().isEmpty()) {
            throw new IllegalStateException("V2 causal output must not contain legacy causalClaims");
        }
        StoryContextAnalysisResult.CausalAssessment bound = evidenceResolver.bind(
                assessment, task);
        validateV2Admissibility(bound);
        return new StoryContextAnalysisResult(
                result.objectiveUnderstanding(), result.architectureFindings(), result.decisionFindings(),
                result.evidenceFindings(), result.historicalContext(), result.constraintFindings(),
                result.impactedComponentFindings(), result.uncertainties(), result.missingInformation(),
                result.implementationQuestions(), result.confidence(), result.provenance(),
                result.outputClassification(), List.of(), bound);
    }

    @SuppressWarnings("unchecked")
    private static StoryContextAnalysisResult.CausalQuestion causalQuestion(Map<String, Object> contract) {
        Object raw = contract.get("causalQuestion");
        if (!(raw instanceof Map<?, ?> question)) {
            throw new IllegalStateException("V2 causalQuestion is missing");
        }
        Object source = question.get("source");
        Object target = question.get("target");
        Object relation = question.get("relationAsked");
        Object answerRequired = question.get("answerRequired");
        if (!(source instanceof String sourceValue) || !(target instanceof String targetValue)
                || !(relation instanceof String relationValue)) {
            throw new IllegalStateException("V2 causalQuestion is malformed");
        }
        return new StoryContextAnalysisResult.CausalQuestion(sourceValue, targetValue, relationValue,
                Boolean.TRUE.equals(answerRequired));
    }

    private static boolean sameQuestion(
            StoryContextAnalysisResult.CausalQuestion expected,
            StoryContextAnalysisResult.CausalQuestion actual
    ) {
        return expected.source().equals(actual.source())
                && expected.target().equals(actual.target())
                && expected.relationAsked().equals(actual.relationAsked())
                && expected.answerRequired() == actual.answerRequired();
    }

    private static void validateV2Admissibility(StoryContextAnalysisResult.CausalAssessment assessment) {
        var assertions = assessment.evidenceAssertions();
        if (assessment.classification() == StoryContextAnalysisResult.CausalClassification.EXPLICITLY_DOCUMENTED
                && assertions.stream().noneMatch(assertion ->
                assertion.assertionRole() == EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT)) {
            throw new IllegalStateException("EXPLICITLY_DOCUMENTED requires an inspectable direct assertion");
        }
        if (assessment.classification() == StoryContextAnalysisResult.CausalClassification.STRONGLY_SUPPORTED) {
            long distinctReferences = assertions.stream().map(assertion -> assertion.evidenceReference().reference())
                    .distinct().count();
            long distinctDigests = assertions.stream().map(StoryContextAnalysisResult.EvidenceAssertion::resolvedContentDigest)
                    .distinct().count();
            if (distinctReferences < 2 || distinctDigests < 2) {
                throw new IllegalStateException("STRONGLY_SUPPORTED requires two distinct resolved assertions");
            }
        }
    }

    private static void validateCausalClaims(
            List<StoryContextAnalysisResult.CausalClaim> claims,
            Set<String> allowedRefs,
            Map<String, Object> groundingContract
    ) {
        if (Boolean.TRUE.equals(groundingContract.get("causalAnswerRequired")) && claims.isEmpty()) {
            throw new IllegalStateException("causal answer is required; causalClaims must not be empty");
        }
        Object relationshipValue = groundingContract.get("causalRelationship");
        if (Boolean.TRUE.equals(groundingContract.get("causalAnswerRequired"))
                && relationshipValue instanceof Map<?, ?> relationship
                && relationship.get("source") instanceof String source
                && relationship.get("target") instanceof String target
                && (claims.size() != 1
                || !source.equals(claims.getFirst().source())
                || !target.equals(claims.getFirst().target()))) {
            throw new IllegalStateException("causal answer must contain exactly the required relationship: "
                    + source + " -> " + target);
        }
        Set<String> relationships = new HashSet<>();
        for (StoryContextAnalysisResult.CausalClaim claim : claims) {
            String relationship = claim.source() + "\u0000" + claim.target();
            if (!relationships.add(relationship)) {
                throw new IllegalStateException("Duplicate causal relationship: "
                        + claim.source() + " -> " + claim.target());
            }
            if (claim.causalClassification() != StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED
                    && claim.evidenceReferences().isEmpty()) {
                throw new IllegalStateException("Affirmative causal claim requires evidence references: "
                        + claim.source() + " -> " + claim.target());
            }
            boolean affirmativeBasis = claim.evidenceBasis()
                    == StoryContextAnalysisResult.CausalEvidenceBasis.DIRECT_DOCUMENTATION
                    || claim.evidenceBasis()
                    == StoryContextAnalysisResult.CausalEvidenceBasis.MATERIAL_CORROBORATION;
            if (claim.causalClassification()
                    == StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED
                    && affirmativeBasis) {
                throw new IllegalStateException("NOT_ESTABLISHED causal claim has affirmative evidence basis");
            }
            for (EvidenceRef evidenceRef : claim.evidenceReferences()) {
                if (!allowedRefs.contains(evidenceRef.reference())) {
                    throw new IllegalStateException(
                            "Causal claim references unauthorized evidence: " + evidenceRef.reference());
                }
            }
            StoryContextAnalysisResult.CausalClassification maximum = maximumDefensibleClassification(
                    claim.evidenceReferences());
            if (causalRank(claim.causalClassification()) > causalRank(maximum)) {
                throw new IllegalStateException("Causal claim exceeds defensible evidence level: "
                        + claim.source() + " -> " + claim.target());
            }
        }
    }

    private static StoryContextAnalysisResult.CausalClassification maximumDefensibleClassification(
            List<EvidenceRef> evidenceReferences
    ) {
        if (evidenceReferences.isEmpty() || evidenceReferences.stream().anyMatch(reference ->
                reference.role() == EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT
                        || reference.role() == EvidenceRef.CausalEvidenceRole.CONTRADICTORY_EVIDENCE)) {
            return StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED;
        }
        if (evidenceReferences.stream().anyMatch(reference ->
                reference.role() == EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT)) {
            return StoryContextAnalysisResult.CausalClassification.EXPLICITLY_DOCUMENTED;
        }
        long materialReferences = evidenceReferences.stream()
                .filter(reference -> reference.role() == EvidenceRef.CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT)
                .map(EvidenceRef::reference).distinct().count();
        return materialReferences >= 2
                ? StoryContextAnalysisResult.CausalClassification.STRONGLY_SUPPORTED
                : StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED;
    }

    private static int causalRank(StoryContextAnalysisResult.CausalClassification classification) {
        return switch (classification) {
            case NOT_ESTABLISHED -> 0;
            case STRONGLY_SUPPORTED -> 1;
            case EXPLICITLY_DOCUMENTED -> 2;
        };
    }

    private static void validateGroundedFindings(
            List<? extends Record> findings,
            Set<String> allowedRefs,
            boolean relationTypeRequired
    ) {
        for (Record finding : findings) {
            try {
                // Use reflection to access grounding() method
                var groundingMethod = finding.getClass().getMethod("grounding");
                Object grounding = groundingMethod.invoke(finding);

                // Access evidenceReferences from grounding
                var evidenceRefsMethod = grounding.getClass().getMethod("evidenceReferences");
                @SuppressWarnings("unchecked")
                List<EvidenceRef> evidenceRefs = (List<EvidenceRef>) evidenceRefsMethod.invoke(grounding);

                for (EvidenceRef evidenceRef : evidenceRefs) {
                if (!allowedRefs.contains(evidenceRef.reference())) {
                        String title = findingLabel(finding);
                        throw new IllegalStateException(
                                "Finding '" + title + "' references unauthorized evidence: " + evidenceRef.reference()
                        );
                    }
                }

                // Validate classification
                var classificationMethod = grounding.getClass().getMethod("classification");
                String classification = (String) classificationMethod.invoke(grounding);
                if (!Set.of("FACTUAL_EXTRACTION", "AI_INTERPRETATION", "RECOMMENDATION").contains(classification)) {
                    String title = findingLabel(finding);
                    throw new IllegalStateException(
                            "Finding '" + title + "' has invalid classification: " + classification
                    );
                }

                // Factual/Interpretative findings must be grounded with at least one evidence reference
                if (("FACTUAL_EXTRACTION".equals(classification) || "AI_INTERPRETATION".equals(classification))
                        && evidenceRefs.isEmpty()) {
                    String title = findingLabel(finding);
                    throw new IllegalStateException(
                            "Finding '" + title + "' of type " + classification + " must have at least one evidence reference"
                    );
                }

                // Validate relationType
                var relationTypeMethod = grounding.getClass().getMethod("relationType");
                Object relationType = relationTypeMethod.invoke(grounding);
                if (relationTypeRequired && relationType == null) {
                    String title = findingLabel(finding);
                    throw new IllegalStateException(
                        "Finding '" + title + "' must have relationType"
                    );
                }
                if (relationType == null) {
                    continue;
                }
                if (!Set.of("EXPLICIT", "TEMPORAL_PROXIMITY", "POSSIBLE_RELEVANCE", "INFERRED_HYPOTHESIS")
                        .contains(relationType.toString())) {
                    String title = findingLabel(finding);
                    throw new IllegalStateException(
                            "Finding '" + title + "' has invalid relationType: " + relationType
                    );
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Validation failed for finding: " + e.getMessage(), e);
            }
        }
    }

    private static String findingLabel(Record finding) throws ReflectiveOperationException {
        for (String methodName : List.of("title", "componentName")) {
            try {
                return (String) finding.getClass().getMethod(methodName).invoke(finding);
            } catch (NoSuchMethodException ignored) {
                // Finding categories use either title or componentName as their display label.
            }
        }
        throw new IllegalStateException("Finding has no display label: " + finding.getClass().getSimpleName());
    }
}
