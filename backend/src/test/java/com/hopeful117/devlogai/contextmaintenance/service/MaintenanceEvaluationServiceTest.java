package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFinding;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.projectfreshness.ProjectRefreshGuidance;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MaintenanceEvaluationServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectFreshnessService freshnessService = mock(ProjectFreshnessService.class);
    private final MaintenanceFindingRepository repository = mock(MaintenanceFindingRepository.class);
    private final MaintenanceFindingService findingService = mock(MaintenanceFindingService.class);
    private final MaintenanceEvaluationService service = new MaintenanceEvaluationServiceImpl(
            projectRepository, freshnessService, repository, findingService
    );

    @Test
    void shouldCreateStaleUnderstandingAndMissingProjectionFindings() {
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
        when(repository.findByProject_IdAndStatusOrderByCreatedAtDescIdDesc(projectId, MaintenanceFindingStatus.OPEN))
                .thenReturn(List.of(), List.of());
        when(findingService.create(eq(projectId), any(CreateMaintenanceFindingRequest.class)))
                .thenAnswer(invocation -> toResponse(projectId, invocation.getArgument(1)));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals("maintenance-evaluation-v1", result.version());
        assertEquals(2, result.createdCount());
        assertEquals(0, result.skippedCount());
        assertEquals(2, result.createdFindings().size());

        ArgumentCaptor<CreateMaintenanceFindingRequest> captor =
                ArgumentCaptor.forClass(CreateMaintenanceFindingRequest.class);
        verify(findingService, times(2)).create(eq(projectId), captor.capture());
        List<CreateMaintenanceFindingRequest> requests = captor.getAllValues();
        assertEquals(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING, requests.get(0).issueType());
        assertEquals(MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH, requests.get(1).issueType());
    }

    @Test
    void shouldNotCreateFindingForCurrentFreshnessWithoutUncheckedSources() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(currentFreshness(UUID.randomUUID())),
                0,
                false
        ));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        assertEquals(0, result.skippedCount());
        verifyNoInteractions(findingService);
    }

    @Test
    void shouldSkipDuplicateOpenFindings() {
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
        when(repository.findByProject_IdAndStatusOrderByCreatedAtDescIdDesc(projectId, MaintenanceFindingStatus.OPEN))
                .thenReturn(List.of(existingStaleFinding(projectId)), List.of(existingMissingProjectionFinding(projectId)));

        MaintenanceEvaluationResponse result = service.evaluate(projectId);

        assertEquals(0, result.createdCount());
        assertEquals(2, result.skippedCount());
        verifyNoInteractions(findingService);
    }

    @Test
    void shouldFailWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.evaluate(projectId));
    }

    private ProjectFreshnessResponse staleFreshness(UUID sourceId) {
        return new ProjectFreshnessResponse(
                ProjectFreshnessResponse.PROJECTION_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ProjectFreshnessResponse.Source(
                        sourceId, "repo", "main", "origin/main", "a".repeat(40)
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
                        sourceId, "repo", "main", "origin/main", "a".repeat(40)
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

    private MaintenanceFinding existingStaleFinding(UUID projectId) {
        return MaintenanceFinding.builder()
                .contextSurface(MaintenanceContextSurface.PROJECT_UNDERSTANDING)
                .issueType(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING)
                .status(MaintenanceFindingStatus.OPEN)
                .summary("Project understanding is stale for source 'repo'.")
                .details("""
                        Freshness check status is STALE with guidance REFRESH_RECOMMENDED.
                        Source requested revision: origin/main
                        Source current revision: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                        Baseline analyzed revision: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
                        Baseline completed at: 2026-08-14T09:00:00Z
                        Checked at: 2026-08-14T10:00:00Z
                        """)
                .project(com.hopeful117.devlogai.project.entity.Project.builder().id(projectId).build())
                .build();
    }

    private MaintenanceFinding existingMissingProjectionFinding(UUID projectId) {
        return MaintenanceFinding.builder()
                .contextSurface(MaintenanceContextSurface.PROJECT_PROJECTION)
                .issueType(MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH)
                .status(MaintenanceFindingStatus.OPEN)
                .summary("Project freshness projection is missing for active sources.")
                .details("""
                        Active sources without any persisted freshness check: 1
                        Checked sources included in summary: 1
                        Summary truncated: false
                        Run freshness checks before relying on freshness-based maintenance signals.
                        """)
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
                Instant.now(),
                Instant.now()
        );
    }
}
