package com.hopeful117.devlogai.fact.mapper;

import com.hopeful117.devlogai.fact.dto.request.CreateFactRequest;
import com.hopeful117.devlogai.fact.dto.response.FactResponse;
import com.hopeful117.devlogai.fact.entity.Fact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FactMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "analysis", ignore = true)
    @Mapping(target = "detectedAt", ignore = true)
    Fact toEntity(CreateFactRequest request);

    @Mapping(target = "analysisId", source = "analysis.id")
    FactResponse toResponse(Fact entity);
}
