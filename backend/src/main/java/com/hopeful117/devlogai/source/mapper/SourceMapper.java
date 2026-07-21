package com.hopeful117.devlogai.source.mapper;

import com.hopeful117.devlogai.source.dto.request.CreateSourceRequest;
import com.hopeful117.devlogai.source.dto.response.SourceResponse;
import com.hopeful117.devlogai.source.entity.Source;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SourceMapper {

    @Mapping(target = "projectId", source = "project.id")
    SourceResponse toResponse(Source source);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "lastSynchronizedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Source toEntity(CreateSourceRequest request);
}
