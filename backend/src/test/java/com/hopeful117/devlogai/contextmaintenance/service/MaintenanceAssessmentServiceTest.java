package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceAssessmentRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceAssessmentResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceAssessmentMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceAssessmentRepository;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceAssessmentServiceTest {

    @Mock MaintenanceAssessmentRepository assessmentRepository;
    @Mock MaintenanceFindingRepository findingRepository;
    @Mock MaintenanceAssessmentMapper assessmentMapper;

    @InjectMocks MaintenanceAssessmentServiceImpl service;

    @Test
    void shouldCreateAssessmentForValidFinding() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();

        MaintenanceFinding finding = MaintenanceFinding.builder()
                .id(findingId)
                .project(com.hopeful117.devlogai.project.entity.Project.builder().id(projectId).build())
                .build();

        CreateMaintenanceAssessmentRequest request = new CreateMaintenanceAssessmentRequest(
                findingId,
                MaintenanceAssessmentConfidenceLevel.HIGH,
                MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                "The finding appears to be a duplicate.",
                null
        );

        MaintenanceAssessment saved = MaintenanceAssessment.builder()
                .id(UUID.randomUUID())
                .finding(finding)
                .projectId(projectId)
                .confidenceLevel(MaintenanceAssessmentConfidenceLevel.HIGH)
                .semanticClassification(MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE)
                .recommendedAction(MaintenanceAssessmentRecommendedAction.ESCALATE)
                .rationale("The finding appears to be a duplicate.")
                .build();

        MaintenanceAssessmentResponse response = new MaintenanceAssessmentResponse(
                saved.getId(), projectId, findingId,
                MaintenanceAssessmentConfidenceLevel.HIGH,
                MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                "The finding appears to be a duplicate.",
                null,
                Instant.now(), Instant.now()
        );

        when(findingRepository.findByIdAndProject_Id(findingId, projectId))
                .thenReturn(Optional.of(finding));
        when(assessmentRepository.save(any(MaintenanceAssessment.class)))
                .thenReturn(saved);
        when(assessmentMapper.toResponse(saved)).thenReturn(response);

        MaintenanceAssessmentResponse result = service.create(projectId, request);

        assertEquals(response, result);
        verify(assessmentRepository).save(argThat(assessment ->
                assessment.getFinding().equals(finding)
                        && assessment.getProjectId().equals(projectId)
                        && assessment.getConfidenceLevel() == MaintenanceAssessmentConfidenceLevel.HIGH
        ));
    }

    @Test
    void shouldRejectAssessmentForNonExistentFinding() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();

        CreateMaintenanceAssessmentRequest request = new CreateMaintenanceAssessmentRequest(
                findingId,
                MaintenanceAssessmentConfidenceLevel.HIGH,
                MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                "Rationale",
                null
        );

        when(findingRepository.findByIdAndProject_Id(findingId, projectId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(projectId, request));
    }

    @Test
    void shouldRejectAssessmentForFindingBelongingToDifferentProject() {
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();

        MaintenanceFinding finding = MaintenanceFinding.builder()
                .id(findingId)
                .project(com.hopeful117.devlogai.project.entity.Project.builder().id(otherProjectId).build())
                .build();

        CreateMaintenanceAssessmentRequest request = new CreateMaintenanceAssessmentRequest(
                findingId,
                MaintenanceAssessmentConfidenceLevel.HIGH,
                MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                "Rationale",
                null
        );

        when(findingRepository.findByIdAndProject_Id(findingId, projectId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(projectId, request));
    }

    @Test
    void shouldRetrieveAssessmentsByProject() {
        UUID projectId = UUID.randomUUID();

        MaintenanceAssessment assessment = MaintenanceAssessment.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .build();

        MaintenanceAssessmentResponse response = new MaintenanceAssessmentResponse(
                assessment.getId(), projectId, UUID.randomUUID(),
                MaintenanceAssessmentConfidenceLevel.MEDIUM,
                MaintenanceAssessmentSemanticClassification.UNCERTAIN,
                MaintenanceAssessmentRecommendedAction.MONITOR,
                "Uncertain",
                null,
                Instant.now(), Instant.now()
        );

        when(assessmentRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId))
                .thenReturn(List.of(assessment));
        when(assessmentMapper.toResponse(List.of(assessment))).thenReturn(List.of(response));

        List<MaintenanceAssessmentResponse> results = service.getByProject(projectId);

        assertEquals(1, results.size());
        assertEquals(response, results.getFirst());
    }

    @Test
    void shouldRetrieveAssessmentsByFinding() {
        UUID projectId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();

        MaintenanceAssessment assessment = MaintenanceAssessment.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .build();

        MaintenanceAssessmentResponse response = new MaintenanceAssessmentResponse(
                assessment.getId(), projectId, findingId,
                MaintenanceAssessmentConfidenceLevel.LOW,
                MaintenanceAssessmentSemanticClassification.ISOLATED_SIGNAL,
                MaintenanceAssessmentRecommendedAction.NO_ACTION,
                "Isolated signal",
                null,
                Instant.now(), Instant.now()
        );

        when(assessmentRepository.findByFinding_IdAndProjectIdOrderByCreatedAtDescIdDesc(findingId, projectId))
                .thenReturn(List.of(assessment));
        when(assessmentMapper.toResponse(List.of(assessment))).thenReturn(List.of(response));

        List<MaintenanceAssessmentResponse> results = service.getByFinding(projectId, findingId);

        assertEquals(1, results.size());
        assertEquals(response, results.getFirst());
    }
}
