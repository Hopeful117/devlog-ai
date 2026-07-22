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
    @Mapping(target = "insight", expression = "java(insightDetails(entity))")
    ValidatableProposalResponse toResponse(
            ValidatableProposal entity
    );

    default com.hopeful117.devlogai.proposal.dto.response.InsightProposalPayloadResponse insightDetails(
            ValidatableProposal entity) {
        if (entity.getType() != com.hopeful117.devlogai.proposal.entity.ProposalType.INSIGHT
                || entity.getPayload() == null) return null;
        return new com.hopeful117.devlogai.proposal.dto.response.InsightProposalPayloadResponse(
                text(entity, "insightType"), text(entity, "title"),
                text(entity, "summary"), text(entity, "rationale"));
    }

    private String text(ValidatableProposal entity, String key) {
        Object value = entity.getPayload().get(key);
        return value instanceof String text ? text : null;
    }
}
