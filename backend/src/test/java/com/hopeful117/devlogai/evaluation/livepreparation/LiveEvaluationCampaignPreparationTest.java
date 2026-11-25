package com.hopeful117.devlogai.evaluation.livepreparation;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveEvaluationCampaignPreparationTest {
    private static final String RUNTIME = "1".repeat(40);

    private LiveEvaluationPreparationResponse prepare(LiveEvaluationContractRegistry registry, String caseId,
                                                      String oracleDigest, List<String> observations) {
        return prepare(registry, LiveEvaluationContractTest.campaign(), caseId, oracleDigest, observations);
    }

    private LiveEvaluationPreparationResponse prepare(LiveEvaluationContractRegistry registry,
                                                      LiveEvaluationContract contract, String caseId,
                                                      String oracleDigest, List<String> observations) {
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        ProjectRepository projects = mock(ProjectRepository.class);
        CoreProjectAuthorizationService authorization = mock(CoreProjectAuthorizationService.class);
        LiveEvaluationPreparationRepository preparations = mock(LiveEvaluationPreparationRepository.class);
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        Source source = Source.builder().id(UUID.randomUUID()).project(project)
                .type(SourceType.GIT_REPOSITORY).active(true).build();
        when(projects.findBySlug("devlog-ai")).thenReturn(Optional.of(project));
        when(authorization.isAuthorized(any(), eq(project), anyString())).thenReturn(true);
        when(sources.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())).thenReturn(List.of(source));
        when(workspaces.synchronize(source, RUNTIME))
                .thenReturn(new SynchronizedWorkspace(source.getId(), Path.of("/tmp/source"), RUNTIME));
        when(preparations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new LiveEvaluationPreparationService(projects, authorization, preparations, sources, workspaces,
                new ObjectMapper(), Clock.systemUTC(), registry).prepare(
                "devlog-ai", "key-" + caseId, new AuthenticatedPrincipal("principal", PrincipalKind.HUMAN, "test"),
                new LiveEvaluationPreparationRequest(
                        contract.benchmarkId(), contract.benchmarkVersion(), caseId,
                        contract.manifestId(), contract.manifestVersion(), contract.manifestDigest(),
                        "ACCEPTED", oracleDigest, contract.sourceRevision(), RUNTIME,
                        Map.of("runtimeRevision", RUNTIME, "coreVerified", false),
                        contract.runtimeIdentities(), contract.operationalLimits(),
                        contract.provider(), contract.model(),
                        Map.of("status", "APPROVED", "version", contract.pricingVersion(),
                                "provider", contract.provider(), "authentication", "CHATGPT_OAUTH",
                                "currency", "NOT_APPLICABLE", "billingModel", "CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED",
                                "costCeilingPolicy", "OPERATIONAL_LIMITS_ONLY", "digest", contract.pricingDigest()),
                        "campaign/" + caseId, observations));
    }

    private static LiveEvaluationContractRegistry registry() {
        return new LiveEvaluationContractRegistry(
                List.of(LiveEvaluationContractRegistry.Q1_FIRST_TEST, LiveEvaluationContractTest.campaign()));
    }

    @Test
    void registeredCampaignCaseBatchIsReady() {
        LiveEvaluationContract contract = LiveEvaluationContractTest.campaign();

        LiveEvaluationPreparationResponse response = prepare(
                registry(), "Q2", "2".repeat(64), contract.observationIds("Q2"));

        assertEquals(LiveEvaluationPreparationStatus.READY, response.status(), String.valueOf(response.plan().get("blockers")));
        assertEquals("Q2", response.plan().get("caseId"));
        assertEquals("2".repeat(64), response.plan().get("oracleDigest"));
        assertEquals(contract.observationIds("Q2"), response.plan().get("observationIds"));
        assertEquals("devlog-ai-abc-campaign", response.plan().get("contractId"));
        Map<?, ?> ceiling = (Map<?, ?>) response.plan().get("campaignCeiling");
        assertEquals(true, ceiling.get("oneConsumptionPerCase"));
        assertEquals(72, ceiling.get("maxObservations"));
        assertEquals(10800L, ceiling.get("maxWallClockSeconds"));
    }

    @Test
    void anotherCaseOracleDigestIsBlocked() {
        LiveEvaluationPreparationResponse response = prepare(
                registry(), "Q2", "3".repeat(64), LiveEvaluationContractTest.campaign().observationIds("Q2"));

        assertEquals(LiveEvaluationPreparationStatus.BLOCKED, response.status());
        assertTrue(((List<?>) response.plan().get("blockers")).contains("oracle is not the approved oracle for the case"));
    }

    @Test
    void observationSetMustBeTheDerivedCaseBatch() {
        List<String> q1Ids = LiveEvaluationContractTest.campaign().observationIds("Q1");

        LiveEvaluationPreparationResponse response = prepare(registry(), "Q2", "2".repeat(64), q1Ids);

        assertEquals(LiveEvaluationPreparationStatus.BLOCKED, response.status());
        assertTrue(((List<?>) response.plan().get("blockers")).contains("observation set is not the contract set for the case"));
    }

    @Test
    void caseOutsideTheContractIsBlocked() {
        LiveEvaluationPreparationResponse response = prepare(registry(), "Q9", "2".repeat(64), List.of("Q9-A-DEVLOG-CURRENT-r1"));

        assertEquals(LiveEvaluationPreparationStatus.BLOCKED, response.status());
        assertTrue(((List<?>) response.plan().get("blockers")).contains("benchmark or case identity is not in the approved contract"));
    }

    @Test
    void unregisteredCampaignIsBlockedByTheDefaultRegistry() {
        LiveEvaluationPreparationResponse response = prepare(
                new LiveEvaluationContractRegistry(List.of(LiveEvaluationContractRegistry.Q1_FIRST_TEST)), "Q2",
                "2".repeat(64), LiveEvaluationContractTest.campaign().observationIds("Q2"));

        assertEquals(LiveEvaluationPreparationStatus.BLOCKED, response.status());
        assertTrue(((List<?>) response.plan().get("blockers"))
                .contains("manifest identity is not an approved live evaluation contract"));
        assertEquals("devlog-ai-abc-first-test", response.plan().get("contractId"));
    }

    @Test
    void batchLargerThanTheCampaignCeilingIsBlocked() {
        LiveEvaluationContract base = LiveEvaluationContractTest.campaign();
        LiveEvaluationContract tight = new LiveEvaluationContract(base.contractId(), base.benchmarkId(),
                base.benchmarkVersion(), base.manifestId(), base.manifestVersion(), base.manifestDigest(),
                base.sourceRevision(), base.provider(), base.model(), base.pricingVersion(), base.pricingDigest(),
                base.operationalLimits(), base.runtimeIdentities(), base.conditions(), base.caseOracleDigests(),
                base.repetitions(), true, 8, Duration.ofMinutes(10));
        LiveEvaluationContractRegistry registry = new LiveEvaluationContractRegistry(List.of(tight));

        LiveEvaluationPreparationResponse response = prepare(registry, tight, "Q2", "2".repeat(64), tight.observationIds("Q2"));

        List<?> blockers = (List<?>) response.plan().get("blockers");
        assertEquals(LiveEvaluationPreparationStatus.BLOCKED, response.status());
        assertTrue(blockers.contains("case batch exceeds the campaign observation ceiling"));
        assertTrue(blockers.contains("case batch exceeds the campaign wall-clock ceiling"));
    }

    @Test
    void approvedRegistryAcceptsTheFrozenCampaignBatch() {
        LiveEvaluationContract campaign = LiveEvaluationContractRegistry.ABC_CAMPAIGN;

        LiveEvaluationPreparationResponse response = prepare(LiveEvaluationContractRegistry.approved(), campaign,
                "Q7", campaign.caseOracleDigests().get("Q7"), campaign.observationIds("Q7"));

        assertEquals(LiveEvaluationPreparationStatus.READY, response.status(), String.valueOf(response.plan().get("blockers")));
        assertEquals("devlog-ai-abc-campaign", response.plan().get("contractId"));
        assertEquals(9, ((List<?>) response.plan().get("observationIds")).size());
    }
}
