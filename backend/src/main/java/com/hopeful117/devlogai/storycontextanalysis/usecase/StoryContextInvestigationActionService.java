package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.reference.AiReference;
import com.hopeful117.devlogai.ai.reference.AiReferenceBinding;
import com.hopeful117.devlogai.ai.reference.AiReferenceMappingSnapshot;
import com.hopeful117.devlogai.ai.reference.AiReferenceRegistry;
import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationAction;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationActionKind;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationActionRequest;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationBudget;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationCapabilityExecutionService;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationCapabilityResult;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationScope;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationUsage;
import com.hopeful117.devlogai.storycontextanalysis.investigation.ReadEvidenceRangeRequest;
import com.hopeful117.devlogai.storycontextanalysis.investigation.SearchEvidenceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Authenticated, serialized Core execution of one bounded investigation action. */
@Service
public class StoryContextInvestigationActionService {
    private static final String EXECUTE_SCOPE = "MCP_STORY_AGENT_EXECUTE";
    private static final Duration SNAPSHOT_TTL = Duration.ofDays(30);
    private static final int MAX_STORED_ACTIONS = 100;
    private static final int MAX_STORED_REFERENCES = 20;
    private static final int MAX_GROUNDING_REFERENCES = 100;

    private final AiTaskRepository aiTaskRepository;
    private final CoreProjectAuthorizationService authorizationService;
    private final InvestigationCapabilityExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final StoryContextDigestService digestService;
    private final Clock clock;

    @Autowired
    public StoryContextInvestigationActionService(
            AiTaskRepository aiTaskRepository,
            CoreProjectAuthorizationService authorizationService,
            InvestigationCapabilityExecutionService executionService,
            ObjectMapper objectMapper,
            StoryContextDigestService digestService,
            Clock clock) {
        this.aiTaskRepository = aiTaskRepository;
        this.authorizationService = authorizationService;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
        this.digestService = digestService;
        this.clock = clock;
    }

    public StoryContextInvestigationActionService(
            AiTaskRepository aiTaskRepository,
            CoreProjectAuthorizationService authorizationService,
            InvestigationCapabilityExecutionService executionService,
            ObjectMapper objectMapper,
            Clock clock) {
        this(aiTaskRepository, authorizationService, executionService, objectMapper,
                new StoryContextDigestService(objectMapper), clock);
    }

    @Transactional
    public ActionResponse execute(AuthenticatedPrincipal principal, UUID aiTaskId,
                                  InvestigationActionRequest request) {
        return executeInternal(principal, aiTaskId, request, false);
    }

    @Transactional
    public ActionResponse executeAuthenticatedService(UUID aiTaskId,
                                                      InvestigationActionRequest request) {
        return executeInternal(null, aiTaskId, request, true);
    }

    private ActionResponse executeInternal(AuthenticatedPrincipal principal, UUID aiTaskId,
                                           InvestigationActionRequest request, boolean serviceAuthenticated) {
        if (!serviceAuthenticated && (principal == null || !principal.hasDelegatedScope(EXECUTE_SCOPE))) {
            throw new com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException();
        }

        AiTask task = aiTaskRepository.findByIdForUpdate(aiTaskId)
                .orElseThrow(() -> notFound(aiTaskId));
        authorize(principal, task, aiTaskId, serviceAuthenticated);
        if (task.getStatus().isTerminal()) {
            throw new ConflictException("AI task cannot execute investigation actions after completion");
        }

        Map<String, Object> snapshot = copy(task.getContextSnapshot());
        Map<String, Object> investigation = map(snapshot.get("investigation"));
        if (!Boolean.TRUE.equals(investigation.get("initialized"))) {
            throw new ConflictException("Investigation budget has not been explicitly initialized");
        }

        Map<String, Object> scopeMap = map(investigation.get("scope"));
        Map<String, Object> authorizedScopeMap = map(snapshot.get("scope"));
        if (authorizedScopeMap.isEmpty()) authorizedScopeMap = scopeMap;
        String projectSlug = string(authorizedScopeMap.get("projectSlug"));
        UUID storyId = uuid(authorizedScopeMap.get("storyId"));
        String revision = revision(snapshot, investigation);
        List<String> allowedPaths = strings(scopeMap.get("allowedPathPrefixes"));
        if (allowedPaths.isEmpty()) allowedPaths = strings(authorizedScopeMap.get("files"));
        if (projectSlug == null || storyId == null || revision == null) {
            throw new ConflictException("Investigation scope is not initialized in the task snapshot");
        }
        if (request == null) throw new ConflictException("Investigation action is required");
        if (!"SEARCH_EVIDENCE".equals(request.kind()) && !"READ_EVIDENCE_RANGE".equals(request.kind())) {
            throw new ConflictException("Unknown investigation action kind");
        }
        List<Map<String, Object>> actions = actionMetadata(investigation.get("actions"));
        Map<String, Object> duplicate = actions.stream()
                .filter(value -> Objects.equals(value.get("actionId"), request.actionId()))
                .findFirst().orElse(null);
        if (duplicate != null) {
            if (!Objects.equals(string(duplicate.get("requestDigest")), requestDigest(request))) {
                throw new ConflictException("Duplicate action id is bound to a different action request");
            }
            Map<String, Object> canonical = map(duplicate.get("result"));
            if (canonical.isEmpty()) canonical = withGrounding(duplicate, snapshot);
            return new ActionResponse(aiTaskId, true, canonical);
        }
        if (request == null || !Objects.equals(projectSlug, request.projectSlug())
                || !Objects.equals(revision, request.revision())) {
            return rejected(task, snapshot, investigation, request, "SCOPE_MISMATCH",
                    "Action scope does not match the authorized task snapshot");
        }
        validateProtocolBinding(task, investigation, request);

        InvestigationBudget budget = budget(investigation.get("budget"));
        InvestigationUsage usage = usage(investigation.get("usage"));
        if (request.providerGenerationCount() == null || request.providerElapsedMillis() == null
                || request.providerOutputTokens() == null || request.providerGenerationCount() < 0
                || request.providerElapsedMillis() < 0 || request.providerOutputTokens() < 0) {
            throw new ConflictException("Provider generation accounting is required");
        }
        InvestigationUsage providerUsage = withProviderOutput(usage, request.providerOutputTokens());
        InvestigationScope scope = new InvestigationScope(projectSlug, revision, allowedPaths);
        InvestigationAction action = action(request, projectSlug, revision);
        Set<String> actionIds = actions.stream().map(value -> string(value.get("actionId")))
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        long started = System.nanoTime();
        InvestigationCapabilityResult result = executionService.execute(
                task.getAnalysis().getProject().getId(), storyId, action, scope, budget, providerUsage, actionIds);
        long elapsed = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        InvestigationUsage resultingUsage = usageRecord(result, providerUsage, elapsed, request.providerElapsedMillis());
        if (!withinBudget(resultingUsage, budget)) {
            result = InvestigationCapabilityResult.rejected(
                    result.projectId(), result.storyId(), action, "BUDGET_EXHAUSTED",
                    "Investigation action would exceed the remaining budget");
            resultingUsage = providerOnlyUsage(usage, request.providerElapsedMillis(), request.providerOutputTokens());
        }

        Map<String, Object> metadata = metadata(result, action, elapsed, investigation);
        metadata.put("requestDigest", requestDigest(request));
        if (actions.size() >= MAX_STORED_ACTIONS) {
            throw new ConflictException("Investigation action ledger is full");
        }
        actions.add(metadata);
        investigation.put("actions", actions);
        investigation.put("usage", usageMap(resultingUsage));
        investigation.put("accountedProviderGenerationCount", addAccountedGenerations(investigation,
                request.providerGenerationCount()));
        investigation.put("accountedProviderElapsedMillis", accountedProviderElapsed(investigation)
                + request.providerElapsedMillis());
        investigation.put("ledger", ledger(actions));
        snapshot.put("investigation", investigation);
        GroundingUpdate grounding = appendCitableGrounding(task, snapshot, result, projectSlug, revision);
        task.setContextSnapshot(snapshot);
        Map<String, Object> response = canonicalResult(metadata, result, grounding);
        metadata.put("result", response);
        aiTaskRepository.save(task);
        return new ActionResponse(aiTaskId, false, response);
    }

    private void authorize(AuthenticatedPrincipal principal, AiTask task, UUID id, boolean serviceAuthenticated) {
        if (task.getTaskType() != AiTaskType.STORY_CONTEXT_ANALYSIS || task.getCreatedAt() == null
                || !clock.instant().isBefore(task.getCreatedAt().plus(SNAPSHOT_TTL))
                || (!serviceAuthenticated && !authorizationService.isAuthorized(principal,
                task.getAnalysis().getProject().getId(), EXECUTE_SCOPE))) {
            throw notFound(id);
        }
    }

    private void validateProtocolBinding(AiTask task, Map<String, Object> investigation,
                                         InvestigationActionRequest request) {
        if (!"story-context-agent-protocol/v2".equals(request.protocolVersion())
                || !Objects.equals(uuid(investigation.get("executionId")), request.executionId())
                || !Objects.equals(task.getId(), request.parentTaskId())
                || !Objects.equals(task.getCorrelationId(), request.correlationId())
                || !Objects.equals(task.getProvider(), request.provider())
                || !Objects.equals(task.getCredentialBindingId(), request.credentialBindingId())
                || request.providerOutputTokens() == null || request.providerOutputTokens() < 0
                || request.providerGenerationCount() == null || request.providerGenerationCount() < 0
                || request.providerElapsedMillis() == null || request.providerElapsedMillis() < 0) {
            throw new ConflictException("Investigation action identity or provider binding does not match the task");
        }
    }

    private ActionResponse rejected(AiTask task, Map<String, Object> snapshot,
                                    Map<String, Object> investigation,
                                    InvestigationActionRequest request, String code, String message) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request == null || request.actionId() == null || request.projectSlug() == null
                || request.revision() == null || request.kind() == null) {
            throw new ConflictException("Rejected investigation action is not representable as v2");
        }
        accountProviderGeneration(investigation, request);
        InvestigationUsage usage = usage(investigation.get("usage"));
        InvestigationBudget budget = budget(investigation.get("budget"));
        InvestigationUsage rejectedUsage;
        try {
            rejectedUsage = new InvestigationUsage(Math.addExact(usage.steps(), 1), usage.retrievalCalls(),
                    usage.evidenceItems(), usage.bytes(), usage.tokens(), usage.elapsedMillis(),
                    usage.providerOutputTokens());
        } catch (ArithmeticException exception) {
            throw new ConflictException("Investigation action accounting overflowed");
        }
        if (!withinBudget(rejectedUsage, budget)) {
            throw new ConflictException("Rejected investigation action exceeds the remaining budget");
        }
        investigation.put("usage", usageMap(rejectedUsage));
        metadata.put("protocolVersion", "story-context-agent-protocol/v2");
        metadata.put("executionId", investigation.get("executionId"));
        metadata.put("parentTaskId", investigation.get("parentTaskId"));
        metadata.put("actionId", request.actionId());
        metadata.put("requestDigest", requestDigest(request));
        metadata.put("kind", request.kind());
        metadata.put("projectSlug", request.projectSlug());
        metadata.put("revision", request.revision());
        metadata.put("outcome", "REJECTED");
        metadata.put("evidence", List.of());
        metadata.put("stopReason", "ACTION_REJECTED");
        metadata.put("groundingContract", map(snapshot.get("groundingContract")));
        metadata.put("groundingDigest", digestService.sha256(digestService.canonicalJson(map(snapshot.get("groundingContract")))));
        metadata.put("rejectionCode", code);
        metadata.put("rejectionMessage", message);
        List<Map<String, Object>> actions = actionMetadata(investigation.get("actions"));
        if (actions.size() < MAX_STORED_ACTIONS) actions.add(metadata);
        investigation.put("actions", actions);
        investigation.put("ledger", ledger(actions));
        snapshot.put("investigation", investigation);
        task.setContextSnapshot(snapshot);
        Map<String, Object> canonical = new LinkedHashMap<>(metadata);
        metadata.put("result", canonical);
        aiTaskRepository.save(task);
        return new ActionResponse(task.getId(), false, canonical);
    }

    private void accountProviderGeneration(Map<String, Object> investigation,
                                           InvestigationActionRequest request) {
        if (request.providerGenerationCount() == null || request.providerElapsedMillis() == null
                || request.providerOutputTokens() == null || request.providerGenerationCount() < 0
                || request.providerElapsedMillis() < 0 || request.providerOutputTokens() < 0) {
            throw new ConflictException("Provider generation accounting is required");
        }
        InvestigationUsage usage = usage(investigation.get("usage"));
        InvestigationBudget budget = budget(investigation.get("budget"));
        InvestigationUsage updated;
        try {
            updated = new InvestigationUsage(usage.steps(), usage.retrievalCalls(), usage.evidenceItems(),
                    usage.bytes(), usage.tokens(),
                    Math.addExact(usage.elapsedMillis(), request.providerElapsedMillis()),
                    Math.addExact(usage.providerOutputTokens(), request.providerOutputTokens()));
        } catch (ArithmeticException exception) {
            throw new ConflictException("Provider generation accounting overflowed");
        }
        if (!withinBudget(updated, budget)) {
            throw new ConflictException("Investigation provider generation exceeds the remaining budget");
        }
        investigation.put("usage", usageMap(updated));
        investigation.put("accountedProviderGenerationCount", addAccountedGenerations(investigation,
                request.providerGenerationCount()));
    }

    private InvestigationAction action(InvestigationActionRequest request, String projectSlug, String revision) {
        if (request.limits() == null) return null;
        var limits = request.limits();
        if ("SEARCH_EVIDENCE".equals(request.kind())) {
            return new SearchEvidenceRequest(request.actionId(), projectSlug, revision, request.purpose(),
                    request.query(), request.path(), limits.resultLimit(), limits.maximumBytes(), limits.maximumTokens());
        }
        if ("READ_EVIDENCE_RANGE".equals(request.kind()) && request.range() != null) {
            return new ReadEvidenceRangeRequest(request.actionId(), projectSlug, revision, request.purpose(),
                    request.path(), request.range().startLine(), request.range().endLine(), limits.resultLimit(),
                    limits.maximumBytes(), limits.maximumTokens());
        }
        return null;
    }

    private Map<String, Object> metadata(InvestigationCapabilityResult result, InvestigationAction action,
                                          long elapsed, Map<String, Object> investigation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("protocolVersion", "story-context-agent-protocol/v2");
        value.put("executionId", investigation.get("executionId"));
        value.put("parentTaskId", investigation.get("parentTaskId"));
        value.put("actionId", result.actionId());
        value.put("kind", result.kind() == null ? null : result.kind().name());
        value.put("outcome", result.outcome().name());
        value.put("projectSlug", result.projectSlug());
        value.put("revision", result.revision());
        value.put("stopReason", "BUDGET_EXHAUSTED".equals(result.rejectionCode()) ? "BUDGET_EXHAUSTED"
                : result.outcome() == InvestigationCapabilityResult.Outcome.EMPTY
                ? "NO_RELEVANT_EVIDENCE" : result.outcome() == InvestigationCapabilityResult.Outcome.REJECTED
                ? "ACTION_REJECTED" : null);
        value.put("elapsedMillis", Math.min(elapsed, 60_000));
        value.put("evidenceCount", result.evidence().size());
        value.put("evidenceReferences", result.evidence().stream().limit(MAX_STORED_REFERENCES)
                .map(InvestigationCapabilityResult.Evidence::reference).filter(Objects::nonNull).toList());
        if (result.rejectionCode() != null) value.put("rejectionCode", result.rejectionCode());
        if (result.rejectionMessage() != null) value.put("rejectionMessage", bounded(result.rejectionMessage(), 500));
        return value;
    }

    private String requestDigest(InvestigationActionRequest request) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", request.kind());
        value.put("actionId", request.actionId());
        value.put("projectSlug", request.projectSlug());
        value.put("revision", request.revision());
        value.put("purpose", request.purpose());
        value.put("limits", request.limits());
        value.put("query", request.query());
        value.put("path", request.path());
        value.put("range", request.range());
        value.put("protocolVersion", request.protocolVersion());
        value.put("executionId", request.executionId());
        value.put("parentTaskId", request.parentTaskId());
        value.put("correlationId", request.correlationId());
        value.put("provider", request.provider());
        value.put("credentialBindingId", request.credentialBindingId());
        value.put("providerOutputTokens", request.providerOutputTokens());
        value.put("providerGenerationCount", request.providerGenerationCount());
        value.put("providerElapsedMillis", request.providerElapsedMillis());
        return digestService.sha256(digestService.canonicalJson(value));
    }

    private Map<String, Object> canonicalResult(Map<String, Object> metadata,
                                                InvestigationCapabilityResult result,
                                                GroundingUpdate grounding) {
        Map<String, Object> value = new LinkedHashMap<>(metadata);
        value.remove("evidenceCount");
        value.remove("evidenceReferences");
        value.put("evidence", result.evidence().stream().limit(MAX_STORED_REFERENCES)
                .map(this::responseEvidence).toList());
        value.put("groundingContract", boundedGrounding(grounding.contract()));
        value.put("groundingDigest", grounding.digest());
        value.putIfAbsent("rejectionCode", result.rejectionCode());
        value.putIfAbsent("rejectionMessage", result.rejectionMessage());
        return value;
    }

    private Map<String, Object> boundedGrounding(Map<String, Object> source) {
        Map<String, Object> value = new LinkedHashMap<>(source == null ? Map.of() : source);
        Object raw = value.get("allowedGroundingReferences");
        if (raw instanceof List<?> entries) value.put("allowedGroundingReferences", entries.stream()
                .limit(MAX_GROUNDING_REFERENCES).map(this::map).toList());
        return value;
    }

    private Map<String, Object> responseEvidence(InvestigationCapabilityResult.Evidence evidence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("reference", evidence.reference());
        value.put("kind", evidence.kind());
        value.put("path", evidence.path());
        value.put("revision", evidence.revision());
        value.put("sourceType", evidence.sourceType());
        value.put("identifier", evidence.identifier());
        value.put("summary", evidence.summary());
        value.put("trustTier", evidence.trustTier() == null ? null : evidence.trustTier().name());
        value.put("citable", evidence.citable());
        value.put("content", evidence.content());
        value.put("startLine", evidence.startLine());
        value.put("endLine", evidence.endLine());
        return value;
    }

        private GroundingUpdate appendCitableGrounding(AiTask task, Map<String, Object> snapshot,
                                                   InvestigationCapabilityResult result, String projectSlug,
                                                   String revision) {
        Map<String, Object> contract = map(snapshot.get("groundingContract"));
        if (contract.isEmpty()) {
            if (result.evidence().stream().anyMatch(evidence -> citableEvidence(evidence, projectSlug, revision))) {
                throw new ConflictException("Investigation grounding contract cannot represent retrieved evidence");
            }
            return new GroundingUpdate(contract, digestService.sha256(digestService.canonicalJson(contract)));
        }
        String currentDigest = digestService.sha256(digestService.canonicalJson(contract));
        if (result.outcome() != InvestigationCapabilityResult.Outcome.RESULTS
                || !Objects.equals(result.projectSlug(), projectSlug)
                || !Objects.equals(result.revision(), revision)) {
            return new GroundingUpdate(contract, currentDigest);
        }
        Object rawEntries = contract.get("allowedGroundingReferences");
        if (!(rawEntries instanceof List<?>)) {
            if (result.evidence().stream().anyMatch(evidence -> citableEvidence(evidence, projectSlug, revision))) {
                throw new ConflictException("Investigation grounding contract cannot represent retrieved evidence");
            }
            return new GroundingUpdate(contract, currentDigest);
        }

        Map<String, Map<String, Object>> additions = new java.util.TreeMap<>();
        Set<String> existing = new java.util.HashSet<>();
        for (Object raw : (List<?>) rawEntries) {
            if (raw instanceof Map<?, ?> entry && entry.get("ref") instanceof String ref) existing.add(ref);
        }
        for (InvestigationCapabilityResult.Evidence evidence : result.evidence()) {
            if (!citableEvidence(evidence, projectSlug, revision) || existing.contains(evidence.reference())) continue;
            Map<String, Object> entry = groundingEntry(evidence, projectSlug, revision);
            Map<String, Object> previous = additions.putIfAbsent(evidence.reference(), entry);
            if (previous != null && !previous.equals(entry)) additions.remove(evidence.reference());
        }
        if (additions.isEmpty()) {
            return new GroundingUpdate(contract, currentDigest);
        }

        List<Map<String, Object>> updatedEntries = new ArrayList<>();
        for (Object raw : (List<?>) rawEntries) if (raw instanceof Map<?, ?> entry) updatedEntries.add(map(entry));
        int capacity = Math.max(0, MAX_GROUNDING_REFERENCES - updatedEntries.size());
        List<Map<String, Object>> boundedAdditions = additions.values().stream().limit(capacity).toList();
        if (boundedAdditions.size() != additions.size()) {
            throw new ConflictException("Investigation grounding contract capacity is exhausted");
        }
        updatedEntries.addAll(boundedAdditions);
        contract.put("allowedGroundingReferences", updatedEntries);
        Set<String> boundedReferences = boundedAdditions.stream()
                .map(entry -> String.valueOf(entry.get("ref"))).collect(java.util.stream.Collectors.toSet());
        Map<String, Object> updatedMapping = appendReferenceMapping(task.getAiReferenceMappingSnapshot(), boundedReferences);
        if (updatedMapping.isEmpty()) {
            throw new ConflictException("Investigation reference mapping cannot represent retrieved evidence");
        }
        snapshot.put("groundingContract", contract);
        snapshot.put("referenceMapping", updatedMapping);
        task.setAiReferenceMappingSnapshot(updatedMapping);
        String digest = digestService.sha256(digestService.canonicalJson(contract));
        snapshot.put("groundingDigest", digest);
        return new GroundingUpdate(contract, digest);
    }

    private Map<String, Object> withGrounding(Map<String, Object> response, Map<String, Object> snapshot) {
        Map<String, Object> result = new LinkedHashMap<>(response);
        Map<String, Object> contract = map(snapshot.get("groundingContract"));
        result.put("groundingContract", contract);
        result.put("groundingDigest", digestService.sha256(digestService.canonicalJson(contract)));
        return result;
    }

    private boolean citableEvidence(InvestigationCapabilityResult.Evidence evidence,
                                    String projectSlug, String revision) {
        return evidence != null && evidence.citable()
                && nonBlank(evidence.reference()) && nonBlank(evidence.kind())
                && nonBlank(evidence.path()) && nonBlank(evidence.revision())
                && Objects.equals(evidence.revision(), revision)
                && nonBlank(evidence.sourceType()) && nonBlank(evidence.identifier())
                && evidence.trustTier() != null;
    }

    private Map<String, Object> groundingEntry(InvestigationCapabilityResult.Evidence evidence,
                                                String projectSlug, String revision) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("sourceType", evidence.sourceType());
        provenance.put("repositoryLocation", evidence.path());
        provenance.put("originatingFile", evidence.path());
        provenance.put("identifier", evidence.identifier());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "REPOSITORY_EVIDENCE");
        entry.put("ref", evidence.reference());
        entry.put("coreReference", evidence.reference());
        entry.put("taskReference", evidence.reference());
        entry.put("scope", "PROJECT_REVISION");
        entry.put("project", projectSlug);
        entry.put("revision", revision);
        entry.put("provenance", provenance);
        entry.put("resource", evidence.path());
        entry.put("trust", evidence.trustTier().name());
        entry.put("citable", true);
        return entry;
    }

    private Map<String, Object> appendReferenceMapping(Map<String, Object> rawMapping, Set<String> references) {
        if (rawMapping == null || references.isEmpty()) return Map.of();
        try {
            AiReferenceMappingSnapshot current = objectMapper.convertValue(rawMapping, AiReferenceMappingSnapshot.class);
            List<AiReferenceBinding> bindings = new ArrayList<>();
            current.bindings().forEach(binding -> bindings.add(new AiReferenceBinding(
                    new AiReference(binding.type(), binding.ref(), binding.scope()),
                    binding.type().name(), binding.canonicalSourceIdentity(), binding.groundingCapabilities())));
            references.stream().sorted().forEach(ref -> bindings.add(new AiReferenceBinding(
                    new AiReference(AiReferenceType.REPOSITORY_EVIDENCE, ref, AiReferenceScope.PROJECT_REVISION),
                    "REPOSITORY_EVIDENCE", ref, Set.of("EVIDENCE_REFERENCE"))));
            Set<AiReference> architectureReferences = current.architectureKnowledgeReferences().stream()
                    .map(reference -> new AiReference(reference.type(), reference.ref(), reference.scope()))
                    .collect(java.util.stream.Collectors.toSet());
            return AiReferenceMappingSnapshot.from(new AiReferenceRegistry(bindings, architectureReferences)).asMap();
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private Map<String, Object> ledger(List<Map<String, Object>> actions) {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("actionCount", actions.size());
        ledger.put("results", actions.stream().filter(value -> "RESULTS".equals(value.get("outcome"))).count());
        ledger.put("empty", actions.stream().filter(value -> "EMPTY".equals(value.get("outcome"))).count());
        ledger.put("rejected", actions.stream().filter(value -> "REJECTED".equals(value.get("outcome"))).count());
        return ledger;
    }

    private Map<String, Object> usageMap(InvestigationUsage value) {
        return Map.of("steps", value.steps(), "retrievalCalls", value.retrievalCalls(),
                "evidenceItems", value.evidenceItems(), "bytes", value.bytes(), "tokens", value.tokens(),
                "elapsedMillis", value.elapsedMillis(), "providerOutputTokens", value.providerOutputTokens());
    }

    private int accountedGenerations(Map<String, Object> investigation) {
        Object value = investigation.get("accountedProviderGenerationCount");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private int addAccountedGenerations(Map<String, Object> investigation, int increment) {
        try {
            return Math.addExact(accountedGenerations(investigation), increment);
        } catch (ArithmeticException exception) {
            throw new ConflictException("Provider generation accounting overflowed");
        }
    }

    private long accountedProviderElapsed(Map<String, Object> investigation) {
        Object value = investigation.get("accountedProviderElapsedMillis");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private InvestigationUsage usageRecord(InvestigationCapabilityResult result, InvestigationUsage old, long elapsed,
                                           long providerElapsedMillis) {
        long bytes = result.evidence().stream().mapToLong(value -> {
            long content = value.content() == null ? 0 : value.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            long summary = value.summary() == null ? 0 : value.summary().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            return content + summary;
        }).sum();
        long tokens = Math.max(0, bytes / 4);
        return new InvestigationUsage(old.steps() + 1, old.retrievalCalls() + 1,
                old.evidenceItems() + result.evidence().size(), old.bytes() + bytes,
                old.tokens() + tokens, old.elapsedMillis() + elapsed + providerElapsedMillis, old.providerOutputTokens());
    }

    private InvestigationUsage providerOnlyUsage(InvestigationUsage old, long elapsed, int outputTokens) {
        try {
            return new InvestigationUsage(old.steps(), old.retrievalCalls(), old.evidenceItems(), old.bytes(),
                    old.tokens(), Math.addExact(old.elapsedMillis(), elapsed),
                    Math.addExact(old.providerOutputTokens(), outputTokens));
        } catch (ArithmeticException exception) {
            throw new ConflictException("Provider generation accounting overflowed");
        }
    }

    private InvestigationUsage withProviderOutput(InvestigationUsage usage, int providerOutputTokens) {
        if (providerOutputTokens < 0) throw new ConflictException("Provider output token accounting is invalid");
        try {
            return new InvestigationUsage(usage.steps(), usage.retrievalCalls(), usage.evidenceItems(),
                    usage.bytes(), usage.tokens(), usage.elapsedMillis(),
                    Math.addExact(usage.providerOutputTokens(), providerOutputTokens));
        } catch (ArithmeticException exception) {
            throw new ConflictException("Provider output token accounting overflowed");
        }
    }

    private boolean withinBudget(InvestigationUsage usage, InvestigationBudget budget) {
        return usage.steps() <= budget.maximumSteps()
                && usage.retrievalCalls() <= budget.maximumRetrievalCalls()
                && usage.evidenceItems() <= budget.maximumEvidenceItems()
                && usage.bytes() <= budget.maximumBytes()
                && usage.tokens() <= budget.maximumTokens()
                && usage.elapsedMillis() <= budget.maximumElapsedMillis()
                && usage.providerOutputTokens() <= budget.maximumProviderOutputTokens();
    }

    private InvestigationBudget budget(Object value) {
        Map<String, Object> map = map(value);
        return new InvestigationBudget(number(map, "maximumSteps"), number(map, "maximumRetrievalCalls"),
                number(map, "maximumEvidenceItems"), longNumber(map, "maximumBytes"),
                longNumber(map, "maximumTokens"), longNumber(map, "maximumElapsedMillis"),
                number(map, "maximumProviderOutputTokens"));
    }

    private InvestigationUsage usage(Object value) {
        Map<String, Object> map = map(value);
        return new InvestigationUsage(number(map, "steps"), number(map, "retrievalCalls"),
                number(map, "evidenceItems"), longNumber(map, "bytes"), longNumber(map, "tokens"),
                longNumber(map, "elapsedMillis"), number(map, "providerOutputTokens"));
    }

    private String revision(Map<String, Object> snapshot, Map<String, Object> investigation) {
        Map<String, Object> explicit = map(investigation.get("scope"));
        if (string(explicit.get("revision")) != null) return string(explicit.get("revision"));
        Map<String, Object> freshness = map(snapshot.get("freshness"));
        return string(map(freshness.get("sourceRevision")).get("revision"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : objectMapper.convertValue(source, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? objectMapper.convertValue(value, Map.class) : new LinkedHashMap<>(); }

    private List<Map<String, Object>> actionMetadata(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        return list.stream().filter(Map.class::isInstance).map(item -> map(item)).limit(MAX_STORED_ACTIONS).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private UUID uuid(Object value) { try { return value == null ? null : UUID.fromString(String.valueOf(value)); } catch (IllegalArgumentException ex) { return null; } }
    private String string(Object value) { return value instanceof String s && !s.isBlank() ? s : null; }
    private int number(Map<String, Object> map, String key) { Object value = map.get(key); if (!(value instanceof Number n)) throw new ConflictException("Investigation budget is invalid"); return n.intValue(); }
    private long longNumber(Map<String, Object> map, String key) { Object value = map.get(key); if (!(value instanceof Number n)) throw new ConflictException("Investigation budget is invalid"); return n.longValue(); }
    private String bounded(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    private boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    private EntityNotFoundException notFound(UUID id) { return new EntityNotFoundException("Story Context task", id); }

    public record ActionResponse(UUID aiTaskId, boolean duplicate, Map<String, Object> result) { }

    private record GroundingUpdate(Map<String, Object> contract, String digest) { }
}
