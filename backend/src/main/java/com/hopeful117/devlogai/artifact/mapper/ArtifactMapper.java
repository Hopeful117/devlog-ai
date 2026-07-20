package com.hopeful117.devlogai.artifact.mapper;

import com.hopeful117.devlogai.artifact.dto.request.CreateArtifactRequest;
import com.hopeful117.devlogai.artifact.dto.response.ArtifactResponse;
import com.hopeful117.devlogai.artifact.entity.Artifact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ArtifactMapper {
    @Mapping(target = "projectId", source = "project.id")
    ArtifactResponse toResponse(Artifact artifact);


    Artifact toEntity(CreateArtifactRequest request);
}
