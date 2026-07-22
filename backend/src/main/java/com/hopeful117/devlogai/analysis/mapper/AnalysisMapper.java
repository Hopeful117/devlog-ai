package com.hopeful117.devlogai.analysis.mapper;

import com.hopeful117.devlogai.analysis.dto.request.CreateAnalysisRequest;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AnalysisMapper {
    @Mapping(
            target = "projectId",
            source = "project.id"
    )
    AnalysisResponse toResponse(
            Analysis analysis
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "intentId", ignore = true)
    @Mapping(target = "intentVersion", ignore = true)
    @Mapping(target = "userGuidance", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Analysis toEntity(
            CreateAnalysisRequest request
    );
}
