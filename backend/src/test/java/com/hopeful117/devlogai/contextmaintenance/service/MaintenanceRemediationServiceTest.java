package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceFindingMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectunderstanding.ProjectUnderstandingService;
import com.hopeful117.devlogai.projectcontextinput.service.ProjectHumanContextInputService;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceRemediationServiceTest {

    @Mock MaintenanceFindingRepository findingRepository;
    @Mock MaintenanceFindingMapper findingMapper;
    @Mock ProjectFreshnessService freshnessService;
    @Mock SourceRepository sourceRepository;
    @Mock ProjectHumanContextInputService humanContextInputService;
    @Mock ProjectUnderstandingService understandingService;

    @InjectMocks MaintenanceRemediationServiceImpl service;

    private Project project = Project.builder().id(UUID.randomUUID()).build();

    private MaintenanceFinding buildFinding(MaintenanceFindingIssueType issueType, String details) {
        return MaintenanceFinding.builder()
                .id(UUID.randomUUID())
                .project(project)
                .contextSurface(MaintenanceContextSurface.PROJECT_PROJECTION)
                .issueType(issueType)
                .severity(MaintenanceFindingSeverity.HIGH)
                .status(MaintenanceFindingStatus.OPEN)
                .suggestedAction(MaintenanceSuggestedActionCategory.REFRESH)
                .humanReviewRequired(true)
                .summary("Test finding")
                .details(details)
                .actions(new java.util.ArrayList<>())
                .build();
    }

    private MaintenanceFindingResponse responseFor(MaintenanceFinding f) {
        return new MaintenanceFindingResponse(
                f.getId(), project.getId(), f.getContextSurface(), f.getIssueType(),
                f.getSeverity(), MaintenanceFindingStatus.RESOLVED, f.getSuggestedAction(),
                f.isHumanReviewRequired(), f.getSummary(), f.getDetails(),
                List.of(), List.of(), f.getCreatedAt(), f.getUpdatedAt()
        );
    }

    // =================== refreshProjection ===================

    @Test
    void refreshProjection_shouldResolveFindingAndCheckFreshness() {
        UUID findingId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.PROJECTION_REFRESH_GAP, null);
        Source source = Source.builder().id(sourceId).build();

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())).thenReturn(List.of(source));
        when(freshnessService.check(project.getId(), sourceId)).thenReturn(null);
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.refreshProjection(project.getId(), findingId, UUID.randomUUID(), "Refreshed");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(freshnessService).check(project.getId(), sourceId);
    }

    @Test
    void refreshProjection_shouldThrowWhenFindingNotFound() {
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                service.refreshProjection(project.getId(), UUID.randomUUID(), UUID.randomUUID(), "comment"));
    }

    @Test
    void refreshProjection_shouldThrowForWrongIssueType() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING, null);
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.refreshProjection(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("PROJECTION_REFRESH_GAP"));
    }

    @Test
    void refreshProjection_shouldContinueEvenIfFreshnessCheckFails() {
        UUID findingId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.PROJECTION_REFRESH_GAP, null);
        Source source = Source.builder().id(sourceId).build();

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())).thenReturn(List.of(source));
        when(freshnessService.check(project.getId(), sourceId)).thenThrow(new RuntimeException("Git unavailable"));
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.refreshProjection(project.getId(), findingId, UUID.randomUUID(), "comment");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
    }

    // =================== archiveStaleHumanContext ===================

    @Test
    void archiveStaleHumanContext_shouldArchiveInputAndResolveFinding() {
        UUID findingId = UUID.randomUUID();
        UUID inputId = UUID.randomUUID();
        String details = "Active human context input '" + inputId + "' may be stale.";
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT, details);

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.archiveStaleHumanContext(project.getId(), findingId, UUID.randomUUID(), "Archived");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(humanContextInputService).archive(project.getId(), inputId);
    }

    @Test
    void archiveStaleHumanContext_shouldThrowWhenInputIdCannotBeExtracted() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT, "No UUID here");
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.archiveStaleHumanContext(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("Could not extract"));
    }

    @Test
    void archiveStaleHumanContext_shouldThrowForWrongIssueType() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.PROJECTION_REFRESH_GAP, null);
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.archiveStaleHumanContext(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("STALE_HUMAN_CONTEXT_INPUT"));
    }

    @Test
    void archiveStaleHumanContext_shouldHandleNullDetails() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT, null);
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.archiveStaleHumanContext(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("Could not extract"));
    }

    // =================== refreshMissingProjection ===================

    @Test
    void refreshMissingProjection_shouldCheckFreshnessAndResolve() {
        UUID findingId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH, null);
        Source source = Source.builder().id(sourceId).build();

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())).thenReturn(List.of(source));
        when(freshnessService.check(project.getId(), sourceId)).thenReturn(null);
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.refreshMissingProjection(project.getId(), findingId, UUID.randomUUID(), "Done");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(freshnessService).check(project.getId(), sourceId);
    }

    @Test
    void refreshMissingProjection_shouldThrowForWrongIssueType() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING, null);
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.refreshMissingProjection(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("MISSING_PROJECTION_REFRESH"));
    }

    @Test
    void refreshMissingProjection_shouldHandleMultipleSources() {
        UUID findingId = UUID.randomUUID();
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH, null);
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        Source source1 = Source.builder().id(s1).build();
        Source source2 = Source.builder().id(s2).build();

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())).thenReturn(List.of(source1, source2));
        when(freshnessService.check(project.getId(), s1)).thenReturn(null);
        when(freshnessService.check(project.getId(), s2)).thenReturn(null);
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.refreshMissingProjection(project.getId(), findingId, UUID.randomUUID(), "Done");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(freshnessService).check(project.getId(), s1);
        verify(freshnessService).check(project.getId(), s2);
    }

    // =================== refreshProjectUnderstanding ===================

    @Test
    void refreshProjectUnderstanding_shouldChainFreshnessAndUnderstanding() {
        UUID findingId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING, null);
        Source source = Source.builder().id(sourceId).build();

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())).thenReturn(List.of(source));
        when(freshnessService.check(project.getId(), sourceId)).thenReturn(null);
        when(understandingService.execute(any(), any())).thenReturn(null);
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.refreshProjectUnderstanding(project.getId(), findingId, UUID.randomUUID(), "Refreshed");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(freshnessService).check(project.getId(), sourceId);
        verify(understandingService).execute(any(), any());
    }

    @Test
    void refreshProjectUnderstanding_shouldContinueWhenFreshnessCheckFails() {
        UUID findingId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING, null);
        Source source = Source.builder().id(sourceId).build();

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())).thenReturn(List.of(source));
        when(freshnessService.check(project.getId(), sourceId)).thenThrow(new RuntimeException("Git error"));
        when(understandingService.execute(any(), any())).thenReturn(null);
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.refreshProjectUnderstanding(project.getId(), findingId, UUID.randomUUID(), "Refreshed despite freshness failure");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(understandingService).execute(any(), any());
    }

    @Test
    void refreshProjectUnderstanding_shouldContinueWhenUnderstandingFailsForOneSource() {
        UUID findingId = UUID.randomUUID();
        UUID sourceId1 = UUID.randomUUID();
        UUID sourceId2 = UUID.randomUUID();
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING, null);
        Source source1 = Source.builder().id(sourceId1).build();
        Source source2 = Source.builder().id(sourceId2).build();

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())).thenReturn(List.of(source1, source2));
        when(freshnessService.check(project.getId(), sourceId1)).thenReturn(null);
        when(freshnessService.check(project.getId(), sourceId2)).thenReturn(null);
        when(understandingService.execute(eq(project.getId()), argThat(req -> req.sourceId().equals(sourceId1))))
                .thenThrow(new RuntimeException("Analysis failed for source 1"));
        when(understandingService.execute(eq(project.getId()), argThat(req -> req.sourceId().equals(sourceId2))))
                .thenReturn(null);
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.refreshProjectUnderstanding(project.getId(), findingId, UUID.randomUUID(), "Partial success");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(understandingService, times(2)).execute(any(), any());
    }

    @Test
    void refreshProjectUnderstanding_shouldThrowForWrongIssueType() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.PROJECTION_REFRESH_GAP, null);
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.refreshProjectUnderstanding(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("STALE_PROJECT_UNDERSTANDING"));
    }

    @Test
    void refreshProjectUnderstanding_shouldHandleNoActiveSources() {
        UUID findingId = UUID.randomUUID();
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING, null);

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId())).thenReturn(List.of());
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.refreshProjectUnderstanding(project.getId(), findingId, UUID.randomUUID(), "Done");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(understandingService, never()).execute(any(), any());
    }
}
