package com.hopeful117.devlogai.evaluation.liveauthorization;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationContract;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationContractRegistry;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparation;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparationRepository;
import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparationStatus;
import com.hopeful117.devlogai.project.entity.Project;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveEvaluationCampaignAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final String DIGEST = "a".repeat(64);
    private static final String CAMPAIGN_MANIFEST = "c".repeat(64);
    private final Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();

    private static LiveEvaluationContract campaign(Integer maxObservations, Duration maxWallClock) {
        Map<String, String> cases = new LinkedHashMap<>();
        for (int index = 1; index <= 8; index++) cases.put("Q" + index, String.valueOf(index).repeat(64));
        LiveEvaluationContract q1 = LiveEvaluationContractRegistry.approved().fallback();
        return new LiveEvaluationContract("devlog-ai-abc-campaign", q1.benchmarkId(), q1.benchmarkVersion(),
                "devlog-ai-abc-campaign", "1.0.0", CAMPAIGN_MANIFEST, q1.sourceRevision(), q1.provider(), q1.model(),
                q1.pricingVersion(), q1.pricingDigest(), q1.operationalLimits(), q1.runtimeIdentities(),
                q1.conditions(), cases, 3, true, maxObservations, maxWallClock);
    }

    private Map<String, Object> artifact(LiveEvaluationContract contract, String manifestDigest, String caseId) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("manifestDigest", manifestDigest);
        artifact.put("caseId", caseId);
        artifact.put("observationIds", contract.observationIds(caseId));
        return artifact;
    }

    private LiveEvaluationAuthorization authorization(LiveEvaluationAuthorizationStatus status, Map<String, Object> artifact) {
        LiveEvaluationPreparation preparation = LiveEvaluationPreparation.builder()
                .id(UUID.randomUUID()).project(project).planDigest(DIGEST)
                .status(LiveEvaluationPreparationStatus.READY).planSnapshot(artifact)
                .createdAt(NOW).expiresAt(NOW.plusSeconds(3600)).build();
        return LiveEvaluationAuthorization.builder()
                .id(UUID.randomUUID()).preparation(preparation).requesterId("requester").approverId("approver")
                .idempotencyKeyHash("c".repeat(64)).planDigest(DIGEST).status(status)
                .artifactSnapshot(artifact).createdAt(NOW).approvedAt(NOW).expiresAt(NOW.plusSeconds(600)).build();
    }

    private record Fixture(LiveEvaluationAuthorizationService service, LiveEvaluationAuthorizationRepository repository) { }

    private Fixture fixture(LiveEvaluationContractRegistry registry, LiveEvaluationAuthorization target,
                            List<LiveEvaluationAuthorization> consumed) {
        LiveEvaluationAuthorizationRepository repository = mock(LiveEvaluationAuthorizationRepository.class);
        when(repository.findForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(repository.findByStatusAndPreparation_Project_Id(LiveEvaluationAuthorizationStatus.CONSUMED, project.getId()))
                .thenReturn(consumed);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CoreProjectAuthorizationService access = mock(CoreProjectAuthorizationService.class);
        when(access.isAuthorized(any(), eq(project), any())).thenReturn(true);
        return new Fixture(new LiveEvaluationAuthorizationService(mock(LiveEvaluationPreparationRepository.class),
                repository, access, Clock.fixed(NOW, ZoneOffset.UTC), registry), repository);
    }

    private static AuthenticatedPrincipal runner() {
        return new AuthenticatedPrincipal("runner", PrincipalKind.AI_AGENT, "test");
    }

    @Test
    void firstConsumptionOfACaseBatchSucceeds() {
        LiveEvaluationContract contract = campaign(72, Duration.ofHours(3));
        LiveEvaluationAuthorization target = authorization(LiveEvaluationAuthorizationStatus.APPROVED,
                artifact(contract, CAMPAIGN_MANIFEST, "Q2"));
        Fixture fixture = fixture(new LiveEvaluationContractRegistry(List.of(contract)), target,
                List.of(authorization(LiveEvaluationAuthorizationStatus.CONSUMED, artifact(contract, CAMPAIGN_MANIFEST, "Q1"))));

        LiveEvaluationAuthorizationResponse result = fixture.service().consume(target.getId(), DIGEST, "exec-q2", runner());

        assertEquals(LiveEvaluationAuthorizationStatus.CONSUMED, result.status());
        verify(fixture.repository()).acquireAuthorizationLock("campaign:" + project.getId() + ":" + CAMPAIGN_MANIFEST);
    }

    @Test
    void aCaseBatchCannotBeConsumedTwiceInTheSameCampaign() {
        LiveEvaluationContract contract = campaign(72, Duration.ofHours(3));
        LiveEvaluationAuthorization target = authorization(LiveEvaluationAuthorizationStatus.APPROVED,
                artifact(contract, CAMPAIGN_MANIFEST, "Q2"));
        Fixture fixture = fixture(new LiveEvaluationContractRegistry(List.of(contract)), target,
                List.of(authorization(LiveEvaluationAuthorizationStatus.CONSUMED, artifact(contract, CAMPAIGN_MANIFEST, "Q2"))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service().consume(target.getId(), DIGEST, "exec-q2-again", runner()));

        assertTrue(error.getReason().contains("already been consumed"));
        verify(fixture.repository(), never()).save(any());
    }

    @Test
    void campaignObservationCeilingIsEnforced() {
        LiveEvaluationContract contract = campaign(18, null);
        LiveEvaluationAuthorization target = authorization(LiveEvaluationAuthorizationStatus.APPROVED,
                artifact(contract, CAMPAIGN_MANIFEST, "Q3"));
        Fixture fixture = fixture(new LiveEvaluationContractRegistry(List.of(contract)), target, List.of(
                authorization(LiveEvaluationAuthorizationStatus.CONSUMED, artifact(contract, CAMPAIGN_MANIFEST, "Q1")),
                authorization(LiveEvaluationAuthorizationStatus.CONSUMED, artifact(contract, CAMPAIGN_MANIFEST, "Q2"))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service().consume(target.getId(), DIGEST, "exec-q3", runner()));

        assertTrue(error.getReason().contains("observation ceiling"));
    }

    @Test
    void campaignWallClockCeilingIsEnforced() {
        LiveEvaluationContract contract = campaign(null, Duration.ofMinutes(30));
        LiveEvaluationAuthorization target = authorization(LiveEvaluationAuthorizationStatus.APPROVED,
                artifact(contract, CAMPAIGN_MANIFEST, "Q2"));
        Fixture fixture = fixture(new LiveEvaluationContractRegistry(List.of(contract)), target,
                List.of(authorization(LiveEvaluationAuthorizationStatus.CONSUMED, artifact(contract, CAMPAIGN_MANIFEST, "Q1"))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.service().consume(target.getId(), DIGEST, "exec-q2", runner()));

        assertTrue(error.getReason().contains("wall-clock ceiling"));
    }

    @Test
    void fullCampaignOfEightBatchesFitsTheApprovedCeilings() {
        LiveEvaluationContract contract = campaign(LiveEvaluationContractRegistry.CAMPAIGN_MAX_OBSERVATIONS,
                LiveEvaluationContractRegistry.CAMPAIGN_MAX_WALL_CLOCK);
        List<LiveEvaluationAuthorization> consumed = new ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            consumed.add(authorization(LiveEvaluationAuthorizationStatus.CONSUMED,
                    artifact(contract, CAMPAIGN_MANIFEST, "Q" + index)));
        }
        LiveEvaluationAuthorization target = authorization(LiveEvaluationAuthorizationStatus.APPROVED,
                artifact(contract, CAMPAIGN_MANIFEST, "Q8"));
        Fixture fixture = fixture(new LiveEvaluationContractRegistry(List.of(contract)), target, consumed);

        assertEquals(LiveEvaluationAuthorizationStatus.CONSUMED,
                fixture.service().consume(target.getId(), DIGEST, "exec-q8", runner()).status());
    }

    @Test
    void q1ContractStaysRepeatableWithoutCampaignChecks() {
        LiveEvaluationContract q1 = LiveEvaluationContractRegistry.approved().fallback();
        LiveEvaluationAuthorization target = authorization(LiveEvaluationAuthorizationStatus.APPROVED,
                artifact(q1, q1.manifestDigest(), "Q1"));
        Fixture fixture = fixture(LiveEvaluationContractRegistry.approved(), target,
                List.of(authorization(LiveEvaluationAuthorizationStatus.CONSUMED, artifact(q1, q1.manifestDigest(), "Q1"))));

        assertEquals(LiveEvaluationAuthorizationStatus.CONSUMED,
                fixture.service().consume(target.getId(), DIGEST, "exec-q1-rerun", runner()).status());
        verify(fixture.repository(), never()).findByStatusAndPreparation_Project_Id(any(), any());
    }

    @Test
    void approvalRecordsTheAcceptedOracleOfTheCase() {
        LiveEvaluationContract contract = campaign(72, Duration.ofHours(3));
        Map<String, Object> plan = artifact(contract, CAMPAIGN_MANIFEST, "Q5");
        LiveEvaluationAuthorization pending = authorization(LiveEvaluationAuthorizationStatus.PENDING, plan);
        Fixture fixture = fixture(new LiveEvaluationContractRegistry(List.of(contract)), pending, List.of());

        LiveEvaluationAuthorizationResponse result = fixture.service().approve(pending.getId(), DIGEST, runner());

        assertEquals("Q5_ORACLE_ACCEPTED", result.artifact().get("oracleStatus"));
        assertEquals(Map.of("Q5", "ACCEPTED"), result.artifact().get("caseOracleStatus"));
    }
}
