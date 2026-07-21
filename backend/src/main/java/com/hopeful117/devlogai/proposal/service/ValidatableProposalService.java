package com.hopeful117.devlogai.proposal.service;

import com.hopeful117.devlogai.proposal.dto.request.CreateValidatableProposalRequest;
import com.hopeful117.devlogai.proposal.dto.response.ValidatableProposalResponse;

import java.util.List;
import java.util.UUID;

public interface ValidatableProposalService {
    ValidatableProposalResponse create(
            CreateValidatableProposalRequest request
    );

    ValidatableProposalResponse getById(
            UUID id
    );

    List<ValidatableProposalResponse> getByProjectIdAndStatus(
            UUID projectId
    );

    List<ValidatableProposalResponse>getByProjectId(UUID projectId);

    List<ValidatableProposalResponse> getByAnalysisId(
            UUID analysisId
    );
}
