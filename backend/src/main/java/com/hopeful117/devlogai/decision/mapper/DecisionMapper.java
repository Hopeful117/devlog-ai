package com.hopeful117.devlogai.decision.mapper;

import com.hopeful117.devlogai.decision.dto.request.CreateDecisionRequest;
import com.hopeful117.devlogai.decision.dto.response.DecisionResponse;
import com.hopeful117.devlogai.decision.entity.Decision;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DecisionMapper {

    @Mapping(target = "projectId", source = "project.id")
    DecisionResponse toResponse(Decision decision);


    Decision toEntity(CreateDecisionRequest request);
}
