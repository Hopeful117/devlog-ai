package com.hopeful117.devlogai.story.mapper;

import com.hopeful117.devlogai.story.dto.request.CreateEngineeringStoryRequest;
import com.hopeful117.devlogai.story.dto.response.EngineeringStoryResponse;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EngineeringStoryMapper {

    @Mapping(source = "projectId", target = "project.id")
    EngineeringStory toEntity(CreateEngineeringStoryRequest request);

    @Mapping(source = "project.id", target = "projectId")
    EngineeringStoryResponse toResponse(EngineeringStory entity);
}