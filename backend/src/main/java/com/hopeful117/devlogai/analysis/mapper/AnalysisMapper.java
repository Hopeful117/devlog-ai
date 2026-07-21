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

    Analysis toEntity(
            CreateAnalysisRequest request
    );
}
