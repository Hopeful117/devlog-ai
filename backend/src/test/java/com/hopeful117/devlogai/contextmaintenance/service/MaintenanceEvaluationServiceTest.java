package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.agent.CrossSurfacePatternDetectionAgent;
import com.hopeful117.devlogai.contextmaintenance.agent.DuplicateAmbiguityResolutionAgent;
import com.hopeful117.devlogai.contextmaintenance.agent.DuplicateAmbiguityResolutionAgent.AgentAssessmentResult;
import com.hopeful117.devlogai.contextmaintenance.config.MaintenanceAgentProperties;
import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFinding;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingAction;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingActionType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateAuditResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterCategory;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateMemberResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateRecommendation;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.service.TrustedKnowledgeDuplicateAuditService;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInput;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import com.hopeful117.devlogai.projectcontextinput.repository.ProjectHumanContextInputRepository;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.projectfreshness.ProjectRefreshGuidance;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MaintenanceEvaluationServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectFreshnessService freshnessService = mock(ProjectFreshnessService.class);
    private final TrustedKnowledgeDuplicateAuditService duplicateAuditService =
            mock(TrustedKnowledgeDuplicateAuditService.class);
    private final ProjectHumanContextInputRepository humanContextInputRepository =
            mock(ProjectHumanContextInputRepository.class);
    private final MaintenanceFindingRepository repository = mock(MaintenanceFindingRepository.class);
    private final MaintenanceFindingService findingService = mock(MaintenanceFindingService.class);
    private final MaintenanceAssessmentService assessmentService = mock(MaintenanceAssessmentService.class);
    private final DuplicateAmbiguityResolutionAgent duplicateAgent = mock(DuplicateAmbiguityResolutionAgent.class);
    private final CrossSurfacePatternDetectionAgent crossSurfaceAgent = mock(CrossSurfacePatternDetectionAgent.class);
    private final MaintenanceAgentProperties agentProperties = mock(MaintenanceAgentProperties.class);
    private final MaintenanceEvaluationService service = new MaintenanceEvaluationServiceImpl(
            projectRepository, freshnessService, duplicateAuditService, humanContextInputRepository,
            repository, findingService, assessmentService, duplicateAgent, crossSurfaceAgent, agentProperties
    );

    @Test
    void shouldCreateFreshnessAndDuplicateDebtFindings() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(staleFreshness(sourceId)),
                1,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId,
                2,
                2,
                List.of(exactDuplicateCluster(), semanticDuplicateCluster())
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());
        when(findingService.create(eq(projectId), any(CreateMaintenanceFindingRequest.class)))
                .thenAnswer(invocation -> toResponse(projectId, invocation.getArgument(1)));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals("maintenance-evaluation-v1", result.version());
        assertEquals(4, result.createdCount());
        assertEquals(0, result.skippedCount());
        assertEquals(4, result.createdFindings().size());

        ArgumentCaptor<CreateMaintenanceFindingRequest> captor =
                ArgumentCaptor.forClass(CreateMaintenanceFindingRequest.class);
        verify(findingService, times(4)).create(eq(projectId), captor.capture());
        List<CreateMaintenanceFindingRequest> requests = captor.getAllValues();
        assertEquals(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING, requests.get(0).issueType());
        assertEquals(MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH, requests.get(1).issueType());
        assertEquals(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE, requests.get(2).issueType());
        assertEquals(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE, requests.get(3).issueType());
    }

    @Test
    void shouldCreateReviewFindingForRicherSuccessorCluster() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, projectId, List.of(), 0, false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 2, 1, List.of(richerSuccessorCluster())
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());
        when(findingService.create(eq(projectId), any(CreateMaintenanceFindingRequest.class)))
                .thenAnswer(invocation -> toResponse(projectId, invocation.getArgument(1)));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(1, result.createdCount());
        assertEquals(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_OVERLAP_REVIEW,
                result.createdFindings().getFirst().issueType());
        assertEquals(true, result.createdFindings().getFirst().humanReviewRequired());
    }

    @Test
    void shouldNotCreateDuplicateDebtFindingWhenAuditIsEmptyAndFreshnessCurrent() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(currentFreshness(UUID.randomUUID())),
                0,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        assertEquals(0, result.skippedCount());
        verifyNoInteractions(findingService);
    }

    @Test
    void shouldSkipEquivalentOpenDuplicateDebtFinding() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, projectId, List.of(), 0, false
        ));
        InsightDuplicateClusterResponse cluster = exactDuplicateCluster();
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 2, 1, List.of(cluster)
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId))
                .thenReturn(List.of(existingDuplicateFinding(projectId, cluster, MaintenanceFindingStatus.OPEN)));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        assertEquals(1, result.skippedCount());
        verifyNoInteractions(findingService);
    }

    @Test
    void shouldSkipDuplicateFindingWhenEquivalentResolvedFindingExists() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, projectId, List.of(), 0, false
        ));
        InsightDuplicateClusterResponse cluster = exactDuplicateCluster();
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 2, 1, List.of(cluster)
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId))
                .thenReturn(List.of(existingDuplicateFinding(projectId, cluster, MaintenanceFindingStatus.RESOLVED)));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        assertEquals(1, result.skippedCount());
        verifyNoInteractions(findingService);
    }

    @Test
    void shouldFailWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.evaluate(projectId));
    }

    @Test
    void shouldCreateHumanContextMaintenanceFindingForStaleActiveInput() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, projectId, List.of(), 0, false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of(
                humanContextInput("Fresh goal", ProjectHumanContextInputType.GOAL, 5),
                humanContextInput("Older goal", ProjectHumanContextInputType.GOAL, 60)
        ));
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());
        when(findingService.create(eq(projectId), any(CreateMaintenanceFindingRequest.class)))
                .thenAnswer(invocation -> toResponse(projectId, invocation.getArgument(1)));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(1, result.createdCount());
        assertEquals(MaintenanceContextSurface.INTERNAL_HUMAN_CONTEXT,
                result.createdFindings().getFirst().contextSurface());
        assertEquals(MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT,
                result.createdFindings().getFirst().issueType());
    }

    @Test
    void shouldNotCreateHumanContextFindingForSingleActiveInput() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, projectId, List.of(), 0, false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of(
                humanContextInput("Medium-term objective", ProjectHumanContextInputType.GOAL, 5)
        ));
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        verifyNoInteractions(findingService);
    }

    @Test
    void shouldNotCreateHumanContextFindingForArchivedInput() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, projectId, List.of(), 0, false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of(
                humanContextInput("Fresh goal", ProjectHumanContextInputType.GOAL, 5)
        ));
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        verifyNoInteractions(findingService);
    }

    @Test
    void shouldAutoResolveClearedStaleUnderstandingFinding() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(currentFreshness(UUID.randomUUID())),
                0,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                autoResolvableFinding(projectId, findingId, MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                        "Project understanding is stale for source 'repo'.",
                        "stale details",
                        MaintenanceFindingStatus.OPEN)
        ));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        verify(findingService).autoResolve(eq(projectId), eq(findingId),
                eq(MaintenanceEvaluationServiceImpl.SYSTEM_AUTOMATION_ACTOR_ID), anyString());
    }

    @Test
    void shouldNotAutoResolveDismissedFinding() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(currentFreshness(UUID.randomUUID())),
                0,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                autoResolvableFinding(projectId, findingId, MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                        "Project understanding is stale for source 'repo'.",
                        "stale details",
                        MaintenanceFindingStatus.DISMISSED)
        ));

        service.evaluate(projectId);

        verify(findingService, never()).autoResolve(any(), any(), any(), anyString());
    }

    @Test
    void shouldNotAutoResolveDuplicateDebtFinding() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(),
                0,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                autoResolvableFinding(projectId, findingId, MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE,
                        "Trusted knowledge exact duplicate debt detected for cluster 'adr'.",
                        "duplicate details",
                        MaintenanceFindingStatus.OPEN)
        ));

        service.evaluate(projectId);

        verify(findingService, never()).autoResolve(any(), any(), any(), anyString());
    }

    @Test
    void shouldSkipCreationWhenEquivalentAcknowledgedFindingExists() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        ProjectFreshnessResponse stale = staleFreshness(sourceId);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(stale),
                0,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                autoResolvableFinding(projectId, findingId, MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                        "Project understanding is stale for source 'repo'.",
                        """
                                Freshness check status is STALE with guidance REFRESH_RECOMMENDED.
                                Source requested revision: origin/main
                                Source current revision: %s
                                Baseline analyzed revision: %s
                                Baseline completed at: 2026-08-14T09:00:00Z
                                Checked at: 2026-08-14T10:00:00Z
                                """.formatted("a".repeat(40), "b".repeat(40)).trim(),
                        MaintenanceFindingStatus.ACKNOWLEDGED)
        ));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        assertEquals(1, result.skippedCount());
        verify(findingService, never()).create(any(), any());
    }

    @Test
    void shouldSkipOverlapReviewWhenEquivalentResolvedFindingExists() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, projectId, List.of(), 0, false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        UUID clusterId = UUID.randomUUID();
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                MaintenanceFinding.builder()
                        .contextSurface(MaintenanceContextSurface.PROJECT_UNDERSTANDING)
                        .issueType(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_OVERLAP_REVIEW)
                        .severity(MaintenanceFindingSeverity.MEDIUM)
                        .status(MaintenanceFindingStatus.RESOLVED)
                        .summary("Overlap review pending for cluster '%s'".formatted(clusterId))
                        .details("Cluster key: " + clusterId)
                        .project(com.hopeful117.devlogai.project.entity.Project.builder().id(projectId).build())
                        .build()
        ));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        assertEquals(0, result.skippedCount());
        verifyNoInteractions(findingService);
    }

    private ProjectFreshnessResponse staleFreshness(UUID sourceId) {
        return new ProjectFreshnessResponse(
                ProjectFreshnessResponse.PROJECTION_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ProjectFreshnessResponse.Source(
                        sourceId, "repo", "main", "origin/main", "a".repeat(40), null
                ),
                Instant.parse("2026-08-14T10:00:00Z"),
                ProjectFreshnessStatus.STALE,
                ProjectRefreshGuidance.REFRESH_RECOMMENDED,
                new ProjectFreshnessResponse.Baseline(
                        UUID.randomUUID(),
                        Instant.parse("2026-08-14T09:00:00Z"),
                        "b".repeat(40)
                ),
                new ProjectFreshnessResponse.ReviewCounts(1, 0, 1, 0)
        );
    }

    private ProjectFreshnessResponse currentFreshness(UUID sourceId) {
        return new ProjectFreshnessResponse(
                ProjectFreshnessResponse.PROJECTION_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ProjectFreshnessResponse.Source(
                        sourceId, "repo", "main", "origin/main", "a".repeat(40), null
                ),
                Instant.parse("2026-08-14T10:00:00Z"),
                ProjectFreshnessStatus.CURRENT,
                ProjectRefreshGuidance.REFRESH_NOT_NEEDED,
                new ProjectFreshnessResponse.Baseline(
                        UUID.randomUUID(),
                        Instant.parse("2026-08-14T10:00:00Z"),
                        "a".repeat(40)
                ),
                new ProjectFreshnessResponse.ReviewCounts(1, 0, 1, 0)
        );
    }

    private InsightDuplicateClusterResponse exactDuplicateCluster() {
        return new InsightDuplicateClusterResponse(
                "ARCHITECTURE_DESCRIPTION::adr",
                InsightDuplicateClusterCategory.EXACT_DUPLICATE,
                InsightDuplicateRecommendation.KEEP_NEWEST_AS_CANONICAL,
                "Members share the same normalized trusted fingerprint.",
                List.of(
                        member("Architecture Decision Records (ADR) Documentation",
                                Instant.parse("2026-08-14T10:00:00Z")),
                        member("Architecture Decision Records (ADR) Documentation",
                                Instant.parse("2026-08-14T09:00:00Z"))
                )
        );
    }

    private InsightDuplicateClusterResponse semanticDuplicateCluster() {
        return new InsightDuplicateClusterResponse(
                "ARCHITECTURE_DESCRIPTION::rest-spring",
                InsightDuplicateClusterCategory.LIKELY_SEMANTIC_DUPLICATE,
                InsightDuplicateRecommendation.REVIEW_MANUALLY,
                "Members appear semantically close but no single richer canonical record is confidently dominant.",
                List.of(
                        member("REST Spring Boot Application Architecture",
                                Instant.parse("2026-08-14T10:00:00Z")),
                        member("RESTful Spring Boot Application Architecture",
                                Instant.parse("2026-08-14T09:00:00Z"))
                )
        );
    }

    private InsightDuplicateClusterResponse richerSuccessorCluster() {
        return new InsightDuplicateClusterResponse(
                "TECHNOLOGY_DESCRIPTION::testing",
                InsightDuplicateClusterCategory.LIKELY_RICHER_SUCCESSOR,
                InsightDuplicateRecommendation.KEEP_RICHEST_AS_CANONICAL,
                "Members share the same topic family, and one record is materially richer in provenance or detail.",
                List.of(
                        member("Automated Testing Structure",
                                Instant.parse("2026-08-14T10:00:00Z")),
                        member("Automated and Integration Testing Infrastructure",
                                Instant.parse("2026-08-14T09:00:00Z"))
                )
        );
    }

    private InsightDuplicateMemberResponse member(String title, Instant createdAt) {
        return new InsightDuplicateMemberResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                InsightType.ARCHITECTURAL,
                InsightSeverity.INFO,
                "ARCHITECTURE_DESCRIPTION",
                title,
                "content",
                "rationale",
                BigDecimal.ONE,
                1,
                createdAt
        );
    }

    private MaintenanceFinding existingDuplicateFinding(UUID projectId, InsightDuplicateClusterResponse cluster, MaintenanceFindingStatus status) {
        return MaintenanceFinding.builder()
                .contextSurface(MaintenanceContextSurface.PROJECT_UNDERSTANDING)
                .issueType(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE)
                .status(status)
                .summary("Trusted knowledge exact duplicate debt detected for cluster '%s'."
                        .formatted(cluster.clusterKey()))
                .details("""
                        Duplicate cluster key: %s
                        Cluster category: %s
                        Recommendation: %s
                        Member count: %d
                        Detector rationale: %s
                        Members:
                        %s | %s
                        %s | %s
                        """.formatted(
                        cluster.clusterKey(),
                        cluster.category(),
                        cluster.recommendation(),
                        cluster.members().size(),
                        cluster.rationale(),
                        cluster.members().get(0).insightId(),
                        cluster.members().get(0).title(),
                        cluster.members().get(1).insightId(),
                        cluster.members().get(1).title()
                ))
                .project(com.hopeful117.devlogai.project.entity.Project.builder().id(projectId).build())
                .build();
    }

    private MaintenanceFindingResponse toResponse(UUID projectId, CreateMaintenanceFindingRequest request) {
        return new MaintenanceFindingResponse(
                UUID.randomUUID(),
                projectId,
                request.contextSurface(),
                request.issueType(),
                request.severity(),
                MaintenanceFindingStatus.OPEN,
                request.suggestedAction(),
                request.humanReviewRequired(),
                request.summary(),
                request.details(),
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private ProjectHumanContextInput humanContextInput(
            String title,
            ProjectHumanContextInputType type,
            long updatedDaysAgo
    ) {
        return ProjectHumanContextInput.builder()
                .id(UUID.randomUUID())
                .title(title)
                .contentMarkdown("body")
                .type(type)
                .status(ProjectHumanContextInputStatus.ACTIVE)
                .updatedAt(Instant.now().minusSeconds(updatedDaysAgo * 24 * 60 * 60))
                .build();
    }

    private MaintenanceFinding autoResolvableFinding(
            UUID projectId,
            UUID findingId,
            MaintenanceContextSurface surface,
            MaintenanceFindingIssueType issueType,
            String summary,
            String details,
            MaintenanceFindingStatus status
    ) {
        return MaintenanceFinding.builder()
                .id(findingId)
                .project(com.hopeful117.devlogai.project.entity.Project.builder().id(projectId).build())
                .contextSurface(surface)
                .issueType(issueType)
                .severity(com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity.MEDIUM)
                .status(status)
                .suggestedAction(com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory.REVIEW)
                .summary(summary)
                .details(details)
                .actions(new java.util.ArrayList<>(List.of(MaintenanceFindingAction.builder()
                        .actionType(MaintenanceFindingActionType.ACKNOWLEDGE)
                        .actedBy(UUID.randomUUID())
                        .build())))
                .build();
    }

    @Test
    void shouldSuppressLowConfidenceDuplicateAssessments() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(),
                0,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId,
                1,
                1,
                List.of(semanticDuplicateCluster())
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());

        MaintenanceFindingResponse createdFinding = toResponse(projectId,
                duplicateDebtRequest(semanticDuplicateCluster()));
        when(findingService.create(eq(projectId), any(CreateMaintenanceFindingRequest.class)))
                .thenReturn(createdFinding);

        when(agentProperties.isAboveThreshold(any())).thenReturn(false);

        service.evaluate(projectId);

        verify(assessmentService, never()).create(eq(projectId), any());
    }

    @Test
    void shouldPersistHighConfidenceDuplicateAssessments() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(),
                0,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId,
                1,
                1,
                List.of(semanticDuplicateCluster())
        ));
        when(humanContextInputRepository.findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(
                projectId, ProjectHumanContextInputStatus.ACTIVE)).thenReturn(List.of());
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());

        MaintenanceFindingResponse createdFinding = toResponse(projectId,
                duplicateDebtRequest(semanticDuplicateCluster()));
        when(findingService.create(eq(projectId), any(CreateMaintenanceFindingRequest.class)))
                .thenReturn(createdFinding);

        when(agentProperties.isAboveThreshold(any())).thenReturn(true);
        when(duplicateAgent.evaluate(any(), any())).thenReturn(Optional.of(
                new AgentAssessmentResult(
                        com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                        com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel.HIGH,
                        com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentRecommendedAction.RESOLVE,
                        "rationale",
                        "signal"
                )
        ));

        service.evaluate(projectId);

        verify(assessmentService, times(1)).create(eq(projectId), any());
    }

    private CreateMaintenanceFindingRequest duplicateDebtRequest(InsightDuplicateClusterResponse cluster) {
        return switch (cluster.category()) {
            case EXACT_DUPLICATE -> new CreateMaintenanceFindingRequest(
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE,
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity.HIGH,
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory.REVIEW,
                    true,
                    "Trusted knowledge exact duplicate debt detected for cluster '%s'."
                            .formatted(cluster.clusterKey()),
                    "details"
            );
            case LIKELY_SEMANTIC_DUPLICATE -> new CreateMaintenanceFindingRequest(
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE,
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity.MEDIUM,
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory.REVIEW,
                    true,
                    "Trusted knowledge semantic duplicate candidate detected for cluster '%s'."
                            .formatted(cluster.clusterKey()),
                    "details"
            );
            default -> new CreateMaintenanceFindingRequest(
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_OVERLAP_REVIEW,
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity.MEDIUM,
                    com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory.REVIEW,
                    true,
                    "Trusted knowledge overlap requires review for cluster '%s'."
                            .formatted(cluster.clusterKey()),
                    "details"
            );
        };
    }
}
