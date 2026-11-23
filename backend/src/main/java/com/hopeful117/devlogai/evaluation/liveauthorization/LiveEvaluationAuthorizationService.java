package com.hopeful117.devlogai.evaluation.liveauthorization;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparation;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparationRepository;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparationStatus;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;

@Service
public class LiveEvaluationAuthorizationService {
    private static final String SCOPE = "MCP_LIVE_EVALUATION_AUTHORIZE";
    private static final String EXECUTE_SCOPE = "MCP_LIVE_EVALUATION_EXECUTE";
    private static final Duration REQUEST_TTL = Duration.ofMinutes(10);
    private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(15);

    private final LiveEvaluationPreparationRepository preparationRepository;
    private final LiveEvaluationAuthorizationRepository authorizationRepository;
    private final CoreProjectAuthorizationService authorizationService;
    private final Clock clock;
    private final String mcpHumanConfirmationSecret;

    public LiveEvaluationAuthorizationService(
            LiveEvaluationPreparationRepository preparationRepository,
            LiveEvaluationAuthorizationRepository authorizationRepository,
            CoreProjectAuthorizationService authorizationService,
            Clock clock,
            @Value("${devlog.mcp.human-confirmation.secret:${devlog.service-auth.secret:}}") String mcpHumanConfirmationSecret) {
        this.preparationRepository = preparationRepository;
        this.authorizationRepository = authorizationRepository;
        this.authorizationService = authorizationService;
        this.clock = clock;
        this.mcpHumanConfirmationSecret = mcpHumanConfirmationSecret;
    }

    @Transactional
    public LiveEvaluationAuthorizationResponse request(
            LiveEvaluationAuthorizationRequest request, String idempotencyKey, AuthenticatedPrincipal principal) {
        requirePrincipal(principal);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required and bounded");
        }
        LiveEvaluationPreparation preparation = preparationRepository.findById(request.planId())
                .orElseThrow(() -> new EntityNotFoundException("Live evaluation preparation", request.planId()));
        authorize(principal, preparation);
        if (!request.planDigest().equals(preparation.getPlanDigest())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "plan digest does not match preparation");
        }
        if (preparation.getStatus() != LiveEvaluationPreparationStatus.READY
                || !preparation.getExpiresAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "preparation is not ready for authorization");
        }
        String keyHash = sha256(idempotencyKey);
        authorizationRepository.acquireAuthorizationLock(keyHash + ":" + preparation.getId() + ":" + principal.principalId());
        Optional<LiveEvaluationAuthorization> existing = authorizationRepository.findForUpdate(
                keyHash, preparation.getId(), principal.principalId());
        existing.ifPresent(value -> {
            if (!value.getPlanDigest().equals(request.planDigest())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "idempotency key is bound to a different plan");
            }
        });
        if (existing.isPresent()) return LiveEvaluationAuthorizationResponse.from(existing.get());
        Instant now = clock.instant();
        LiveEvaluationAuthorization authorization = authorizationRepository.save(LiveEvaluationAuthorization.builder()
                .preparation(preparation)
                .requesterId(principal.principalId())
                .idempotencyKeyHash(keyHash)
                .planDigest(request.planDigest())
                .status(LiveEvaluationAuthorizationStatus.PENDING)
                .createdAt(now)
                .expiresAt(now.plus(REQUEST_TTL))
                .build());
        return LiveEvaluationAuthorizationResponse.from(authorization);
    }

    @Transactional
    public LiveEvaluationAuthorizationResponse approve(UUID authorizationId, String planDigest,
                                                       AuthenticatedPrincipal principal) {
        return approve(authorizationId, planDigest, principal, null);
    }

    @Transactional
    public LiveEvaluationAuthorizationResponse approve(UUID authorizationId, String planDigest,
                                                       AuthenticatedPrincipal principal,
                                                       String mcpHumanConfirmation) {
        requirePrincipal(principal);
        boolean mcpConfirmed = principal.kind() == PrincipalKind.AI_AGENT
                && verifyMcpHumanConfirmation(authorizationId, planDigest, mcpHumanConfirmation);
        if (principal.kind() != PrincipalKind.HUMAN && !mcpConfirmed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a human principal may approve execution");
        }
        LiveEvaluationAuthorization authorization = authorizationRepository.findForUpdate(authorizationId)
                .orElseThrow(() -> new EntityNotFoundException("Live evaluation authorization", authorizationId));
        authorize(principal, authorization.getPreparation());
        if (!authorization.getPlanDigest().equals(planDigest)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "plan digest does not match authorization request");
        }
        Instant now = clock.instant();
        if (authorization.getStatus() == LiveEvaluationAuthorizationStatus.APPROVED
                && !authorization.getExpiresAt().isAfter(now)) {
            markExpired(authorization);
            throw new ResponseStatusException(HttpStatus.GONE, "authorization request has expired");
        }
        if (authorization.getStatus() == LiveEvaluationAuthorizationStatus.APPROVED) {
            return LiveEvaluationAuthorizationResponse.from(authorization);
        }
        if (authorization.getStatus() != LiveEvaluationAuthorizationStatus.PENDING
                || !authorization.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "authorization request is no longer pending");
        }
        LiveEvaluationPreparation preparation = authorization.getPreparation();
        Map<String, Object> artifact = new LinkedHashMap<>(preparation.getPlanSnapshot());
        Instant expiresAt = now.plus(AUTHORIZATION_TTL).isBefore(preparation.getExpiresAt())
                ? now.plus(AUTHORIZATION_TTL) : preparation.getExpiresAt();
        artifact.put("artifactVersion", "bounded-live-evaluation-authorization-v1");
        artifact.put("issuedBy", "CORE");
        artifact.put("authorizationStatus", "AUTHORIZED");
        artifact.put("authorizationId", authorization.getId().toString());
        artifact.put("planId", preparation.getId().toString());
        artifact.put("planDigest", preparation.getPlanDigest());
        artifact.put("approverId", principal.principalId());
        artifact.put("approvedAt", now.toString());
        artifact.put("expiresAtEpochSeconds", expiresAt.getEpochSecond());
        artifact.remove("blockers");
        String approverId = mcpConfirmed
                ? "mcp-elicitation:" + principal.principalId()
                : principal.principalId();
        artifact.put("approverId", approverId);
        authorization = updateApproved(authorization, approverId, now, expiresAt, artifact);
        return LiveEvaluationAuthorizationResponse.from(authorization);
    }

    @Transactional
    public LiveEvaluationAuthorizationResponse consume(UUID authorizationId, String planDigest,
                                                        String executionIdempotencyKey,
                                                        AuthenticatedPrincipal principal) {
        requirePrincipal(principal);
        if (executionIdempotencyKey == null || executionIdempotencyKey.isBlank()
                || executionIdempotencyKey.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "execution idempotency key is required and bounded");
        }
        LiveEvaluationAuthorization authorization = authorizationRepository.findForUpdate(authorizationId)
                .orElseThrow(() -> new EntityNotFoundException("Live evaluation authorization", authorizationId));
        authorizeExecution(principal, authorization.getPreparation());
        if (!authorization.getPlanDigest().equals(planDigest)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "plan digest does not match authorization");
        }
        String keyHash = sha256(executionIdempotencyKey);
        if (authorization.getStatus() == LiveEvaluationAuthorizationStatus.CONSUMED) {
            if (keyHash.equals(authorization.getConsumptionKeyHash())) {
                return LiveEvaluationAuthorizationResponse.from(authorization);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "authorization has already been consumed");
        }
        Instant now = clock.instant();
        if (authorization.getStatus() == LiveEvaluationAuthorizationStatus.APPROVED
                && authorization.getExpiresAt().isAfter(now)) {
            LiveEvaluationAuthorization consumed = LiveEvaluationAuthorization.builder()
                    .id(authorization.getId()).preparation(authorization.getPreparation())
                    .requesterId(authorization.getRequesterId()).approverId(authorization.getApproverId())
                    .idempotencyKeyHash(authorization.getIdempotencyKeyHash())
                    .planDigest(authorization.getPlanDigest()).status(LiveEvaluationAuthorizationStatus.CONSUMED)
                    .artifactSnapshot(authorization.getArtifactSnapshot()).createdAt(authorization.getCreatedAt())
                    .approvedAt(authorization.getApprovedAt()).consumptionKeyHash(keyHash)
                    .consumerId(principal.principalId()).consumedAt(now).expiresAt(authorization.getExpiresAt())
                    .build();
            return LiveEvaluationAuthorizationResponse.from(authorizationRepository.save(consumed));
        }
        if (authorization.getStatus() == LiveEvaluationAuthorizationStatus.APPROVED) {
            markExpired(authorization);
        }
        throw new ResponseStatusException(HttpStatus.GONE, "authorization is not available for consumption");
    }

    @Transactional(readOnly = true)
    public LiveEvaluationAuthorizationResponse get(UUID authorizationId, AuthenticatedPrincipal principal) {
        LiveEvaluationAuthorization authorization = authorizationRepository.findById(authorizationId)
                .orElseThrow(() -> new EntityNotFoundException("Live evaluation authorization", authorizationId));
        authorize(principal, authorization.getPreparation());
        return LiveEvaluationAuthorizationResponse.from(authorization);
    }

    private LiveEvaluationAuthorization updateApproved(LiveEvaluationAuthorization current, String approverId,
                                                        Instant approvedAt, Instant expiresAt,
                                                        Map<String, Object> artifact) {
        LiveEvaluationAuthorization replacement = LiveEvaluationAuthorization.builder()
                .id(current.getId()).preparation(current.getPreparation()).requesterId(current.getRequesterId())
                .approverId(approverId).idempotencyKeyHash(current.getIdempotencyKeyHash())
                .planDigest(current.getPlanDigest()).status(LiveEvaluationAuthorizationStatus.APPROVED)
                .artifactSnapshot(Collections.unmodifiableMap(artifact)).createdAt(current.getCreatedAt())
                .approvedAt(approvedAt).expiresAt(expiresAt).build();
        return authorizationRepository.save(replacement);
    }

    private void markExpired(LiveEvaluationAuthorization current) {
        authorizationRepository.save(LiveEvaluationAuthorization.builder()
                .id(current.getId()).preparation(current.getPreparation()).requesterId(current.getRequesterId())
                .approverId(current.getApproverId()).idempotencyKeyHash(current.getIdempotencyKeyHash())
                .planDigest(current.getPlanDigest()).status(LiveEvaluationAuthorizationStatus.EXPIRED)
                .artifactSnapshot(current.getArtifactSnapshot()).createdAt(current.getCreatedAt())
                .approvedAt(current.getApprovedAt()).consumptionKeyHash(current.getConsumptionKeyHash())
                .consumerId(current.getConsumerId()).consumedAt(current.getConsumedAt())
                .expiresAt(current.getExpiresAt()).build());
    }

    private void authorize(AuthenticatedPrincipal principal, LiveEvaluationPreparation preparation) {
        if (!principal.hasDelegatedScope(SCOPE)
                || !authorizationService.isAuthorized(principal, preparation.getProject(), SCOPE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Live evaluation authorization is not permitted");
        }
    }

    private void authorizeExecution(AuthenticatedPrincipal principal, LiveEvaluationPreparation preparation) {
        if (!principal.hasDelegatedScope(EXECUTE_SCOPE)
                || !authorizationService.isAuthorized(principal, preparation.getProject(), EXECUTE_SCOPE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Live evaluation execution is not permitted");
        }
    }

    private void requirePrincipal(AuthenticatedPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
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

    private boolean verifyMcpHumanConfirmation(UUID authorizationId, String planDigest, String proof) {
        if (proof == null || proof.isBlank() || mcpHumanConfirmationSecret == null
                || mcpHumanConfirmationSecret.isBlank()) return false;
        String[] parts = proof.split("\\.", -1);
        if (parts.length != 2) return false;
        long timestamp;
        try {
            timestamp = Long.parseLong(parts[0]);
        } catch (NumberFormatException exception) {
            return false;
        }
        if (Math.abs(clock.instant().getEpochSecond() - timestamp) > 120) return false;
        String payload = authorizationId + "\n" + planDigest + "\n" + timestamp;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(mcpHumanConfirmationSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
