package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.authorization.ProjectRole;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.contracts.storycontextagent.StoryContextAgentProtocolV1;
import com.hopeful117.devlogai.storycontextanalysis.service.SnapshotIdentity;
import com.hopeful117.devlogai.storycontextanalysis.policy.StoryContextCompletionPolicyResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

/** Core-owned, one-shot follow-up capability over an authorized initial snapshot. */
@Service
public class StoryAgentFollowUpService {
    public static final int MAX_QUESTION_LENGTH = 2_000;
    public static final int MAX_GUIDANCE_LENGTH = 1_000;
    private static final Duration SNAPSHOT_TTL = Duration.ofDays(30);
    private static final List<ProjectRole> READ_ROLES = List.of(
            ProjectRole.PROJECT_READER, ProjectRole.PROJECT_OWNER);
    private static final Set<String> GUIDANCE_KEYS = Set.of(
            "focus", "audience", "levelOfDetail", "writingStyle", "outputContext", "priorities");

    private final AiTaskRepository aiTaskRepository;
    private final CoreProjectAuthorizationService authorizationService;
    private final AiTaskService aiTaskService;
    private final AIEngineClient aiEngineClient;
    private final IntentCatalog intentCatalog;
    private final ObjectMapper objectMapper;
    private final StoryContextDigestService digestService;
    private final Clock clock;
    private final StoryContextBudgetPreflight budgetPreflight;

    @Autowired
    public StoryAgentFollowUpService(
            AiTaskRepository aiTaskRepository, CoreProjectAuthorizationService authorizationService,
             AiTaskService aiTaskService, AIEngineClient aiEngineClient, IntentCatalog intentCatalog,
             ObjectMapper objectMapper, StoryContextDigestService digestService, Clock clock,
             AiProviderCatalogService providerCatalog,
             StoryContextCompletionPolicyResolver completionPolicyResolver) {
        this.aiTaskRepository = aiTaskRepository;
        this.authorizationService = authorizationService;
        this.aiTaskService = aiTaskService;
        this.aiEngineClient = aiEngineClient;
        this.intentCatalog = intentCatalog;
        this.objectMapper = objectMapper;
        this.digestService = digestService;
        this.clock = clock;
        this.budgetPreflight = new StoryContextBudgetPreflight(providerCatalog, objectMapper, completionPolicyResolver);
    }

    public StoryAgentFollowUpService(
            AiTaskRepository aiTaskRepository, CoreProjectAuthorizationService authorizationService,
            AiTaskService aiTaskService, AIEngineClient aiEngineClient, IntentCatalog intentCatalog,
            ObjectMapper objectMapper, StoryContextDigestService digestService, Clock clock,
            StoryContextCompletionPolicyResolver completionPolicyResolver) {
        this(aiTaskRepository, authorizationService, aiTaskService, aiEngineClient, intentCatalog,
                objectMapper, digestService, clock, new AiProviderCatalogService(), completionPolicyResolver);
    }

    @Transactional
    public FollowUpSubmission submit(AuthenticatedPrincipal principal, UUID parentSnapshotId,
                                     String question, Map<String, Object> guidance,
                                     String idempotencyKey) {
        validateInput(question, guidance);
        authorize(principal, parentSnapshotId);
        aiTaskRepository.acquireSubmissionLock("follow-up:" + parentSnapshotId);

        AiTask parent = aiTaskRepository.findById(parentSnapshotId)
                .orElseThrow(() -> notFound(parentSnapshotId));
        if (parent.getParentSnapshotId() != null) {
            throw notFound(parentSnapshotId);
        }
        if (parent.getSelectedKnowledgeSnapshot() == null || parent.getContextSnapshot() == null) {
            throw notFound(parentSnapshotId);
        }

        String requestDigest = requestDigest(question, guidance);
        var existing = aiTaskRepository.findByParentSnapshotId(parentSnapshotId);
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getFollowUpRequestDigest(), requestDigest)) {
                throw new ConflictException("A different follow-up already exists for this snapshot");
            }
            return response(existing.get(), parentSnapshotId);
        }

        AiTask followUp = new AiTask();
        followUp.setId(UUID.randomUUID());
        followUp.setAnalysis(parent.getAnalysis());
        followUp.setCorrelationId(UUID.randomUUID());
        followUp.setStatus(AiTaskStatus.CREATED);
        followUp.setTaskType(AiTaskType.STORY_CONTEXT_ANALYSIS);
        followUp.setIntentId(parent.getIntentId());
        followUp.setIntentVersion(parent.getIntentVersion());
        followUp.setIntentSnapshot(copy(parent.getIntentSnapshot()));
        followUp.setUserGuidanceSnapshot(copy(providerGuidance(question, guidance)));
        followUp.setPromptRequestId(followUp.getCorrelationId());
        followUp.setContextDigest(parent.getContextDigest());
        followUp.setProjectionDigest(parent.getProjectionDigest());
        followUp.setSelectionDigest(parent.getSelectionDigest());
        followUp.setSelectionVersion(parent.getSelectionVersion());
        followUp.setSelectedKnowledgeSnapshot(copy(parent.getSelectedKnowledgeSnapshot()));
        followUp.setAiReferenceMappingSnapshot(copy(parent.getAiReferenceMappingSnapshot()));
        followUp.setContextSnapshot(followUpSnapshot(parent, followUp.getId(), question, requestDigest));
        followUp.setParentSnapshotId(parentSnapshotId);
        followUp.setFollowUpRequestDigest(requestDigest);
        followUp = aiTaskRepository.save(followUp);

        IntentDefinition intent = intentCatalog.resolve(parent.getIntentId(), parent.getIntentVersion());
        Map<String, Object> followUpOutputContract = Map.of(
                "type", "object", "structured", true, "hasFollowUpResult", true,
                "root", "followUpResult", "requiredFields", List.of(
                        "status", "answer", "evidenceReferences", "uncertainties",
                        "missingInformation", "nextStep", "parentSnapshotId", "followUpId",
                        "snapshotId", "contextDigest", "projectionDigest"));
        intent = new IntentDefinition(intent.id(), intent.version(), intent.objective(),
                intent.supportedInsightTypes(), intent.constraints(), followUpOutputContract,
                intent.promptTemplate(), intent.contextProfiles());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contextDigest", parent.getContextDigest());
        metadata.put("projectionDigest", parent.getProjectionDigest());
        metadata.put("protocolVersion", StoryContextAgentProtocolV1.PROTOCOL_VERSION);
        metadata.put("contractVersion", parent.getContextSnapshot().get("projection") instanceof Map<?, ?> projection
                ? projection.get("contractVersion") : StoryContextAgentProjectionV1.CONTRACT_VERSION);
        metadata.put("projectionVersion", parent.getContextSnapshot().get("projectionVersion"));
        metadata.put("scope", parent.getContextSnapshot().get("scope"));
        metadata.put("freshness", parent.getContextSnapshot().get("freshness"));
        metadata.put("groundingDigest", parent.getContextSnapshot().get("groundingDigest"));
        metadata.put("parentSnapshotId", parentSnapshotId.toString());
        metadata.put("followUpId", followUp.getId().toString());
        metadata.put("followUpQuestion", question.trim());
        PromptRequest prompt = new PromptRequest(
                UUID.randomUUID(), followUp.getCorrelationId(), parent.getAnalysis().getId(),
                followUp.getId(), AiTaskType.STORY_CONTEXT_ANALYSIS, intent,
                com.hopeful117.devlogai.intent.model.UserGuidance.from(
                        providerGuidance(question, guidance)),
                 followUp.getSelectedKnowledgeSnapshot(), intent.outputSchema(),
                 map(parent.getContextSnapshot().get("groundingContract")), metadata,
                 parent.getContextDigest(), parent.getSelectionDigest(), parent.getProjectionDigest(),
                 parent.getCredentialBindingId(), parent.getProvider(), parent.getModelIdentifier(),
                 map(parent.getContextSnapshot().get("allowedParameters")));
         StoryContextBudgetPreflight.Result budget = budgetPreflight.check(prompt);
         prompt = prompt.withAllowedParameters(budget.effectiveParameters());
         Map<String, Object> snapshot = followUp.getContextSnapshot();
         snapshot.put("allowedParameters", budget.accepted()
                 ? budget.effectiveParameters() : map(parent.getContextSnapshot().get("allowedParameters")));
         snapshot.put("budgetAccounting", budgetAccounting(budget));
         followUp.setContextSnapshot(snapshot);
         followUp = aiTaskRepository.save(followUp);
         if (!budget.accepted()) {
             aiTaskService.failSubmission(followUp.getId(),
                     new com.hopeful117.devlogai.ai.task.dto.request.FailAiTaskRequest(
                             budget.code(), budget.message()));
             throw new IllegalStateException(budget.message());
         }

         aiTaskService.submit(followUp.getId(), new com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest(null));
         aiEngineClient.submit(prompt);
        return response(followUp, parentSnapshotId);
    }

    private Map<String, Object> budgetAccounting(StoryContextBudgetPreflight.Result budget) {
        Map<String, Object> accounting = new LinkedHashMap<>();
        accounting.put("preflightOutcome", budget.outcome());
        accounting.put("promptPolicyVersion", StoryContextBudgetPreflight.PROMPT_POLICY_VERSION);
        if (budget.policyVersion() != null) accounting.put("completionPolicyVersion", budget.policyVersion());
        if (budget.complexityClass() != null) accounting.put("complexityClass", budget.complexityClass());
        if (budget.measurementCertainty() != null) accounting.put("measurementCertainty", budget.measurementCertainty());
        accounting.put("measurementUnit", "tokens");
        accounting.put("measurementStatus", budget.inputEstimate() >= 0 ? "ESTIMATED" : "UNKNOWN");
        if (budget.inputEstimate() >= 0) accounting.put("inputEstimate", budget.inputEstimate());
        if (budget.outputLimit() >= 0) accounting.put("outputLimit", budget.outputLimit());
        if (budget.requiredOutputLimit() > 0) accounting.put("requiredOutputLimit", budget.requiredOutputLimit());
         if (budget.targetOutputLimit() >= 0) accounting.put("targetOutputLimit", budget.targetOutputLimit());
         accounting.put("outputLimitSource", budget.outputLimitSource());
        if (budget.capacity() >= 0) accounting.put("modelCapacity", budget.capacity());
        if (budget.code() != null) accounting.put("failureCode", budget.code());
        if (budget.message() != null) accounting.put("failureMessage", budget.message());
        return accounting;
    }

    private void authorize(AuthenticatedPrincipal principal, UUID snapshotId) {
        if (principal == null) throw new com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException();
        if (!principal.hasDelegatedScope("MCP_STORY_AGENT_EXECUTE")) {
            throw notFound(snapshotId);
        }
        var identity = aiTaskRepository.findStoryContextSnapshotIdentity(snapshotId)
                .orElseThrow(() -> notFound(snapshotId));
        Instant expiryOrigin = identity.createdAt();
        if (identity.parentSnapshotId() != null) {
            expiryOrigin = aiTaskRepository.findStoryContextSnapshotIdentity(identity.parentSnapshotId())
                    .map(SnapshotIdentity::createdAt)
                    .orElse(null);
        }
        if (identity.taskType() != AiTaskType.STORY_CONTEXT_ANALYSIS
                || expiryOrigin == null
                || !authorizationService.isAuthorized(principal, identity.projectId(),
                "MCP_STORY_AGENT_EXECUTE")
                || !clock.instant().isBefore(expiryOrigin.plus(SNAPSHOT_TTL))) {
            throw notFound(snapshotId);
        }
    }

    private void validateInput(String question, Map<String, Object> guidance) {
        if (question == null || question.isBlank() || question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("question must be between 1 and 2000 characters");
        }
        if (guidance != null) {
            if (!guidance.keySet().stream().allMatch(GUIDANCE_KEYS::contains)) {
                throw new IllegalArgumentException("guidance contains unsupported fields");
            }
            String serialized = digestService.canonicalJson(guidance);
            if (serialized.length() > MAX_GUIDANCE_LENGTH) {
                throw new IllegalArgumentException("guidance exceeds the bounded follow-up limit");
            }
        }
    }

    private Map<String, Object> providerGuidance(String question, Map<String, Object> guidance) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (guidance != null) result.putAll(guidance);
        return result;
    }

    private Map<String, Object> followUpSnapshot(AiTask parent, UUID followUpId, String question, String digest) {
        Map<String, Object> snapshot = copy(parent.getContextSnapshot());
        snapshot.put("parentSnapshotId", parent.getId());
        snapshot.put("followUpId", followUpId);
        snapshot.put("followUpQuestionDigest", digestService.sha256(question.trim()));
        snapshot.put("followUpRequestDigest", digest);
        snapshot.put("status", "SUBMITTED");
        return snapshot;
    }

    private String requestDigest(String question, Map<String, Object> guidance) {
        return digestService.sha256(digestService.canonicalJson(Map.of(
                "question", question.trim(), "guidance", guidance == null ? Map.of() : guidance)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? null : objectMapper.convertValue(value, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private FollowUpSubmission response(AiTask task, UUID parentSnapshotId) {
        return new FollowUpSubmission(task.getId(), parentSnapshotId, task.getId(),
                task.getStatus().name(), task.getContextDigest(), task.getProjectionDigest());
    }

    private EntityNotFoundException notFound(UUID snapshotId) {
        return new EntityNotFoundException("Story Context snapshot", snapshotId);
    }

    public record FollowUpSubmission(UUID followUpId, UUID parentSnapshotId, UUID snapshotId,
                                     String status, String contextDigest, String projectionDigest) { }
}
