package com.hopeful117.devlogai.insight.mapper;

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
    @Mapping(target = "proposalId", source = "proposal.id")
    @Mapping(target = "validationId", source = "validation.id")
    InsightResponse toResponse(
            Insight insight
    );
}
