package com.hopeful117.devlogai.validation.mapper;

import com.hopeful117.devlogai.validation.dto.request.CreateValidationRequest;
import com.hopeful117.devlogai.validation.dto.response.ValidationResponse;
import com.hopeful117.devlogai.validation.entity.Validation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ValidationMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "proposal", ignore = true)
    @Mapping(target = "validatedAt", ignore = true)
    Validation toEntity(
            CreateValidationRequest request
    );

    @Mapping(
            target = "proposalId",
            source = "proposal.id"
    )
    ValidationResponse toResponse(
            Validation entity
    );
}
