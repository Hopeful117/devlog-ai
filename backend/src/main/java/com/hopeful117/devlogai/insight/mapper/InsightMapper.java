package com.hopeful117.devlogai.insight.mapper;

import com.hopeful117.devlogai.insight.dto.request.CreateInsightRequest;
import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.entity.Insight;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InsightMapper {

    @Mapping(
            target = "projectId",
            source = "project.id"
    )
    @Mapping(
            target = "analysisId",
            source = "analysis.id"
    )
    InsightResponse toResponse(
            Insight insight
    );

    Insight toEntity(
            CreateInsightRequest request
    );
}

