package com.hopeful117.devlogai.proposal.mapper;

import com.hopeful117.devlogai.proposal.dto.request.CreateValidatableProposalRequest;
import com.hopeful117.devlogai.proposal.dto.response.ValidatableProposalResponse;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ValidatableProposalMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "analysis", ignore = true)
    @Mapping(target = "aiTask", ignore = true)
    @Mapping(target = "sourceIndex", ignore = true)
    @Mapping(target = "confidence", ignore = true)
    @Mapping(target = "supportingFactIds", ignore = true)
    @Mapping(target = "supportingObservationIds", ignore = true)
    @Mapping(target = "evidenceReferences", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "decidedAt", ignore = true)
    ValidatableProposal toEntity(
            CreateValidatableProposalRequest request
    );

    @Mapping(
            target = "projectId",
            source = "project.id"
    )
    @Mapping(
            target = "analysisId",
            source = "analysis.id"
    )
    ValidatableProposalResponse toResponse(
            ValidatableProposal entity
    );
}
