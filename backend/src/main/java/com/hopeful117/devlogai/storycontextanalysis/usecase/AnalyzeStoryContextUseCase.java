package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.*;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.reference.AiReferenceRegistryFactory;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.storycontextagent.StoryContextAgentProtocolV1;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.storycontextanalysis.repository.StoryContextAnalysisRepository;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentMetrics;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AnalysisRepository analysisRepository;
    @Autowired(required = false)
    private StoryContextAgentMetrics metrics;
    private static final TaskSnapshotEvidenceResolver evidenceResolver = new TaskSnapshotEvidenceResolver();

    /** Read-only projection path; it performs no task creation or second selection. */
    public Map<String, Object> project(String projectSlug, UUID storyId, List<String> files) {
        return project(projectSlug, storyId, INTENT_ID, files);
    }

    public Map<String, Object> project(String projectSlug, UUID storyId, String intent, List<String> files) {
        Project project = projectRepository.findBySlug(projectSlug)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectSlug));
        EngineeringStory story = storyId == null ? null : storyRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("EngineeringStory", storyId));
        if (story != null && !story.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("Story does not belong to project");
        }
        List<String> requestedFiles = files == null ? List.of() : List.copyOf(files);
        CanonicalEngineeringContext canonical = engineeringContextFacade.getCanonicalEngineeringContext(
                projectSlug, intent, requestedFiles, storyId);
        if (canonical == null) {
            throw new IllegalStateException("Canonical EngineeringContext is required for Story Context projection");
        }
        Map<String, Object> projection = StoryContextAgentProjectionV1.build(
                canonical, projectSlug, storyId, intent, requestedFiles, story, objectMapper);
        if (metrics != null) {
            metrics.increment("sca_projection_construction_total");
            if (Boolean.TRUE.equals(((Map<?, ?>) projection.get("accounting")).get("truncated"))) {
                metrics.increment("sca_budget_truncated_total");
            }
        }
        return projection;
    }

    public UUID execute(
            String projectSlug,
            UUID storyId,
            List<String> files,
            Map<String, Object> guidance
    ) {
        return execute(projectSlug, storyId, INTENT_ID, files, guidance);
    }

    public UUID execute(
            String projectSlug, UUID storyId, String requestedIntent, List<String> files,
            Map<String, Object> guidance
    ) {
        return execute(projectSlug, storyId, requestedIntent, files, guidance, null);
    }

    @Transactional
    public UUID execute(
            String projectSlug, UUID storyId, String requestedIntent, List<String> files,
            Map<String, Object> guidance, String idempotencyKey
    ) {
        Project project = projectRepository.findBySlug(projectSlug)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectSlug));
        EngineeringStory story = storyId == null ? null : storyRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("EngineeringStory", storyId));
        if (story != null && !story.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("Story does not belong to project");
        }

        if (requestedIntent == null || requestedIntent.isBlank()) {
            throw new IllegalArgumentException("intent is required");
        }
        var intentDef = intentCatalog.resolve(requestedIntent, INTENT_VERSION);

        List<String> requestedFiles = files == null ? List.of() : List.copyOf(files);
        Map<String, Object> submissionIdentity = new LinkedHashMap<>();
        submissionIdentity.put("projectSlug", projectSlug);
        submissionIdentity.put("storyId", storyId == null ? null : storyId.toString());
        submissionIdentity.put("intent", requestedIntent);
        submissionIdentity.put("files", requestedFiles);
        submissionIdentity.put("guidance", guidance == null ? Map.of() : guidance);
        String submissionDigest = sha256(canonicalJson(submissionIdentity));
        String idempotencyKeyHash = idempotencyKey == null || idempotencyKey.isBlank()
                ? null : sha256(idempotencyKey);
        // PostgreSQL advisory locks make the check-and-create operation atomic
        // across application instances without locking unrelated AI tasks.
        aiTaskRepository.acquireSubmissionLock(idempotencyKeyHash == null
                ? submissionDigest : idempotencyKeyHash);
        if (idempotencyKeyHash != null) {
            Optional<AiTask> existing = aiTaskRepository.findByIdempotencyKeyHash(idempotencyKeyHash);
            if (existing.isPresent()) {
                if (!Objects.equals(existing.get().getSubmissionDigest(), submissionDigest)) {
                    throw new com.hopeful117.devlogai.shared.exception.ConflictException(
                            "Idempotency-Key was already used for a different request");
                }
                return existing.get().getId();
            }
        }
        Optional<AiTask> sameRequest = aiTaskRepository.findBySubmissionDigest(submissionDigest);
        if (sameRequest.isPresent()) return sameRequest.get().getId();

        CanonicalEngineeringContext canonicalContext = engineeringContextFacade.getCanonicalEngineeringContext(
                projectSlug,
                requestedIntent,
                requestedFiles,
                storyId
        );
        if (canonicalContext == null) {
            throw new IllegalStateException("Canonical EngineeringContext is required for Story Context Analysis");
        }

        UserGuidance userGuidance = mapGuidance(guidance);
        Map<String, Object> storyAgentProjection = StoryContextAgentProjectionV1.build(
                canonicalContext, projectSlug, storyId, requestedIntent, files, story, objectMapper);
        if (metrics != null) {
            metrics.increment("sca_projection_construction_total");
            if (Boolean.TRUE.equals(((Map<?, ?>) storyAgentProjection.get("accounting")).get("truncated"))) {
                metrics.increment("sca_budget_truncated_total");
            }
        }
        String projectionDigest = (String) storyAgentProjection.get("projectionDigest");

        // The Core canonical construction owns the digest; this use case only propagates it.
        String contextDigest = canonicalContext.contextDigest();
        if (contextDigest == null || contextDigest.isBlank()) {
            throw new IllegalStateException("Canonical context digest is required before submission");
        }

        // Authorize only the canonical repository evidence projected into this prompt.
        Map<String, Object> groundingContract = buildGroundingContract(canonicalContext, projectSlug, guidance);

        Analysis executionAnalysis = analysisRepository.save(Analysis.builder()
                .project(project)
                .type(AnalysisType.STORY_CONTEXT_ANALYSIS)
                .intentId(requestedIntent)
                .intentVersion(INTENT_VERSION)
                .status(AnalysisStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build());

        // Create AiTask with selected knowledge and grounding contract
        AiTask aiTask = aiTaskService.createForStoryContextAnalysisEntity(
                executionAnalysis.getId(),
                AiTaskType.STORY_CONTEXT_ANALYSIS,
                requestedIntent,
                INTENT_VERSION,
                intentDef.promptTemplate(),
                storyAgentProjection,
                contextDigest,
                groundingContract,
                guidance,
                AiReferenceRegistryFactory.createForStoryContext(canonicalContext.authorizedReferences())
        );

        aiTask.setContextDigest(contextDigest);
        aiTask.setSelectionDigest(null);
        aiTask.setProjectionDigest(projectionDigest);
        aiTask.setSubmissionDigest(submissionDigest);
        aiTask.setIdempotencyKeyHash(idempotencyKeyHash);
        // Freeze the complete execution contract before SUBMITTED. Callback data is
        // an echo only and must never be able to complete or repair this snapshot.
        Map<String, Object> taskContextSnapshot = buildExecutionSnapshot(
                aiTask, canonicalContext, storyAgentProjection, projectionDigest,
                projectSlug, storyId, requestedIntent, files, guidance, groundingContract, submissionDigest);
        aiTask.setContextSnapshot(taskContextSnapshot);
        aiTaskRepository.save(aiTask);

        // Submit task before sending to Python (ensures SUBMITTED status)
        aiTaskService.submit(aiTask.getId(), new com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest(null));

        // Build PromptRequest with selected knowledge and grounding contract
        Map<String, Object> promptMetadata = new LinkedHashMap<>();
        promptMetadata.put("projectSlug", projectSlug);
        promptMetadata.put("storyId", storyId == null ? null : storyId.toString());
        promptMetadata.put("contextDigest", contextDigest);
        if (aiTask.getSelectionDigest() != null) promptMetadata.put("selectionDigest", aiTask.getSelectionDigest());
        promptMetadata.put("projectionDigest", projectionDigest);
        promptMetadata.put("protocolVersion", StoryContextAgentProtocolV1.PROTOCOL_VERSION);
        promptMetadata.put("contractVersion", StoryContextAgentProjectionV1.CONTRACT_VERSION);
        promptMetadata.put("projectionVersion", StoryContextAgentProjectionV1.PROJECTION_VERSION);
        promptMetadata.put("scope", taskContextSnapshot.get("scope"));
        promptMetadata.put("freshness", storyAgentProjection.get("freshness"));
        promptMetadata.put("groundingDigest", sha256(canonicalJson(groundingContract)));
        promptMetadata.put("storyAgentContractVersion", StoryContextAgentProjectionV1.CONTRACT_VERSION);
        PromptRequest promptRequest = new PromptRequest(
                UUID.randomUUID(),
                aiTask.getCorrelationId(),
                project.getId(),
                aiTask.getId(),
                AiTaskType.STORY_CONTEXT_ANALYSIS,
                intentDef,
                userGuidance,
                storyAgentProjection,
                intentDef.outputSchema(),
                groundingContract,
                promptMetadata
        );

        aiEngineClient.submit(promptRequest);

        return aiTask.getId();
    }

    private Map<String, Object> buildExecutionSnapshot(
            AiTask task, CanonicalEngineeringContext canonical, Map<String, Object> projection,
            String projectionDigest, String projectSlug, UUID storyId, String requestedIntent, List<String> files,
            Map<String, Object> guidance, Map<String, Object> groundingContract, String submissionDigest) {
        if (task.getId() == null) {
            throw new IllegalStateException("AI task id is required before creating the Story Context snapshot");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(
                task.getContextSnapshot() == null ? Map.of() : task.getContextSnapshot());
        snapshot.put("protocolVersion", StoryContextAgentProtocolV1.PROTOCOL_VERSION);
        snapshot.put("aiTaskId", task.getId());
        snapshot.put("snapshotId", task.getId());
        snapshot.put("contextDigest", canonical.contextDigest());
        snapshot.put("projectionDigest", projectionDigest);
        snapshot.put("contextVersion", canonical.contextVersion());
        snapshot.put("projectionVersion", StoryContextAgentProjectionV1.PROJECTION_VERSION);
        Map<String,Object> scope = new LinkedHashMap<>();
        scope.put("projectSlug", projectSlug); scope.put("storyId", storyId == null ? null : storyId.toString());
        scope.put("intent", requestedIntent); scope.put("files", files == null ? List.of() : List.copyOf(files));
        snapshot.put("scope", scope);
        Map<String, Object> requestEcho = valueMap(canonical.requestEcho());
        snapshot.put("requestEcho", requestEcho);
        Object normalizedFreshness = projection.get("freshness");
        snapshot.put("freshness", normalizedFreshness);
        snapshot.put("contextFreshness", normalizedFreshness); // legacy compatibility alias
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
        snapshot.put("groundingDigest", sha256(canonicalJson(groundingContract)));
        snapshot.put("canonicalContext", valueMap(canonical));
        snapshot.put("projection", projectionEnvelope(projection, groundingContract));
        snapshot.put("guidance", guidance == null ? Map.of() : new LinkedHashMap<>(guidance));
        snapshot.put("status", "SUBMITTED");
        snapshot.put("submissionDigest", submissionDigest);
        StoryContextAgentProtocolV1.requireSnapshotIdentity(snapshot, task.getId(),
                canonical.contextDigest(), projectionDigest);
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
        return canonical.repositoryContext() == null ? List.of() : canonical.repositoryContext().warnings().stream().sorted().toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> valueMap(Object value) {
        return value == null ? Map.of() : objectMapper.convertValue(value, Map.class);
    }

    private Map<String, Object> buildGroundingContract(
            CanonicalEngineeringContext canonical, String projectSlug, Map<String, Object> guidance) {
        Map<String, Object> contract = new LinkedHashMap<>();
        List<EvidenceRef> typed = canonical.authorizedReferences();
        String revision = StoryContextAgentProjectionV1.canonicalRevision(canonical);
        contract.put("allowedGroundingReferences", typed.stream().map(ref -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "REPOSITORY_EVIDENCE"); entry.put("ref", ref.reference());
            entry.put("coreReference", ref.reference()); entry.put("taskReference", ref.reference());
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

    private String digestProjection(Map<String, Object> selectedKnowledge,
                                   Map<String, Object> groundingContract) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("version", StoryContextAgentProjectionV1.PROJECTION_VERSION);
        envelope.put("selectedKnowledge", selectedKnowledge);
        envelope.put("groundingContract", groundingContract == null ? Map.of() : groundingContract);
        return sha256(canonicalJson(envelope));
    }

    private Map<String, Object> projectionEnvelope(Map<String, Object> storyAgentProjection,
                                                           Map<String, Object> groundingContract) {
        // Store the exact immutable V1 payload exposed to AI.
        return storyAgentProjection;
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

    private UserGuidance mapGuidance(Map<String, Object> guidance) {
        if (guidance == null || guidance.isEmpty()) {
            return null;
        }
        // Keep the REST payload aligned with the shared guidance contract. In
        // particular, do not shift fields into unrelated prompt fields: this
        // object is serialized into PromptRequest and rendered by Python.
        return UserGuidance.from(guidance);
    }

    @Transactional
    public void handleCallback(UUID correlationId, AiTaskResultRequest request) {
        request.validateCallbackContract();
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

        UUID storyId = callbackStoryId(task);

        @SuppressWarnings("unchecked")
        Map<String, Object> contextFreshness = task.getContextSnapshot() != null
                ? (Map<String, Object>) task.getContextSnapshot().get("contextFreshness")
                : null;

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .story(storyId == null ? null : storyRepository.findById(storyId).orElseThrow())
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
            throw new InvalidAiTaskResultException("Story Context Analysis callback is missing prompt identities");
        }
        if (!StoryContextAgentProtocolV1.PROTOCOL_VERSION.equals(metadata.protocolVersion())) {
            throw new InvalidAiTaskResultException("Story Context Analysis callback protocolVersion is missing or unsupported");
        }
        Map<String, Object> snapshot = task.getContextSnapshot() == null ? Map.of() : task.getContextSnapshot();
        String expectedContext = task.getContextDigest();
        String expectedProjection = task.getProjectionDigest();
        String expectedSelection = task.getSelectionDigest();
        String expectedProjectionVersion = String.valueOf(snapshot.get("projectionVersion"));
        Object expectedScope = snapshot.get("scope");
        Object expectedFreshness = snapshot.get("freshness");
        Object expectedGrounding = snapshot.get("groundingDigest");
        if (expectedProjection == null || !expectedProjection.matches("[0-9a-f]{64}")) {
            throw new InvalidAiTaskResultException("Story Context Analysis task is missing a valid projection digest");
        }
        if (metadata.projectionDigest() == null
                || !metadata.projectionDigest().matches("[0-9a-f]{64}")) {
            throw new InvalidAiTaskResultException("Story Context Analysis callback is missing a valid projection digest");
        }
        if (!Objects.equals(expectedContext, metadata.contextDigest())) {
            throw new InvalidAiTaskResultException("Context digest mismatch in callback");
        }
        if (!Objects.equals(expectedProjection, metadata.projectionDigest())) {
            throw new InvalidAiTaskResultException("Projection digest mismatch in callback");
        }
        if (!Objects.equals(expectedSelection, metadata.selectionDigest())) {
            throw new InvalidAiTaskResultException("Selection digest mismatch in callback");
        }
        if (!Objects.equals(StoryContextAgentProjectionV1.PROJECTION_VERSION, metadata.projectionVersion())
                || !Objects.equals(StoryContextAgentProjectionV1.PROJECTION_VERSION, expectedProjectionVersion)) {
            throw new InvalidAiTaskResultException("Projection version mismatch in callback");
        }
        if (!(metadata.scope() instanceof Map<?, ?>) || !Objects.equals(expectedScope, metadata.scope())) {
            throw new InvalidAiTaskResultException("Scope mismatch in callback");
        }
        if (!(metadata.freshness() instanceof Map<?, ?>) || !Objects.equals(expectedFreshness, metadata.freshness())) {
            throw new InvalidAiTaskResultException("Freshness mismatch in callback");
        }
        if (!(expectedGrounding instanceof String grounding) || !grounding.matches("[0-9a-f]{64}")
                || !Objects.equals(grounding, metadata.groundingDigest())) {
            throw new InvalidAiTaskResultException("Grounding identity mismatch in callback");
        }
        boolean hasIdentitySnapshot = snapshot.containsKey("contextDigest")
                || snapshot.containsKey("projectionDigest") || snapshot.containsKey("selectionDigest");
        if (hasIdentitySnapshot && (!Objects.equals(expectedContext, snapshot.get("contextDigest"))
                || !Objects.equals(expectedProjection, snapshot.get("projectionDigest"))
                || !Objects.equals(expectedSelection, snapshot.get("selectionDigest")))) {
            throw new InvalidAiTaskResultException("AI task snapshot identity is incomplete or inconsistent");
        }
        validateSnapshotScope(task);
        if (!Objects.equals(StoryContextAgentProjectionV1.PROJECTION_VERSION, snapshot.get("projectionVersion")))
            throw new InvalidAiTaskResultException("AI task snapshot projection version is missing or inconsistent");
        if (!(snapshot.get("freshness") instanceof Map<?, ?>) || !(snapshot.get("groundingContract") instanceof Map<?, ?>))
            throw new InvalidAiTaskResultException("AI task snapshot freshness and grounding identity are required");
        Object projection = snapshot.get("projection");
        if (!(projection instanceof Map<?, ?> p)
                || !Objects.equals(expectedContext, p.get("contextDigest"))
                || !Objects.equals(expectedProjection, p.get("projectionDigest"))
                || !Objects.equals(StoryContextAgentProjectionV1.PROJECTION_VERSION, p.get("projectionVersion")))
            throw new InvalidAiTaskResultException("AI task snapshot projection identity is incomplete or inconsistent");
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

        Set<String> allowedRefSet = allowedGroundingReferences(groundingContract, task);

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
    private static Set<String> allowedGroundingReferences(Map<String, Object> groundingContract, AiTask task) {
        if (groundingContract.containsKey("allowedEvidenceReferences")
                || "legacy-grounding/v0".equals(groundingContract.get("groundingContractVersion"))) {
            throw new IllegalStateException("legacy grounding is forbidden in Story Context V1");
        }
        Object typed = groundingContract.get("allowedGroundingReferences");
        if (typed instanceof List<?> entries) {
            Set<String> refs = new LinkedHashSet<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    throw new IllegalStateException("Typed grounding reference must be an object");
                }
                Object type = map.get("type");
                Object ref = map.get("ref");
                if (!"REPOSITORY_EVIDENCE".equals(type) || !(ref instanceof String value)
                        || value.isBlank() || !"PROJECT_REVISION".equals(map.get("scope"))
                        || !(map.get("project") instanceof String project) || project.isBlank()
                        || !(map.get("revision") instanceof String revision) || revision.isBlank()
                        || !(map.get("provenance") instanceof Map<?, ?>)
                        || !(map.get("trust") instanceof String trust) || trust.isBlank()
                        || !Objects.equals(ref, map.get("coreReference"))
                        || !Objects.equals(ref, map.get("taskReference"))) {
                    throw new IllegalStateException("Invalid typed grounding reference");
                }
                Object snapshotScope = taskSnapshotScope(task);
                if (!(snapshotScope instanceof Map<?, ?> requested)
                        || !Objects.equals(project, requested.get("projectSlug"))) {
                    throw new IllegalStateException("Grounding project does not match requested scope");
                }
                if (!Objects.equals(revision, snapshotRevision(task))) throw new IllegalStateException("Grounding revision does not match PROJECT_REVISION snapshot");
                Map<String, Object> evidence = snapshotEvidence(task, value);
                if (evidence == null || !Objects.equals(map.get("provenance"), evidence.get("provenance"))) throw new IllegalStateException("Grounding provenance does not match snapshot evidence");
                refs.add(value);
            }
            return refs;
        }
        if (!"story-context-grounding/v1".equals(groundingContract.get("groundingContractVersion"))) {
            throw new IllegalStateException("V1 grounding requires the typed allow-list contract");
        }
        throw new IllegalStateException("V1 grounding typed allow-list is missing or invalid");
    }

    private static void validateSnapshotScope(AiTask task) {
        Object raw = taskSnapshotScope(task);
        if (!(raw instanceof Map<?, ?> scope)) throw new IllegalStateException("AI task snapshot scope is missing");
        Set<String> keys = Set.of("projectSlug", "storyId", "intent", "files");
        if (!scope.keySet().stream().allMatch(keys::contains)
                || scope.get("projectSlug") == null || scope.get("files") == null)
            throw new IllegalStateException("AI task snapshot scope is incomplete or inconsistent");
        if ((scope.containsKey("intent") && !Objects.equals(task.getIntentId(), scope.get("intent")))
                || !Objects.equals(task.getContextSnapshot().get("storyId"), scope.get("storyId"))) {
            throw new IllegalStateException("AI task scope does not match its intent or story");
        }
        Object projection = task.getSelectedKnowledgeSnapshot();
        if (projection instanceof Map<?, ?> p && p.get("request") instanceof Map<?, ?> request && !Objects.equals(scope, request)) throw new IllegalStateException("AI task snapshot scope does not match request scope");
    }

    private static UUID callbackStoryId(AiTask task) {
        Map<String, Object> snapshot = task.getContextSnapshot();
        Object rawStoryId = null;
        if (snapshot != null && snapshot.get("scope") instanceof Map<?, ?> scope) {
            rawStoryId = scope.get("storyId");
        }
        if (rawStoryId == null && snapshot != null) rawStoryId = snapshot.get("storyId");
        return rawStoryId == null ? null : UUID.fromString(rawStoryId.toString());
    }

    private static String snapshotRevision(AiTask task) {
        Object raw = task.getSelectedKnowledgeSnapshot();
        if (!(raw instanceof Map<?, ?> snapshot)) throw new IllegalStateException("Selected snapshot is missing");
        Object freshness = snapshot.get("freshness");
        if (freshness instanceof Map<?, ?> f && f.get("sourceRevision") instanceof Map<?, ?> source && source.get("revision") instanceof String revision && !revision.isBlank()) return revision;
        throw new IllegalStateException("PROJECT_REVISION snapshot revision is missing");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> snapshotEvidence(AiTask task, String reference) {
        Object raw = task.getSelectedKnowledgeSnapshot();
        if (!(raw instanceof Map<?, ?> snapshot)) return null;
        Object context = snapshot.get("context");
        Object entries = context instanceof Map<?, ?> c ? c.get("repositoryEvidence") : null;
        if (!(entries instanceof List<?>)) {
            context = snapshot.get("repositoryContext");
            entries = context instanceof Map<?, ?> c ? c.get("evidence") : null;
        }
        if (!(entries instanceof List<?> list)) return null;
        for (Object entry : list) if (entry instanceof Map<?, ?> e && Objects.equals(reference, e.get("reference"))) return (Map<String, Object>) e;
        return null;
    }

    private static Object taskSnapshotScope(AiTask task) {
        return task.getContextSnapshot() == null ? null : task.getContextSnapshot().get("scope");
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
