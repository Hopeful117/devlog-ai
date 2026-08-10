package com.hopeful117.devlogai.knowledge.relation.service;

import com.hopeful117.devlogai.knowledge.relation.dto.request.CreateKnowledgeRelationRequest;
import com.hopeful117.devlogai.knowledge.relation.dto.response.KnowledgeRelationResponse;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelation;
import com.hopeful117.devlogai.knowledge.relation.mapper.KnowledgeRelationMapper;
import com.hopeful117.devlogai.knowledge.relation.repository.KnowledgeRelationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class KnowledgeRelationServiceImpl implements KnowledgeRelationService {

    private final KnowledgeRelationRepository knowledgeRelationRepository;
    private final ProjectRepository projectRepository;
    private final KnowledgeRelationMapper knowledgeRelationMapper;

    @Override
    public KnowledgeRelationResponse create(CreateKnowledgeRelationRequest request) {
        if (request.getSourceEntityType() == request.getTargetEntityType()
                && request.getSourceEntityId().equals(request.getTargetEntityId())) {
            throw new IllegalArgumentException(
                    "Source and target must be different entities"
            );
        }

        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Project",
                                request.getProjectId()
                        )
                );

        KnowledgeRelation relation = knowledgeRelationMapper.toEntity(request);
        relation.setProject(project);

        KnowledgeRelation savedRelation =
                knowledgeRelationRepository.save(relation);

        return knowledgeRelationMapper.toResponse(savedRelation);
    }

    @Override
    public KnowledgeRelationResponse getById(UUID id) {
        KnowledgeRelation relation = knowledgeRelationRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "KnowledgeRelation",
                                id
                        )
                );

        return knowledgeRelationMapper.toResponse(relation);
    }

    @Override
    public List<KnowledgeRelationResponse> getByProject(UUID projectId) {
        return knowledgeRelationRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(knowledgeRelationMapper::toResponse)
                .toList();
    }

    @Override
    public List<KnowledgeRelationResponse> getBySource(
            EntityType sourceEntityType,
            UUID sourceEntityId) {
        return knowledgeRelationRepository
                .findBySourceEntityTypeAndSourceEntityId(
                        sourceEntityType,
                        sourceEntityId
                )
                .stream()
                .map(knowledgeRelationMapper::toResponse)
                .toList();
    }

    @Override
    public List<KnowledgeRelationResponse> getByTarget(
            EntityType targetEntityType,
            UUID targetEntityId) {
        return knowledgeRelationRepository
                .findByTargetEntityTypeAndTargetEntityId(
                        targetEntityType,
                        targetEntityId
                )
                .stream()
                .map(knowledgeRelationMapper::toResponse)
                .toList();
    }

    @Override
    public void delete(UUID id) {
        KnowledgeRelation relation = knowledgeRelationRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "KnowledgeRelation",
                                id
                        )
                );

        knowledgeRelationRepository.delete(relation);
    }
}
