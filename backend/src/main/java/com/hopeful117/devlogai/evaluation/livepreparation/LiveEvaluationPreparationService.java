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
    private final ProjectRepository projectRepository;
    private final CoreProjectAuthorizationService authorizationService;
    private final LiveEvaluationPreparationRepository repository;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LiveEvaluationContractRegistry contracts;

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
        Optional<LiveEvaluationContract> match = contracts.find(
                request.manifestId(), request.manifestVersion(), request.manifestDigest());
        if (match.isEmpty()) {
            blockers.add("manifest identity is not an approved live evaluation contract");
        }
        LiveEvaluationContract contract = match.orElse(contracts.fallback());
        if (!contract.benchmarkId().equals(request.benchmarkId())
                || !contract.benchmarkVersion().equals(request.benchmarkVersion())
                || !contract.hasCase(request.caseId())) {
            blockers.add("benchmark or case identity is not in the approved contract");
        }
        if (!SHA256.matcher(request.manifestDigest()).matches()) blockers.add("manifest digest is invalid");
        if (!SHA256.matcher(request.oracleDigest()).matches()) blockers.add("oracle digest is invalid");
        if (!REVISION.matcher(request.sourceRevision()).matches()) blockers.add("source revision is invalid");
        if (!contract.sourceRevision().equals(request.sourceRevision())) {
            blockers.add("source revision is not the approved contract snapshot");
        }
        if (!request.oracleDigest().equals(contract.caseOracleDigests().get(request.caseId()))) {
            blockers.add("oracle is not the approved oracle for the case");
        }
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
        if (!contract.hasCase(request.caseId())
                || !contract.observationIds(request.caseId()).equals(request.observationIds())) {
            blockers.add("observation set is not the contract set for the case");
        }
        if (!contract.operationalLimits().equals(request.operationalLimits())) {
            blockers.add("operational limits do not match the approved contract");
        }
        if (!contract.runtimeIdentities().equals(request.runtimeIdentities())) {
            blockers.add("runtime identities do not match the approved contract");
        }
        if (!"APPROVED".equals(request.pricingContract().get("status"))
                || !"CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED".equals(request.pricingContract().get("billingModel"))
                || !"NOT_APPLICABLE".equals(request.pricingContract().get("currency"))
                || !"OPERATIONAL_LIMITS_ONLY".equals(request.pricingContract().get("costCeilingPolicy"))
                || !contract.pricingDigest().equals(request.pricingContract().get("digest"))
                || !contract.pricingVersion().equals(request.pricingContract().get("version"))
                || !contract.provider().equals(request.pricingContract().get("provider"))
                || !"CHATGPT_OAUTH".equals(request.pricingContract().get("authentication"))) {
            blockers.add("non-metered OAuth pricing contract is not approved");
        }
        if (!contract.provider().equals(request.provider()) || !contract.model().equals(request.model())) {
            blockers.add("provider or model is not the approved contract");
        }
        if (contract.maxCampaignObservations() != null
                && contract.batchObservationCount() > contract.maxCampaignObservations()) {
            blockers.add("case batch exceeds the campaign observation ceiling");
        }
        if (contract.maxCampaignWallClock() != null
                && contract.batchWallClock().compareTo(contract.maxCampaignWallClock()) > 0) {
            blockers.add("case batch exceeds the campaign wall-clock ceiling");
        }
        return List.copyOf(blockers);
    }

    private Map<String, Object> plan(LiveEvaluationPreparationRequest request, List<String> blockers,
                                     boolean runtimeVerified, Instant expiresAt) {
        LiveEvaluationContract contract = contracts.find(
                request.manifestId(), request.manifestVersion(), request.manifestDigest())
                .orElse(contracts.fallback());
        String caseId = contract.hasCase(request.caseId())
                ? request.caseId() : contract.caseOracleDigests().keySet().iterator().next();
        List<String> observations = contract.observationIds(caseId);
        Map<String, Object> pricing = Map.of(
                "status", "APPROVED", "version", contract.pricingVersion(),
                "provider", contract.provider(), "authentication", "CHATGPT_OAUTH",
                "currency", "NOT_APPLICABLE", "billingModel", "CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED",
                "costCeilingPolicy", "OPERATIONAL_LIMITS_ONLY", "digest", contract.pricingDigest());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", "bounded-live-evaluation-preparation-v1");
        result.put("contractId", contract.contractId());
        result.put("benchmarkId", contract.benchmarkId());
        result.put("benchmarkVersion", contract.benchmarkVersion());
        result.put("caseId", caseId);
        result.put("manifestId", contract.manifestId());
        result.put("manifestVersion", contract.manifestVersion());
        result.put("manifestDigest", contract.manifestDigest());
        result.put("campaignId", contract.contractId());
        result.put("oracleStatus", "ACCEPTED");
        result.put("oracleDigest", contract.caseOracleDigests().get(caseId));
        result.put("sourceRevision", contract.sourceRevision());
        result.put("runtimeRevision", request.runtimeRevision());
        result.put("runtimeAttestation", Map.of(
                "runtimeRevision", request.runtimeRevision(),
                "coreVerified", runtimeVerified));
        result.put("runtimeIdentities", contract.runtimeIdentities());
        result.put("operationalLimits", contract.operationalLimits());
        result.put("authorizedObservationIds", observations);
        result.put("authorizedConditions", contract.conditions());
        result.put("budgets", contract.operationalLimits());
        result.put("limits", contract.operationalLimits());
        result.put("provider", contract.provider());
        result.put("model", contract.model());
        result.put("pricingContract", pricing);
        result.put("pricingStatus", "APPROVED");
        result.put("pricingVersion", contract.pricingVersion());
        result.put("pricingDigest", contract.pricingDigest());
        result.put("costCeiling", Map.of("currency", "NOT_APPLICABLE", "maxAmount", "NOT_MEASURED"));
        if (contract.hasCampaignCeiling()) {
            Map<String, Object> ceiling = new LinkedHashMap<>();
            ceiling.put("oneConsumptionPerCase", contract.oneConsumptionPerCase());
            ceiling.put("batchObservations", contract.batchObservationCount());
            ceiling.put("batchWallClockSeconds", contract.batchWallClock().toSeconds());
            if (contract.maxCampaignObservations() != null) {
                ceiling.put("maxObservations", contract.maxCampaignObservations());
            }
            if (contract.maxCampaignWallClock() != null) {
                ceiling.put("maxWallClockSeconds", contract.maxCampaignWallClock().toSeconds());
            }
            result.put("campaignCeiling", ceiling);
        }
        result.put("artifactNamespace", request.artifactNamespace());
        result.put("observationIds", observations);
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
