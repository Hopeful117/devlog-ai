package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceFindingMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.insight.service.InsightService;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.shared.exception.ConflictException;
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
class KnowledgeDeduplicationServiceTest {

    @Mock MaintenanceFindingRepository findingRepository;
    @Mock MaintenanceFindingMapper findingMapper;
    @Mock InsightService insightService;

    @InjectMocks KnowledgeDeduplicationServiceImpl service;

    private Project project = Project.builder().id(UUID.randomUUID()).build();

    private MaintenanceFinding buildFinding(MaintenanceFindingIssueType issueType, String details) {
        return MaintenanceFinding.builder()
                .id(UUID.randomUUID())
                .project(project)
                .contextSurface(MaintenanceContextSurface.PROJECT_UNDERSTANDING)
                .issueType(issueType)
                .severity(MaintenanceFindingSeverity.MEDIUM)
                .status(MaintenanceFindingStatus.OPEN)
                .suggestedAction(MaintenanceSuggestedActionCategory.REVIEW)
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

    // =================== mergeExactDuplicate ===================

    @Test
    void mergeExactDuplicate_shouldSupersedeNonCanonicalInsights() {
        UUID findingId = UUID.randomUUID();
        UUID insight1 = UUID.randomUUID();
        UUID insight2 = UUID.randomUUID();
        UUID insight3 = UUID.randomUUID();
        String details = "Duplicate insights: " + insight1 + ", " + insight2 + ", " + insight3;
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE, details);

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.mergeExactDuplicate(project.getId(), findingId, UUID.randomUUID(), "Merged");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(insightService).supersedeInsight(insight2, insight1);
        verify(insightService).supersedeInsight(insight3, insight1);
        verify(insightService, never()).supersedeInsight(eq(insight1), any());
    }

    @Test
    void mergeExactDuplicate_shouldThrowWhenFindingNotFound() {
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                service.mergeExactDuplicate(project.getId(), UUID.randomUUID(), UUID.randomUUID(), "comment"));
    }

    @Test
    void mergeExactDuplicate_shouldThrowForWrongIssueType() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.PROJECTION_REFRESH_GAP, null);
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.mergeExactDuplicate(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("TRUSTED_KNOWLEDGE_EXACT_DUPLICATE"));
    }

    @Test
    void mergeExactDuplicate_shouldThrowWhenInsightIdsCannotBeExtracted() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE, "No UUIDs here");
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.mergeExactDuplicate(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("Could not extract insight IDs"));
    }

    @Test
    void mergeExactDuplicate_shouldThrowWhenOnlyOneInsightId() {
        UUID insight1 = UUID.randomUUID();
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE,
                "Single insight: " + insight1);
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.mergeExactDuplicate(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("Could not extract insight IDs"));
    }

    @Test
    void mergeExactDuplicate_shouldHandleSupersedeFailureGracefully() {
        UUID findingId = UUID.randomUUID();
        UUID insight1 = UUID.randomUUID();
        UUID insight2 = UUID.randomUUID();
        String details = insight1 + " " + insight2;
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE, details);

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(insightService.supersedeInsight(insight2, insight1)).thenThrow(new RuntimeException("Insight not found"));
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.mergeExactDuplicate(project.getId(), findingId, UUID.randomUUID(), "Merged");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
    }

    // =================== resolveSemanticDuplicate ===================

    @Test
    void resolveSemanticDuplicate_shouldSupersedeNonCanonicalInsights() {
        UUID findingId = UUID.randomUUID();
        UUID insight1 = UUID.randomUUID();
        UUID insight2 = UUID.randomUUID();
        String details = "Semantic duplicates: " + insight1 + ", " + insight2;
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE, details);

        when(findingRepository.findByIdAndProject_Id(findingId, project.getId())).thenReturn(Optional.of(finding));
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingMapper.toResponse(any(MaintenanceFinding.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        MaintenanceFindingResponse result = service.resolveSemanticDuplicate(project.getId(), findingId, UUID.randomUUID(), "Resolved");

        assertEquals(MaintenanceFindingStatus.RESOLVED, result.status());
        verify(insightService).supersedeInsight(insight2, insight1);
    }

    @Test
    void resolveSemanticDuplicate_shouldThrowForWrongIssueType() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE, null);
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.resolveSemanticDuplicate(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE"));
    }

    @Test
    void resolveSemanticDuplicate_shouldThrowWhenInsightIdsCannotBeExtracted() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE, "No UUIDs");
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.resolveSemanticDuplicate(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("Could not extract insight IDs"));
    }

    @Test
    void resolveSemanticDuplicate_shouldHandleNullDetails() {
        MaintenanceFinding finding = buildFinding(MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE, null);
        when(findingRepository.findByIdAndProject_Id(any(), any())).thenReturn(Optional.of(finding));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.resolveSemanticDuplicate(project.getId(), finding.getId(), UUID.randomUUID(), "comment"));
        assertTrue(ex.getMessage().contains("Could not extract insight IDs"));
    }
}
