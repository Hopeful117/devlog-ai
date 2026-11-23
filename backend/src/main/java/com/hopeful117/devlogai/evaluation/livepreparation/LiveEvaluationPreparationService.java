package com.hopeful117.devlogai.evaluation.livepreparation;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LiveEvaluationPreparationService {
    private static final String SCOPE = "MCP_LIVE_EVALUATION_PREPARE";
    private static final Duration PLAN_TTL = Duration.ofHours(1);
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern REVISION = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
    private static final Pattern ARTIFACT_NAMESPACE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_/-]{0,119}");
    private static final String MANIFEST_ID = "devlog-ai-abc-first-test";
    private static final String MANIFEST_VERSION = "1.1.0";
    private static final String MANIFEST_DIGEST = "bcd538cc5a40c6d270098145181bdc421caee9cc1f215d4692d1168726482465";
    private static final String SOURCE_REVISION = "fa33f03d9af42e3ac6e64112ecebe38d86de7b9b";
    private static final String ORACLE_DIGEST = "2127d4ce224b1f39f5d8f113bb0b24b3dab7e9443fcb101600a774341c8d6d4e";
    private static final String PRICING_DIGEST = "0b48079816061d0660476bf2bb5d08572ff5f3378be7d5f3ba77d4013c229fbd";
    private static final List<String> OBSERVATIONS = List.of(
            "Q1-A-DEVLOG-CURRENT-r1",
            "Q1-B-DIRECT-INVESTIGATOR-r1",
            "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1");
    private static final List<String> CONDITIONS = List.of(
            "A_DEVLOG_CURRENT", "B_DIRECT_INVESTIGATOR", "C_DEVLOG_ENGINEERING_ANALYST");
    private static final Map<String, Object> OPERATIONAL_LIMITS = Map.of(
            "maxModelTurns", 8,
            "maxProviderCalls", 8,
            "maxToolOrActionOperations", 8,
            "maxReadBytesPerOperation", 64000,
            "maxReadTokensPerOperation", 8000,
            "maxWallClockSeconds", 120,
            "maxOutputTokensPerResponse", 32768);
    private static final Map<String, Object> RUNTIME_IDENTITIES = Map.of(
            "A", Map.of("runtime", "java-core-story-context-one-shot", "entrypoint",
                    "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java",
                    "promptVersion", "story-context-agent-protocol/v1", "answerContract", "story-context-analysis-result-v1"),
            "B", Map.of("runtime", "comparative-v4-direct-investigator", "entrypoint",
                    "ai-engine/evaluations/comparative_baseline_v4/runtime.py", "configuration",
                    "docs/evaluation/devlog-ai-q1-b-direct-config-v1.json", "promptVersion",
                    "comparative-v4-common-answer-1.0.0", "answerContract", "comparative-v4-common-answer-1.0.0"),
            "C", Map.of("runtime", "python-core-bounded-investigation", "entrypoint",
                    "ai-engine/app/services/story_context_analysis_generation_service.py", "promptVersion",
                    "story-context-agent-protocol/v2", "answerContract", "story-context-analysis-result-v1"));

    private final ProjectRepository projectRepository;
    private final CoreProjectAuthorizationService authorizationService;
    private final LiveEvaluationPreparationRepository repository;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public LiveEvaluationPreparationResponse prepare(
            String projectSlug, String idempotencyKey, AuthenticatedPrincipal principal,
            LiveEvaluationPreparationRequest request) {
        if (principal == null || !principal.hasDelegatedScope(SCOPE)) {
            return unauthorized(projectSlug);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required and bounded");
        }
        Project project = projectRepository.findBySlug(projectSlug)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectSlug));
        if (!authorizationService.isAuthorized(principal, project, SCOPE)) {
            return unauthorized(projectSlug);
        }
        String keyHash = sha256(idempotencyKey);
        String scopeHash = sha256(project.getId() + ":" + principal.principalId() + ":" + SCOPE);
        repository.acquirePreparationLock(keyHash + ":" + scopeHash);
        Optional<LiveEvaluationPreparation> existing = repository.findForUpdate(keyHash, scopeHash);
        String requestDigest = digest(request);
        if (existing.isPresent()) {
            if (!existing.get().getRequestDigest().equals(requestDigest)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "idempotency key is bound to a different request");
            }
            return LiveEvaluationPreparationResponse.from(existing.get());
        }
        boolean runtimeVerified = verifyRuntimeRevision(project, request.runtimeRevision());
        List<String> blockers = blockers(request, runtimeVerified);
        Instant now = clock.instant();
        Map<String, Object> plan = plan(request, blockers, runtimeVerified, now.plus(PLAN_TTL));
        String planDigest = digest(plan);
        LiveEvaluationPreparation preparation = repository.save(LiveEvaluationPreparation.builder()
                .project(project)
                .principalId(principal.principalId())
                .idempotencyKeyHash(keyHash)
                .idempotencyScopeHash(scopeHash)
                .requestDigest(requestDigest)
                .planDigest(planDigest)
                .status(blockers.isEmpty() ? LiveEvaluationPreparationStatus.READY : LiveEvaluationPreparationStatus.BLOCKED)
                .planSnapshot(plan)
                .expiresAt(now.plus(PLAN_TTL))
                .build());
        return LiveEvaluationPreparationResponse.from(preparation);
    }

    @Transactional(readOnly = true)
    public LiveEvaluationPreparationResponse get(UUID planId, AuthenticatedPrincipal principal) {
        LiveEvaluationPreparation value = repository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("Live evaluation preparation", planId));
        if (principal == null || !principal.hasDelegatedScope(SCOPE)
                || !authorizationService.isAuthorized(principal, value.getProject(), SCOPE)) {
            throw new EntityNotFoundException("Live evaluation preparation", planId);
        }
        return LiveEvaluationPreparationResponse.from(value);
    }

    private LiveEvaluationPreparationResponse unauthorized(String projectSlug) {
        Instant now = clock.instant();
        return new LiveEvaluationPreparationResponse(null, projectSlug,
                LiveEvaluationPreparationStatus.NOT_AUTHORIZED, null, null, now, now,
                Map.of("capability", "bounded-live-evaluation-preparation", "providerCalls", 0,
                        "networkCalls", 0), List.of("project or delegation authorization is not established"));
    }

    private List<String> blockers(LiveEvaluationPreparationRequest request, boolean runtimeVerified) {
        List<String> blockers = new ArrayList<>();
        if (!MANIFEST_ID.equals(request.manifestId()) || !MANIFEST_VERSION.equals(request.manifestVersion())
                || !MANIFEST_DIGEST.equals(request.manifestDigest())) {
            blockers.add("manifest identity is not the approved Q1 contract");
        }
        if (!"devlog-ai-abc".equals(request.benchmarkId()) || !"1.0.0".equals(request.benchmarkVersion())
                || !"Q1".equals(request.caseId())) {
            blockers.add("benchmark or case identity is not the approved Q1 contract");
        }
        if (!SHA256.matcher(request.manifestDigest()).matches()) blockers.add("manifest digest is invalid");
        if (!SHA256.matcher(request.oracleDigest()).matches()) blockers.add("oracle digest is invalid");
        if (!REVISION.matcher(request.sourceRevision()).matches()) blockers.add("source revision is invalid");
        if (!SOURCE_REVISION.equals(request.sourceRevision())) blockers.add("source revision is not the approved Q1 snapshot");
        if (!ORACLE_DIGEST.equals(request.oracleDigest())) blockers.add("oracle is not the approved Q1 oracle");
        if (!REVISION.matcher(request.runtimeRevision()).matches()) blockers.add("runtime revision is invalid");
        if (!Objects.equals(request.runtimeAttestation().get("runtimeRevision"), request.runtimeRevision())) {
            blockers.add("runtime attestation revision does not match runtime revision");
        }
        if (!runtimeVerified) blockers.add("runtime worktree cannot be independently verified by Core");
        if (!"ACCEPTED".equals(request.oracleStatus())) {
            blockers.add("oracle is not explicitly accepted");
        }
        if (!ARTIFACT_NAMESPACE.matcher(request.artifactNamespace()).matches()
                || request.artifactNamespace().contains("..")
                || request.artifactNamespace().startsWith("/")) {
            blockers.add("artifact namespace is invalid");
        }
        if (!OBSERVATIONS.equals(request.observationIds())) {
            blockers.add("observation set is not the approved Q1 set");
        }
        if (!OPERATIONAL_LIMITS.equals(request.operationalLimits())) {
            blockers.add("operational limits do not match the approved Q1 contract");
        }
        if (!RUNTIME_IDENTITIES.equals(request.runtimeIdentities())) {
            blockers.add("runtime identities do not match the approved Q1 contract");
        }
        if (!"APPROVED".equals(request.pricingContract().get("status"))
                || !"CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED".equals(request.pricingContract().get("billingModel"))
                || !"NOT_APPLICABLE".equals(request.pricingContract().get("currency"))
                || !"OPERATIONAL_LIMITS_ONLY".equals(request.pricingContract().get("costCeilingPolicy"))
                || !PRICING_DIGEST.equals(request.pricingContract().get("digest"))
                || !"q1-chatgpt-oauth-pricing-v1".equals(request.pricingContract().get("version"))
                || !"openai_chatgpt".equals(request.pricingContract().get("provider"))
                || !"CHATGPT_OAUTH".equals(request.pricingContract().get("authentication"))) {
            blockers.add("non-metered OAuth pricing contract is not approved");
        }
        if (!"openai_chatgpt".equals(request.provider()) || !"gpt-5.6-luna".equals(request.model())) {
            blockers.add("provider or model is not the approved Q1 contract");
        }
        return List.copyOf(blockers);
    }

    private Map<String, Object> plan(LiveEvaluationPreparationRequest request, List<String> blockers,
                                     boolean runtimeVerified, Instant expiresAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", "bounded-live-evaluation-preparation-v1");
        result.put("benchmarkId", "devlog-ai-abc");
        result.put("benchmarkVersion", "1.0.0");
        result.put("caseId", "Q1");
        result.put("manifestId", MANIFEST_ID);
        result.put("manifestVersion", MANIFEST_VERSION);
        result.put("manifestDigest", MANIFEST_DIGEST);
        result.put("campaignId", MANIFEST_ID);
        result.put("oracleStatus", "ACCEPTED");
        result.put("oracleDigest", ORACLE_DIGEST);
        result.put("sourceRevision", SOURCE_REVISION);
        result.put("runtimeRevision", request.runtimeRevision());
        result.put("runtimeAttestation", Map.of(
                "runtimeRevision", request.runtimeRevision(),
                "coreVerified", runtimeVerified));
        result.put("runtimeIdentities", RUNTIME_IDENTITIES);
        result.put("operationalLimits", OPERATIONAL_LIMITS);
        result.put("authorizedObservationIds", OBSERVATIONS);
        result.put("authorizedConditions", CONDITIONS);
        result.put("budgets", OPERATIONAL_LIMITS);
        result.put("limits", OPERATIONAL_LIMITS);
        result.put("provider", "openai_chatgpt");
        result.put("model", "gpt-5.6-luna");
        result.put("pricingContract", Map.of(
                "status", "APPROVED", "version", "q1-chatgpt-oauth-pricing-v1",
                "provider", "openai_chatgpt", "authentication", "CHATGPT_OAUTH",
                "currency", "NOT_APPLICABLE", "billingModel", "CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED",
                "costCeilingPolicy", "OPERATIONAL_LIMITS_ONLY", "digest", PRICING_DIGEST));
        result.put("pricingStatus", "APPROVED");
        result.put("pricingVersion", "q1-chatgpt-oauth-pricing-v1");
        result.put("pricingDigest", PRICING_DIGEST);
        result.put("costCeiling", Map.of("currency", "NOT_APPLICABLE", "maxAmount", "NOT_MEASURED"));
        result.put("artifactNamespace", request.artifactNamespace());
        result.put("observationIds", OBSERVATIONS);
        result.put("expiresAt", expiresAt.toString());
        result.put("blockers", blockers);
        result.put("providerCalls", 0);
        result.put("networkCalls", 0);
        return Collections.unmodifiableMap(result);
    }

    private boolean verifyRuntimeRevision(Project project, String runtimeRevision) {
        try {
            Source source = sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())
                    .stream()
                    .filter(candidate -> candidate.getType() == SourceType.GIT_REPOSITORY)
                    .findFirst()
                    .orElse(null);
            if (source == null) return false;
            SynchronizedWorkspace workspace = workspaceManager.synchronize(source, runtimeRevision);
            return runtimeRevision.equals(workspace.resolvedRevision());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String digest(Object value) {
        try {
            Object canonical = canonical(value);
            return sha256(objectMapper.writeValueAsString(canonical));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot canonicalize live preparation", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonical(item)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) return collection.stream().map(this::canonical).toList();
        return value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
