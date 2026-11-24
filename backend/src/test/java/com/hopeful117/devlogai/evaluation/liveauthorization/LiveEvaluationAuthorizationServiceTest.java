package com.hopeful117.devlogai.evaluation.liveauthorization;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparation;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparationRepository;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparationStatus;
import com.hopeful117.devlogai.project.entity.Project;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LiveEvaluationAuthorizationServiceTest {
    @Test
    void humanApprovalProducesAnExactCoreArtifact() {
        UUID planId = UUID.randomUUID();
        UUID authorizationId = UUID.randomUUID();
        String digest = "a".repeat(64);
        Instant now = Instant.parse("2026-11-23T05:00:00Z");
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        LiveEvaluationPreparation preparation = LiveEvaluationPreparation.builder()
                .id(planId).project(project).principalId("human")
                .requestDigest("b".repeat(64)).planDigest(digest)
                .status(LiveEvaluationPreparationStatus.READY)
                .planSnapshot(Map.of("provider", "openai_chatgpt", "model", "gpt-5.6-luna",
                        "blockers", java.util.List.of(), "providerCalls", 0, "networkCalls", 0,
                        "campaignId", "devlog-ai-abc-first-test",
                        "authorizedConditions", java.util.List.of("A_DEVLOG_CURRENT", "B_DIRECT_INVESTIGATOR", "C_DEVLOG_ENGINEERING_ANALYST"),
                        "pricingStatus", "APPROVED",
                        "costCeiling", Map.of("currency", "NOT_APPLICABLE", "maxAmount", "NOT_MEASURED")))
                .createdAt(now).expiresAt(now.plusSeconds(3600)).build();
        LiveEvaluationAuthorization pending = LiveEvaluationAuthorization.builder()
                .id(authorizationId).preparation(preparation).requesterId("requester")
                .idempotencyKeyHash("c".repeat(64)).planDigest(digest)
                .status(LiveEvaluationAuthorizationStatus.PENDING)
                .createdAt(now).expiresAt(now.plusSeconds(600)).build();

        LiveEvaluationPreparationRepository preparations = mock(LiveEvaluationPreparationRepository.class);
        LiveEvaluationAuthorizationRepository authorizations = mock(LiveEvaluationAuthorizationRepository.class);
        CoreProjectAuthorizationService access = mock(CoreProjectAuthorizationService.class);
        when(authorizations.findById(any())).thenReturn(Optional.of(pending));
        when(authorizations.findForUpdate(authorizationId)).thenReturn(Optional.of(pending));
        when(authorizations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(access.isAuthorized(any(), eq(project), anyString())).thenReturn(true);
        LiveEvaluationAuthorizationService service = new LiveEvaluationAuthorizationService(
                preparations, authorizations, access, Clock.fixed(now, ZoneOffset.UTC));

        LiveEvaluationAuthorizationResponse result = service.approve(
                authorizationId, digest, new AuthenticatedPrincipal("human", PrincipalKind.HUMAN, "test"));

        assertEquals(LiveEvaluationAuthorizationStatus.APPROVED, result.status());
        assertEquals("AUTHORIZED", result.artifact().get("authorizationStatus"));
        assertEquals("CORE", result.artifact().get("issuedBy"));
        assertEquals(planId.toString(), result.artifact().get("planId"));
        assertEquals(digest, result.artifact().get("planDigest"));
        assertEquals("devlog-ai-abc-first-test", result.artifact().get("campaignId"));
        assertEquals(java.util.List.of("A_DEVLOG_CURRENT", "B_DIRECT_INVESTIGATOR", "C_DEVLOG_ENGINEERING_ANALYST"),
                result.artifact().get("authorizedConditions"));
        assertEquals("APPROVED", result.artifact().get("pricingStatus"));
        assertEquals("NOT_MEASURED", ((Map<?, ?>) result.artifact().get("costCeiling")).get("maxAmount"));
        verify(authorizations).save(any());
    }

    @Test
    void idempotencyIsScopedToTheRequestingPrincipal() {
        UUID planId = UUID.randomUUID();
        String digest = "a".repeat(64);
        Instant now = Instant.parse("2026-11-23T05:00:00Z");
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        LiveEvaluationPreparation preparation = LiveEvaluationPreparation.builder()
                .id(planId).project(project).principalId("requester-a")
                .requestDigest("b".repeat(64)).planDigest(digest)
                .status(LiveEvaluationPreparationStatus.READY)
                .planSnapshot(Map.of()).createdAt(now).expiresAt(now.plusSeconds(3600)).build();
        LiveEvaluationPreparationRepository preparations = mock(LiveEvaluationPreparationRepository.class);
        LiveEvaluationAuthorizationRepository authorizations = mock(LiveEvaluationAuthorizationRepository.class);
        CoreProjectAuthorizationService access = mock(CoreProjectAuthorizationService.class);
        when(preparations.findById(planId)).thenReturn(Optional.of(preparation));
        when(authorizations.findForUpdate(any(), eq(planId), any())).thenReturn(Optional.empty());
        when(authorizations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(access.isAuthorized(any(), eq(project), anyString())).thenReturn(true);
        LiveEvaluationAuthorizationService service = new LiveEvaluationAuthorizationService(
                preparations, authorizations, access, Clock.fixed(now, ZoneOffset.UTC));
        LiveEvaluationAuthorizationRequest request = new LiveEvaluationAuthorizationRequest(planId, digest);

        service.request(request, "same-key", new AuthenticatedPrincipal("requester-a", PrincipalKind.AI_AGENT, "test"));
        service.request(request, "same-key", new AuthenticatedPrincipal("requester-b", PrincipalKind.AI_AGENT, "test"));

        verify(authorizations).findForUpdate(any(), eq(planId), eq("requester-a"));
        verify(authorizations).findForUpdate(any(), eq(planId), eq("requester-b"));
        verify(authorizations, times(2)).save(any());
    }

    @Test
    void consumptionIsAtomicAndIdempotentForTheSameExecutionKey() {
        UUID planId = UUID.randomUUID();
        UUID authorizationId = UUID.randomUUID();
        String digest = "a".repeat(64);
        String executionKey = "execution-1";
        Instant now = Instant.parse("2026-11-23T05:00:00Z");
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        LiveEvaluationPreparation preparation = LiveEvaluationPreparation.builder()
                .id(planId).project(project).principalId("requester")
                .planDigest(digest).status(LiveEvaluationPreparationStatus.READY)
                .planSnapshot(Map.of()).createdAt(now).expiresAt(now.plusSeconds(3600)).build();
        LiveEvaluationAuthorization approved = LiveEvaluationAuthorization.builder()
                .id(authorizationId).preparation(preparation).requesterId("requester")
                .idempotencyKeyHash("b".repeat(64)).planDigest(digest)
                .status(LiveEvaluationAuthorizationStatus.APPROVED).artifactSnapshot(Map.of("issuedBy", "CORE"))
                .createdAt(now).approvedAt(now).expiresAt(now.plusSeconds(900)).build();
        LiveEvaluationAuthorization consumed = LiveEvaluationAuthorization.builder()
                .id(authorizationId).preparation(preparation).requesterId("requester")
                .idempotencyKeyHash("b".repeat(64)).planDigest(digest)
                .status(LiveEvaluationAuthorizationStatus.CONSUMED).artifactSnapshot(approved.getArtifactSnapshot())
                .createdAt(now).approvedAt(now).consumptionKeyHash(sha256(executionKey))
                .consumerId("runner").consumedAt(now).expiresAt(now.plusSeconds(900)).build();
        LiveEvaluationAuthorizationRepository authorizations = mock(LiveEvaluationAuthorizationRepository.class);
        when(authorizations.findForUpdate(authorizationId)).thenReturn(Optional.of(approved), Optional.of(consumed));
        when(authorizations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CoreProjectAuthorizationService access = mock(CoreProjectAuthorizationService.class);
        when(access.isAuthorized(any(), eq(project), eq("MCP_LIVE_EVALUATION_EXECUTE"))).thenReturn(true);
        LiveEvaluationAuthorizationService service = new LiveEvaluationAuthorizationService(
                mock(LiveEvaluationPreparationRepository.class), authorizations, access,
                Clock.fixed(now, ZoneOffset.UTC));
        AuthenticatedPrincipal runner = new AuthenticatedPrincipal("runner", PrincipalKind.AI_AGENT, "test");

        assertEquals(LiveEvaluationAuthorizationStatus.CONSUMED,
                service.consume(authorizationId, digest, executionKey, runner).status());
        assertEquals(LiveEvaluationAuthorizationStatus.CONSUMED,
                service.consume(authorizationId, digest, executionKey, runner).status());
        verify(authorizations, times(1)).save(any());
    }

    @Test
    void aConsumedAuthorizationRejectsADifferentExecutionKey() {
        UUID authorizationId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        LiveEvaluationPreparation preparation = LiveEvaluationPreparation.builder()
                .id(UUID.randomUUID()).project(project).build();
        LiveEvaluationAuthorization consumed = LiveEvaluationAuthorization.builder()
                .id(authorizationId).preparation(preparation).requesterId("requester")
                .planDigest("a".repeat(64)).status(LiveEvaluationAuthorizationStatus.CONSUMED)
                .consumptionKeyHash(sha256("execution-1")).expiresAt(Instant.parse("2026-11-23T06:00:00Z"))
                .build();
        LiveEvaluationAuthorizationRepository authorizations = mock(LiveEvaluationAuthorizationRepository.class);
        when(authorizations.findForUpdate(authorizationId)).thenReturn(Optional.of(consumed));
        CoreProjectAuthorizationService access = mock(CoreProjectAuthorizationService.class);
        when(access.isAuthorized(any(), eq(project), eq("MCP_LIVE_EVALUATION_EXECUTE"))).thenReturn(true);
        LiveEvaluationAuthorizationService service = new LiveEvaluationAuthorizationService(
                mock(LiveEvaluationPreparationRepository.class), authorizations, access,
                Clock.fixed(Instant.parse("2026-11-23T05:00:00Z"), ZoneOffset.UTC), "test-secret");

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.consume(authorizationId, "a".repeat(64), "execution-2",
                        new AuthenticatedPrincipal("runner", PrincipalKind.AI_AGENT, "test")));
    }

    @Test
    void anAgentPrincipalCanApproveDirectly() {
        UUID planId = UUID.randomUUID();
        UUID authorizationId = UUID.randomUUID();
        String digest = "a".repeat(64);
        Instant now = Instant.parse("2026-11-23T05:00:00Z");
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        LiveEvaluationPreparation preparation = LiveEvaluationPreparation.builder()
                .id(planId).project(project).principalId("requester").planDigest(digest)
                .status(LiveEvaluationPreparationStatus.READY).planSnapshot(Map.of())
                .createdAt(now).expiresAt(now.plusSeconds(3600)).build();
        LiveEvaluationAuthorization pending = LiveEvaluationAuthorization.builder()
                .id(authorizationId).preparation(preparation).requesterId("requester")
                .planDigest(digest).status(LiveEvaluationAuthorizationStatus.PENDING)
                .createdAt(now).expiresAt(now.plusSeconds(600)).build();
        LiveEvaluationAuthorizationRepository authorizations = mock(LiveEvaluationAuthorizationRepository.class);
        when(authorizations.findForUpdate(authorizationId)).thenReturn(Optional.of(pending));
        when(authorizations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CoreProjectAuthorizationService access = mock(CoreProjectAuthorizationService.class);
        when(access.isAuthorized(any(), eq(project), eq("MCP_LIVE_EVALUATION_AUTHORIZE"))).thenReturn(true);
        LiveEvaluationAuthorizationService service = new LiveEvaluationAuthorizationService(
                mock(LiveEvaluationPreparationRepository.class), authorizations, access,
                Clock.fixed(now, ZoneOffset.UTC));

        LiveEvaluationAuthorizationResponse result = service.approve(
                authorizationId, digest, new AuthenticatedPrincipal("approver-agent", PrincipalKind.AI_AGENT, "test"));

        assertEquals(LiveEvaluationAuthorizationStatus.APPROVED, result.status());
        assertEquals("approver-agent", result.artifact().get("approverId"));
    }

    @Test
    void selfApprovalIsRejected() {
        UUID planId = UUID.randomUUID();
        UUID authorizationId = UUID.randomUUID();
        String digest = "a".repeat(64);
        Instant now = Instant.parse("2026-11-23T05:00:00Z");
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        LiveEvaluationPreparation preparation = LiveEvaluationPreparation.builder()
                .id(planId).project(project).principalId("same-agent").planDigest(digest)
                .status(LiveEvaluationPreparationStatus.READY).planSnapshot(Map.of())
                .createdAt(now).expiresAt(now.plusSeconds(3600)).build();
        LiveEvaluationAuthorization pending = LiveEvaluationAuthorization.builder()
                .id(authorizationId).preparation(preparation).requesterId("same-agent")
                .planDigest(digest).status(LiveEvaluationAuthorizationStatus.PENDING)
                .createdAt(now).expiresAt(now.plusSeconds(600)).build();
        LiveEvaluationAuthorizationRepository authorizations = mock(LiveEvaluationAuthorizationRepository.class);
        when(authorizations.findForUpdate(authorizationId)).thenReturn(Optional.of(pending));
        CoreProjectAuthorizationService access = mock(CoreProjectAuthorizationService.class);
        when(access.isAuthorized(any(), eq(project), eq("MCP_LIVE_EVALUATION_AUTHORIZE"))).thenReturn(true);
        LiveEvaluationAuthorizationService service = new LiveEvaluationAuthorizationService(
                mock(LiveEvaluationPreparationRepository.class), authorizations, access,
                Clock.fixed(now, ZoneOffset.UTC));

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.approve(authorizationId, digest,
                        new AuthenticatedPrincipal("same-agent", PrincipalKind.AI_AGENT, "test")));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
