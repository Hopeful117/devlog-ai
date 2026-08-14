package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.request.MaintenanceFindingActionRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceFindingMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceFindingServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock MaintenanceFindingRepository repository;
    @Mock MaintenanceFindingMapper mapper;

    @InjectMocks MaintenanceFindingServiceImpl service;

    @Test
    void shouldCreateOpenFindingForProject() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        CreateMaintenanceFindingRequest request = new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                MaintenanceFindingSeverity.HIGH,
                MaintenanceSuggestedActionCategory.REFRESH,
                true,
                "  Project understanding is stale  ",
                "  Latest comparable analysis predates current repository evidence.  "
        );
        MaintenanceFinding saved = MaintenanceFinding.builder()
                .id(UUID.randomUUID())
                .project(project)
                .contextSurface(request.contextSurface())
                .issueType(request.issueType())
                .severity(request.severity())
                .status(MaintenanceFindingStatus.OPEN)
                .suggestedAction(request.suggestedAction())
                .humanReviewRequired(true)
                .summary("Project understanding is stale")
                .details("Latest comparable analysis predates current repository evidence.")
                .build();
        MaintenanceFindingResponse response = new MaintenanceFindingResponse(
                saved.getId(), projectId, saved.getContextSurface(), saved.getIssueType(),
                saved.getSeverity(), saved.getStatus(), saved.getSuggestedAction(),
                saved.isHumanReviewRequired(), saved.getSummary(), saved.getDetails(), List.of(), null, null
        );

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(repository.save(any(MaintenanceFinding.class))).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        MaintenanceFindingResponse result = service.create(projectId, request);

        assertEquals(response, result);
        verify(repository).save(argThat(finding ->
                finding.getProject().equals(project)
                        && finding.getStatus() == MaintenanceFindingStatus.OPEN
                        && finding.getContextSurface() == MaintenanceContextSurface.PROJECT_UNDERSTANDING
                        && finding.getIssueType() == MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING
                        && finding.getSeverity() == MaintenanceFindingSeverity.HIGH
                        && finding.getSuggestedAction() == MaintenanceSuggestedActionCategory.REFRESH
                        && finding.isHumanReviewRequired()
                        && "Project understanding is stale".equals(finding.getSummary())
                        && "Latest comparable analysis predates current repository evidence."
                        .equals(finding.getDetails())
        ));
    }

    @Test
    void shouldNormalizeBlankDetailsToNull() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        CreateMaintenanceFindingRequest request = new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.PROJECT_PROJECTION,
                MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH,
                MaintenanceFindingSeverity.MEDIUM,
                MaintenanceSuggestedActionCategory.INVESTIGATE,
                false,
                "Projection refresh missing",
                "   "
        );
        MaintenanceFinding saved = MaintenanceFinding.builder()
                .id(UUID.randomUUID())
                .project(project)
                .contextSurface(request.contextSurface())
                .issueType(request.issueType())
                .severity(request.severity())
                .status(MaintenanceFindingStatus.OPEN)
                .suggestedAction(request.suggestedAction())
                .humanReviewRequired(false)
                .summary(request.summary())
                .details(null)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(repository.save(any(MaintenanceFinding.class))).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(new MaintenanceFindingResponse(
                saved.getId(), projectId, saved.getContextSurface(), saved.getIssueType(),
                saved.getSeverity(), saved.getStatus(), saved.getSuggestedAction(),
                saved.isHumanReviewRequired(), saved.getSummary(), saved.getDetails(), List.of(), null, null
        ));

        service.create(projectId, request);

        verify(repository).save(argThat(finding -> finding.getDetails() == null));
    }

    @Test
    void shouldUpdateFindingStatusWithinProjectScope() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        MaintenanceFinding finding = MaintenanceFinding.builder()
                .id(findingId)
                .status(MaintenanceFindingStatus.OPEN)
                .build();
        MaintenanceFindingResponse response = new MaintenanceFindingResponse(
                findingId, projectId, MaintenanceContextSurface.PROJECT_PROJECTION,
                MaintenanceFindingIssueType.PROJECTION_REFRESH_GAP,
                MaintenanceFindingSeverity.MEDIUM, MaintenanceFindingStatus.RESOLVED,
                MaintenanceSuggestedActionCategory.REFRESH, false, "Gap", "Body", List.of(), null, null
        );

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(repository.findByIdAndProject_Id(findingId, projectId)).thenReturn(Optional.of(finding));
        when(repository.save(finding)).thenReturn(finding);
        when(mapper.toResponse(finding)).thenReturn(response);

        MaintenanceFindingResponse result = service.updateStatus(
                projectId, findingId, MaintenanceFindingStatus.RESOLVED
        );

        assertEquals(MaintenanceFindingStatus.RESOLVED, finding.getStatus());
        assertEquals(response, result);
    }

    @Test
    void shouldReturnProjectFindingsInRepositoryOrder() {
        UUID projectId = UUID.randomUUID();
        MaintenanceFinding finding = MaintenanceFinding.builder()
                .id(UUID.randomUUID())
                .summary("Gap")
                .build();
        MaintenanceFindingResponse response = new MaintenanceFindingResponse(
                finding.getId(), projectId, MaintenanceContextSurface.PROJECT_PROJECTION,
                MaintenanceFindingIssueType.PROJECTION_REFRESH_GAP,
                MaintenanceFindingSeverity.LOW, MaintenanceFindingStatus.OPEN,
                MaintenanceSuggestedActionCategory.MONITOR, false, "Gap", null, List.of(), null, null
        );

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId))
                .thenReturn(List.of(finding));
        when(mapper.toResponse(List.of(finding))).thenReturn(List.of(response));

        List<MaintenanceFindingResponse> results = service.getByProject(projectId);

        assertEquals(List.of(response), results);
    }

    @Test
    void shouldFailWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.getByProject(projectId));
    }

    @Test
    void shouldAcknowledgeSupportedDuplicateFindingWithAuditTrail() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        MaintenanceFinding finding = MaintenanceFinding.builder()
                .id(findingId)
                .project(project)
                .issueType(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE)
                .contextSurface(MaintenanceContextSurface.PROJECT_UNDERSTANDING)
                .severity(MaintenanceFindingSeverity.HIGH)
                .status(MaintenanceFindingStatus.OPEN)
                .suggestedAction(MaintenanceSuggestedActionCategory.REVIEW)
                .summary("Duplicate debt")
                .actions(new java.util.ArrayList<>())
                .build();
        MaintenanceFindingActionRequest request = new MaintenanceFindingActionRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000123"),
                "Reviewed and acknowledged"
        );
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(repository.findByIdAndProject_Id(findingId, projectId)).thenReturn(Optional.of(finding));
        when(repository.save(finding)).thenReturn(finding);
        when(mapper.toResponse(finding)).thenAnswer(invocation -> {
            MaintenanceFinding saved = invocation.getArgument(0);
            return new MaintenanceFindingResponse(
                    saved.getId(), projectId, saved.getContextSurface(), saved.getIssueType(),
                    saved.getSeverity(), saved.getStatus(), saved.getSuggestedAction(), true,
                    saved.getSummary(), saved.getDetails(), List.of(), null, null
            );
        });

        MaintenanceFindingResponse result = service.acknowledge(projectId, findingId, request);

        assertEquals(MaintenanceFindingStatus.ACKNOWLEDGED, finding.getStatus());
        assertEquals(MaintenanceFindingStatus.ACKNOWLEDGED, result.status());
        assertEquals(1, finding.getActions().size());
        assertEquals(MaintenanceFindingActionType.ACKNOWLEDGE, finding.getActions().getFirst().getActionType());
    }

    @Test
    void shouldRequireCommentToDismissFinding() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        MaintenanceFinding finding = MaintenanceFinding.builder()
                .id(findingId)
                .issueType(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE)
                .status(MaintenanceFindingStatus.OPEN)
                .actions(new java.util.ArrayList<>())
                .build();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(repository.findByIdAndProject_Id(findingId, projectId)).thenReturn(Optional.of(finding));

        var error = assertThrows(
                com.hopeful117.devlogai.shared.exception.ConflictException.class,
                () -> service.dismiss(projectId, findingId,
                        new MaintenanceFindingActionRequest(UUID.randomUUID(), "   "))
        );

        assertEquals("A rationale comment is required for this maintenance action.", error.getMessage());
    }

    @Test
    void shouldRejectRemediationForUnsupportedFindingFamily() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        MaintenanceFinding finding = MaintenanceFinding.builder()
                .id(findingId)
                .issueType(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING)
                .status(MaintenanceFindingStatus.OPEN)
                .actions(new java.util.ArrayList<>())
                .build();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(repository.findByIdAndProject_Id(findingId, projectId)).thenReturn(Optional.of(finding));

        var error = assertThrows(
                com.hopeful117.devlogai.shared.exception.ConflictException.class,
                () -> service.resolve(projectId, findingId,
                        new MaintenanceFindingActionRequest(UUID.randomUUID(), "Handled elsewhere"))
        );

        assertEquals("This maintenance finding family does not yet support remediation actions.",
                error.getMessage());
    }

    @Test
    void shouldResolveHumanContextFindingWithAuditTrail() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        MaintenanceFinding finding = MaintenanceFinding.builder()
                .id(findingId)
                .issueType(MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT)
                .contextSurface(MaintenanceContextSurface.INTERNAL_HUMAN_CONTEXT)
                .status(MaintenanceFindingStatus.OPEN)
                .actions(new java.util.ArrayList<>())
                .build();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(repository.findByIdAndProject_Id(findingId, projectId)).thenReturn(Optional.of(finding));
        when(repository.save(finding)).thenReturn(finding);
        when(mapper.toResponse(finding)).thenAnswer(invocation -> {
            MaintenanceFinding saved = invocation.getArgument(0);
            return new MaintenanceFindingResponse(
                    findingId, projectId, saved.getContextSurface(), saved.getIssueType(),
                    MaintenanceFindingSeverity.MEDIUM, saved.getStatus(),
                    MaintenanceSuggestedActionCategory.REVIEW, true,
                    "Stale human input", "details", List.of(), null, null
            );
        });

        MaintenanceFindingResponse result = service.resolve(projectId, findingId,
                new MaintenanceFindingActionRequest(UUID.randomUUID(), "Archived after review"));

        assertEquals(MaintenanceFindingStatus.RESOLVED, finding.getStatus());
        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        assertEquals(1, finding.getActions().size());
        assertEquals(MaintenanceFindingActionType.RESOLVE, finding.getActions().getFirst().getActionType());
    }

    @Test
    void shouldAutoResolveEligibleFindingWithSystemAuditTrail() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        UUID systemActorId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        MaintenanceFinding finding = MaintenanceFinding.builder()
                .id(findingId)
                .issueType(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING)
                .contextSurface(MaintenanceContextSurface.PROJECT_UNDERSTANDING)
                .status(MaintenanceFindingStatus.ACKNOWLEDGED)
                .actions(new java.util.ArrayList<>())
                .build();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(repository.findByIdAndProject_Id(findingId, projectId)).thenReturn(Optional.of(finding));
        when(repository.save(finding)).thenReturn(finding);
        when(mapper.toResponse(finding)).thenAnswer(invocation -> {
            MaintenanceFinding saved = invocation.getArgument(0);
            return new MaintenanceFindingResponse(
                    findingId, projectId, saved.getContextSurface(), saved.getIssueType(),
                    MaintenanceFindingSeverity.MEDIUM, saved.getStatus(),
                    MaintenanceSuggestedActionCategory.REFRESH, false,
                    "Understanding stale", "details", List.of(), null, null
            );
        });

        MaintenanceFindingResponse result = service.autoResolve(projectId, findingId, systemActorId,
                "Automatically resolved because the deterministic maintenance condition no longer applies.");

        assertEquals(MaintenanceFindingStatus.RESOLVED, finding.getStatus());
        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        assertEquals(1, finding.getActions().size());
        assertEquals(MaintenanceFindingActionType.AUTO_RESOLVE, finding.getActions().getFirst().getActionType());
        assertEquals(systemActorId, finding.getActions().getFirst().getActedBy());
    }
}
