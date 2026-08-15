package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateAuditResponse;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.mapper.InsightMapper;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.knowledge.relation.dto.request.CreateKnowledgeRelationRequest;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.knowledge.relation.service.KnowledgeRelationService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {
    @Mock InsightRepository repository;
    @Mock InsightMapper mapper;
    @Mock TrustedKnowledgeDuplicateAuditService duplicateAuditService;
    @Mock KnowledgeRelationService knowledgeRelationService;
    @InjectMocks InsightServiceImpl service;

    @Test
    void shouldReturnInsightById() {
        UUID id = UUID.randomUUID();
        Insight insight = new Insight();
        InsightResponse response = mock(InsightResponse.class);
        when(repository.findById(id)).thenReturn(Optional.of(insight));
        when(mapper.toResponse(insight)).thenReturn(response);
        assertSame(response, service.getById(id));
    }

    @Test
    void shouldRejectUnknownInsight() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.getById(id));
    }

    @Test
    void shouldFilterInsights() {
        UUID projectId = UUID.randomUUID();
        Insight insight = new Insight();
        InsightResponse response = mock(InsightResponse.class);
        when(mapper.toResponse(insight)).thenReturn(response);
        when(repository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(insight));
        when(repository.findByProjectIdAndTypeOrderByCreatedAtDesc(projectId, InsightType.ARCHITECTURAL))
                .thenReturn(List.of(insight));
        when(repository.findByProjectIdAndSeverityOrderByCreatedAtDesc(projectId, InsightSeverity.CRITICAL))
                .thenReturn(List.of(insight));
        when(repository.findByProjectIdAndTypeAndSeverityOrderByCreatedAtDesc(
                projectId, InsightType.ARCHITECTURAL, InsightSeverity.CRITICAL)).thenReturn(List.of(insight));

        assertEquals(List.of(response), service.getByProject(projectId));
        assertEquals(List.of(response), service.getByProjectAndType(projectId, InsightType.ARCHITECTURAL));
        assertEquals(List.of(response), service.getByProjectAndSeverity(projectId, InsightSeverity.CRITICAL));
        assertEquals(List.of(response), service.getByProjectAndTypeAndSeverity(
                projectId, InsightType.ARCHITECTURAL, InsightSeverity.CRITICAL));
    }

    @Test
    void shouldReturnDuplicateAudit() {
        UUID projectId = UUID.randomUUID();
        InsightDuplicateAuditResponse response = new InsightDuplicateAuditResponse(projectId, 0, 0, List.of());
        when(duplicateAuditService.audit(projectId)).thenReturn(response);

        assertSame(response, service.getDuplicateAudit(projectId));
    }

    @Test
    void shouldSupersedeInsightAndCreateResolvesRelation() {
        UUID projectUuid = UUID.randomUUID();
        UUID supersededUuid = UUID.randomUUID();
        UUID supersedingUuid = UUID.randomUUID();
        com.hopeful117.devlogai.project.entity.Project project = com.hopeful117.devlogai.project.entity.Project.builder().id(projectUuid).build();
        Insight superseded = Insight.builder()
                .id(supersededUuid)
                .project(project)
                .title("Old API Architecture")
                .content("Content A")
                .status(InsightStatus.ACTIVE)
                .build();
        Insight superseding = Insight.builder()
                .id(supersedingUuid)
                .project(project)
                .title("Updated API Architecture")
                .content("Content B")
                .status(InsightStatus.ACTIVE)
                .build();
        when(repository.findById(supersededUuid)).thenReturn(Optional.of(superseded));
        when(repository.findById(supersedingUuid)).thenReturn(Optional.of(superseding));
        InsightResponse response = mock(InsightResponse.class);
        when(mapper.toResponse(any())).thenReturn(response);

        InsightResponse result = service.supersedeInsight(supersededUuid, supersedingUuid);

        assertEquals(InsightStatus.SUPERSEDED, superseded.getStatus());
        verify(knowledgeRelationService).create(argThat(req ->
                req.getSourceEntityId().equals(supersededUuid)
                        && req.getTargetEntityId().equals(supersedingUuid)
                        && req.getRelationType() == KnowledgeRelationType.RESOLVES
        ));
        assertSame(response, result);
    }

    @Test
    void shouldSupersedeInsightEvenIfRelationCreationFails() {
        UUID projectUuid = UUID.randomUUID();
        UUID supersededUuid = UUID.randomUUID();
        UUID supersedingUuid = UUID.randomUUID();
        com.hopeful117.devlogai.project.entity.Project project = com.hopeful117.devlogai.project.entity.Project.builder().id(projectUuid).build();
        Insight superseded = Insight.builder()
                .id(supersededUuid)
                .project(project)
                .title("Old")
                .content("A")
                .status(InsightStatus.ACTIVE)
                .build();
        Insight superseding = Insight.builder()
                .id(supersedingUuid)
                .project(project)
                .title("New")
                .content("B")
                .status(InsightStatus.ACTIVE)
                .build();
        when(repository.findById(supersededUuid)).thenReturn(Optional.of(superseded));
        when(repository.findById(supersedingUuid)).thenReturn(Optional.of(superseding));
        InsightResponse response = mock(InsightResponse.class);
        when(mapper.toResponse(any())).thenReturn(response);
        when(knowledgeRelationService.create(any(CreateKnowledgeRelationRequest.class)))
                .thenThrow(new RuntimeException("relation service unavailable"));

        assertDoesNotThrow(() -> service.supersedeInsight(supersededUuid, supersedingUuid));

        assertEquals(InsightStatus.SUPERSEDED, superseded.getStatus());
    }
}
