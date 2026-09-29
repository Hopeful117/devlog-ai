package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.reference.AiReferenceRegistryFactory;
import com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.storycontextagent.StoryContextAgentProtocolV1;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates the immutable Core snapshot and submits the prepared task to the AI engine. */
@Service
@RequiredArgsConstructor
public class StoryContextSubmissionService {
    private static final String INTENT_VERSION = "v1";

    private final IntentCatalog intentCatalog;
    private final AiTaskService aiTaskService;
    private final AIEngineClient aiEngineClient;
    private final AiTaskRepository aiTaskRepository;
    private final AnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;
    private final StoryContextDigestService digestService;

    @Transactional
    public UUID submit(PreparedStoryContext prepared, Map<String, Object> guidance,
                       String submissionDigest, String idempotencyKeyHash) {
        String projectSlug = prepared.projectSlug();
        UUID storyId = prepared.storyId();
        String requestedIntent = prepared.intent();
        List<String> files = prepared.files();
        CanonicalEngineeringContext canonicalContext = prepared.canonicalContext();
        Map<String, Object> projection = prepared.projection();
        IntentDefinition intent = intentCatalog.resolve(requestedIntent, INTENT_VERSION);
        UserGuidance userGuidance = mapGuidance(guidance);
        String projectionDigest = prepared.projectionDigest();
        String contextDigest = canonicalContext.contextDigest();
        if (contextDigest == null || contextDigest.isBlank()) {
            throw new IllegalStateException("Canonical context digest is required before submission");
        }

        Map<String, Object> groundingContract = buildGroundingContract(canonicalContext, projectSlug, guidance);
        Analysis executionAnalysis = analysisRepository.save(Analysis.builder()
                .project(prepared.project())
                .type(AnalysisType.STORY_CONTEXT_ANALYSIS)
                .intentId(requestedIntent)
                .intentVersion(INTENT_VERSION)
                .status(AnalysisStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build());

        AiTask task = aiTaskService.createForStoryContextAnalysisEntity(
                executionAnalysis.getId(), AiTaskType.STORY_CONTEXT_ANALYSIS, requestedIntent,
                INTENT_VERSION, intent.promptTemplate(), projection, contextDigest, groundingContract,
                guidance, AiReferenceRegistryFactory.createForStoryContext(canonicalContext.authorizedReferences()));
        task.setContextDigest(contextDigest);
        task.setSelectionDigest(null);
        task.setProjectionDigest(projectionDigest);
        task.setSubmissionDigest(submissionDigest);
        task.setIdempotencyKeyHash(idempotencyKeyHash);
        Map<String, Object> snapshot = buildExecutionSnapshot(
                task, canonicalContext, projection, projectionDigest, projectSlug, storyId,
                requestedIntent, files, guidance, groundingContract, submissionDigest);
        task.setContextSnapshot(snapshot);
        aiTaskRepository.save(task);
        aiTaskService.submit(task.getId(), new SubmitAiTaskRequest(null));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("projectSlug", projectSlug);
        metadata.put("storyId", storyId == null ? null : storyId.toString());
        metadata.put("contextDigest", contextDigest);
        metadata.put("projectionDigest", projectionDigest);
        metadata.put("protocolVersion", StoryContextAgentProtocolV1.PROTOCOL_VERSION);
        metadata.put("contractVersion", StoryContextAgentProjectionV1.CONTRACT_VERSION);
        metadata.put("projectionVersion", StoryContextAgentProjectionV1.PROJECTION_VERSION);
        metadata.put("scope", snapshot.get("scope"));
        metadata.put("freshness", projection.get("freshness"));
        metadata.put("groundingDigest", digestService.sha256(digestService.canonicalJson(groundingContract)));
        metadata.put("storyAgentContractVersion", StoryContextAgentProjectionV1.CONTRACT_VERSION);
        PromptRequest prompt = new PromptRequest(
                UUID.randomUUID(), task.getCorrelationId(), prepared.project().getId(), task.getId(),
                AiTaskType.STORY_CONTEXT_ANALYSIS, intent, userGuidance, projection, intent.outputSchema(),
                groundingContract, metadata);
        aiEngineClient.submit(prompt);
        return task.getId();
    }

    private Map<String, Object> buildGroundingContract(
            CanonicalEngineeringContext canonical, String projectSlug, Map<String, Object> guidance) {
        Map<String, Object> contract = new LinkedHashMap<>();
        String revision = StoryContextAgentProjectionV1.canonicalRevision(canonical);
        contract.put("allowedGroundingReferences", canonical.authorizedReferences().stream().map(ref -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "REPOSITORY_EVIDENCE");
            entry.put("ref", ref.reference());
            entry.put("coreReference", ref.reference());
            entry.put("taskReference", ref.reference());
            entry.put("scope", "PROJECT_REVISION");
            entry.put("project", projectSlug);
            entry.put("revision", revision);
            entry.put("provenance", canonical.provenanceByReference().get(ref.reference()));
            entry.put("resource", ref.resource());
            entry.put("trust", canonical.trustByReference().getOrDefault(ref.reference(), "UNKNOWN"));
            return entry;
        }).toList());
        contract.put("groundingContractVersion", "story-context-grounding/v1");
        boolean required = guidance != null && Boolean.TRUE.equals(guidance.get("causalAnswerRequired"));
        contract.put("causalAnswerRequired", required);
        if (required && guidance.get("causalRelationship") instanceof Map<?, ?> relationship) {
            Object source = relationship.get("source"), target = relationship.get("target");
            if (source instanceof String sourceValue && !sourceValue.isBlank()
                    && target instanceof String targetValue && !targetValue.isBlank()) {
                contract.put("causalContractVersion", "V2");
                contract.put("causalQuestion", Map.of("source", sourceValue, "target", targetValue,
                        "relationAsked", guidance.getOrDefault("relationAsked", "CAUSAL"), "answerRequired", true));
                contract.put("causalRelationship", Map.of("source", sourceValue, "target", targetValue));
            }
        }
        if (required && !contract.containsKey("causalQuestion")) {
            throw new IllegalArgumentException("causalAnswerRequired requires a valid causalRelationship");
        }
        return contract;
    }

    private Map<String, Object> buildExecutionSnapshot(
            AiTask task, CanonicalEngineeringContext canonical, Map<String, Object> projection,
            String projectionDigest, String projectSlug, UUID storyId, String intent, List<String> files,
            Map<String, Object> guidance, Map<String, Object> groundingContract, String submissionDigest) {
        Map<String, Object> snapshot = new LinkedHashMap<>(
                task.getContextSnapshot() == null ? Map.of() : task.getContextSnapshot());
        snapshot.put("protocolVersion", StoryContextAgentProtocolV1.PROTOCOL_VERSION);
        snapshot.put("aiTaskId", task.getId());
        snapshot.put("snapshotId", task.getId());
        snapshot.put("contextDigest", canonical.contextDigest());
        snapshot.put("projectionDigest", projectionDigest);
        snapshot.put("contextVersion", canonical.contextVersion());
        snapshot.put("projectionVersion", StoryContextAgentProjectionV1.PROJECTION_VERSION);
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("projectSlug", projectSlug);
        scope.put("storyId", storyId == null ? null : storyId.toString());
        scope.put("intent", intent);
        scope.put("files", files == null ? List.of() : List.copyOf(files));
        snapshot.put("scope", scope);
        snapshot.put("requestEcho", valueMap(canonical.requestEcho()));
        Object normalizedFreshness = projection.get("freshness");
        snapshot.put("freshness", normalizedFreshness);
        snapshot.put("contextFreshness", normalizedFreshness);
        snapshot.put("revisions", revisions(canonical));
        snapshot.put("policy", Map.of("contractVersion", StoryContextAgentProjectionV1.PROJECTION_VERSION,
                "projectionDigest", projectionDigest,
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
        snapshot.put("groundingDigest", digestService.sha256(digestService.canonicalJson(groundingContract)));
        snapshot.put("canonicalContext", valueMap(canonical));
        snapshot.put("projection", projection);
        snapshot.put("guidance", guidance == null ? Map.of() : new LinkedHashMap<>(guidance));
        snapshot.put("status", "SUBMITTED");
        snapshot.put("submissionDigest", submissionDigest);
        StoryContextAgentProtocolV1.requireSnapshotIdentity(snapshot, task.getId(), canonical.contextDigest(), projectionDigest);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> valueMap(Object value) {
        return value == null ? Map.of() : objectMapper.convertValue(value, Map.class);
    }

    private Map<String, Object> revisions(CanonicalEngineeringContext canonical) {
        Map<String, Object> revisions = new LinkedHashMap<>();
        revisions.put("freshness", canonical.freshness());
        revisions.put("evidence", canonical.repositoryContext() == null ? List.of()
                : canonical.repositoryContext().evidence().stream()
                .map(e -> e.content() == null ? null : e.content().revision())
                .filter(java.util.Objects::nonNull).distinct().toList());
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
        return canonical.repositoryContext() == null ? List.of()
                : canonical.repositoryContext().warnings().stream().sorted().toList();
    }

    private UserGuidance mapGuidance(Map<String, Object> guidance) {
        return guidance == null || guidance.isEmpty() ? null : UserGuidance.from(guidance);
    }
}
