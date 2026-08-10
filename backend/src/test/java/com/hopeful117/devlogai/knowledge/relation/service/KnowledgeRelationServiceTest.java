package com.hopeful117.devlogai.knowledge.relation.service;

import com.hopeful117.devlogai.knowledge.relation.dto.request.CreateKnowledgeRelationRequest;
import com.hopeful117.devlogai.knowledge.relation.dto.response.KnowledgeRelationResponse;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelation;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.knowledge.relation.mapper.KnowledgeRelationMapper;
import com.hopeful117.devlogai.knowledge.relation.repository.KnowledgeRelationRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeRelationServiceTest {

    @Mock
    KnowledgeRelationRepository knowledgeRelationRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    KnowledgeRelationMapper knowledgeRelationMapper;

    @InjectMocks
    KnowledgeRelationServiceImpl knowledgeRelationService;

    @Test
    void shouldCreateRelationSuccessfully() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        CreateKnowledgeRelationRequest request =
                new CreateKnowledgeRelationRequest(
                        projectId,
                        EntityType.CHALLENGE,
                        sourceId,
                        EntityType.DECISION,
                        targetId,
                        KnowledgeRelationType.RESOLVES,
                        "Decision resolved the challenge"
                );

        Project project = new Project();
        KnowledgeRelation relation = new KnowledgeRelation();
        KnowledgeRelationResponse response = new KnowledgeRelationResponse(
                UUID.randomUUID(),
                projectId,
                EntityType.CHALLENGE,
                sourceId,
                EntityType.DECISION,
                targetId,
                KnowledgeRelationType.RESOLVES,
                "Decision resolved the challenge",
                null
        );

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(knowledgeRelationMapper.toEntity(request))
                .thenReturn(relation);
        when(knowledgeRelationRepository.save(relation))
                .thenReturn(relation);
        when(knowledgeRelationMapper.toResponse(relation))
                .thenReturn(response);

        KnowledgeRelationResponse result =
                knowledgeRelationService.create(request);

        assertNotNull(result);
        assertEquals(response, result);
        assertEquals(project, relation.getProject());

        verify(projectRepository).findById(projectId);
        verify(knowledgeRelationMapper).toEntity(request);
        verify(knowledgeRelationRepository).save(relation);
        verify(knowledgeRelationMapper).toResponse(relation);
    }

    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        CreateKnowledgeRelationRequest request =
                new CreateKnowledgeRelationRequest(
                        projectId,
                        EntityType.CHALLENGE,
                        sourceId,
                        EntityType.DECISION,
                        targetId,
                        KnowledgeRelationType.RESOLVES,
                        null
                );

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> knowledgeRelationService.create(request)
        );

        verify(projectRepository).findById(projectId);
        verify(knowledgeRelationRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenSourceEqualsTarget() {
        UUID projectId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        CreateKnowledgeRelationRequest request =
                new CreateKnowledgeRelationRequest(
                        projectId,
                        EntityType.CHALLENGE,
                        entityId,
                        EntityType.CHALLENGE,
                        entityId,
                        KnowledgeRelationType.RELATES_TO,
                        null
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> knowledgeRelationService.create(request)
        );

        verify(projectRepository, never()).findById(any());
        verify(knowledgeRelationRepository, never()).save(any());
    }

    @Test
    void shouldReturnRelationsForProject() {
        UUID projectId = UUID.randomUUID();

        KnowledgeRelation relation = new KnowledgeRelation();
        KnowledgeRelationResponse response = new KnowledgeRelationResponse(
                UUID.randomUUID(),
                projectId,
                EntityType.CHALLENGE,
                UUID.randomUUID(),
                EntityType.DECISION,
                UUID.randomUUID(),
                KnowledgeRelationType.RESOLVES,
                null,
                null
        );

        when(knowledgeRelationRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(relation));
        when(knowledgeRelationMapper.toResponse(relation))
                .thenReturn(response);

        List<KnowledgeRelationResponse> result =
                knowledgeRelationService.getByProject(projectId);

        assertEquals(1, result.size());
        assertEquals(response, result.get(0));

        verify(knowledgeRelationRepository)
                .findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Test
    void shouldFindRelationByIdSuccessfully() {
        UUID id = UUID.randomUUID();

        KnowledgeRelation relation = new KnowledgeRelation();
        KnowledgeRelationResponse response = new KnowledgeRelationResponse(
                id,
                UUID.randomUUID(),
                EntityType.CHALLENGE,
                UUID.randomUUID(),
                EntityType.INSIGHT,
                UUID.randomUUID(),
                KnowledgeRelationType.DERIVED_FROM,
                null,
                null
        );

        when(knowledgeRelationRepository.findById(id))
                .thenReturn(Optional.of(relation));
        when(knowledgeRelationMapper.toResponse(relation))
                .thenReturn(response);

        KnowledgeRelationResponse result =
                knowledgeRelationService.getById(id);

        assertEquals(response, result);

        verify(knowledgeRelationRepository).findById(id);
        verify(knowledgeRelationMapper).toResponse(relation);
    }

    @Test
    void shouldThrowExceptionWhenRelationDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(knowledgeRelationRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> knowledgeRelationService.getById(id)
        );

        verify(knowledgeRelationRepository).findById(id);
        verify(knowledgeRelationMapper, never()).toResponse(any());
    }

    @Test
    void shouldReturnRelationsBySource() {
        UUID sourceId = UUID.randomUUID();

        KnowledgeRelation relation = new KnowledgeRelation();
        KnowledgeRelationResponse response = new KnowledgeRelationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntityType.CHALLENGE,
                sourceId,
                EntityType.DECISION,
                UUID.randomUUID(),
                KnowledgeRelationType.ADDRESSES,
                null,
                null
        );

        when(knowledgeRelationRepository
                .findBySourceEntityTypeAndSourceEntityId(
                        EntityType.CHALLENGE, sourceId))
                .thenReturn(List.of(relation));
        when(knowledgeRelationMapper.toResponse(relation))
                .thenReturn(response);

        List<KnowledgeRelationResponse> result =
                knowledgeRelationService.getBySource(
                        EntityType.CHALLENGE, sourceId);

        assertEquals(1, result.size());
        assertEquals(response, result.get(0));

        verify(knowledgeRelationRepository)
                .findBySourceEntityTypeAndSourceEntityId(
                        EntityType.CHALLENGE, sourceId);
    }

    @Test
    void shouldReturnRelationsByTarget() {
        UUID targetId = UUID.randomUUID();

        KnowledgeRelation relation = new KnowledgeRelation();
        KnowledgeRelationResponse response = new KnowledgeRelationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntityType.DECISION,
                UUID.randomUUID(),
                EntityType.CHALLENGE,
                targetId,
                KnowledgeRelationType.RESOLVES,
                null,
                null
        );

        when(knowledgeRelationRepository
                .findByTargetEntityTypeAndTargetEntityId(
                        EntityType.CHALLENGE, targetId))
                .thenReturn(List.of(relation));
        when(knowledgeRelationMapper.toResponse(relation))
                .thenReturn(response);

        List<KnowledgeRelationResponse> result =
                knowledgeRelationService.getByTarget(
                        EntityType.CHALLENGE, targetId);

        assertEquals(1, result.size());
        assertEquals(response, result.get(0));

        verify(knowledgeRelationRepository)
                .findByTargetEntityTypeAndTargetEntityId(
                        EntityType.CHALLENGE, targetId);
    }

    @Test
    void shouldDeleteRelationSuccessfully() {
        UUID id = UUID.randomUUID();

        KnowledgeRelation relation = new KnowledgeRelation();

        when(knowledgeRelationRepository.findById(id))
                .thenReturn(Optional.of(relation));

        knowledgeRelationService.delete(id);

        verify(knowledgeRelationRepository).findById(id);
        verify(knowledgeRelationRepository).delete(relation);
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistentRelation() {
        UUID id = UUID.randomUUID();

        when(knowledgeRelationRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> knowledgeRelationService.delete(id)
        );

        verify(knowledgeRelationRepository).findById(id);
        verify(knowledgeRelationRepository, never()).delete(any());
    }
}
