package com.hopeful117.devlogai.knowledge.relation.mapper;

import com.hopeful117.devlogai.knowledge.relation.dto.request.CreateKnowledgeRelationRequest;
import com.hopeful117.devlogai.knowledge.relation.dto.response.KnowledgeRelationResponse;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface KnowledgeRelationMapper {

    @Mapping(target = "projectId", source = "project.id")
    KnowledgeRelationResponse toResponse(KnowledgeRelation relation);

    KnowledgeRelation toEntity(CreateKnowledgeRelationRequest request);
}
