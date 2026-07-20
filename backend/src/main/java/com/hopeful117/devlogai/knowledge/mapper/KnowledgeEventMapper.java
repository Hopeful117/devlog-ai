package com.hopeful117.devlogai.knowledge.mapper;

import com.hopeful117.devlogai.knowledge.dto.request.CreateKnowledgeEventRequest;
import com.hopeful117.devlogai.knowledge.dto.response.KnowledgeEventResponse;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface KnowledgeEventMapper  {
    @Mapping(target = "projectId", source = "project.id")
    KnowledgeEventResponse toResponse(KnowledgeEvent entity);


    KnowledgeEvent toEntity(CreateKnowledgeEventRequest request);
}
