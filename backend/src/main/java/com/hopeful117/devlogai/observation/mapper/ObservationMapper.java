package com.hopeful117.devlogai.observation.mapper;

import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.observation.dto.response.ObservationResponse;
import com.hopeful117.devlogai.observation.entity.Observation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface ObservationMapper {

    @Mapping(target = "analysisId", source = "analysis.id")
    @Mapping(target = "supportingFactIds", source = "supportingFacts")
    ObservationResponse toResponse(Observation entity);

    default UUID toId(Fact fact) {
        return fact.getId();
    }
}
