package com.hopeful117.devlogai.validation.service;

import com.hopeful117.devlogai.validation.dto.request.CreateValidationRequest;
import com.hopeful117.devlogai.validation.dto.response.ValidationResponse;

import java.util.UUID;

public interface ValidationService {
    ValidationResponse validate(
            CreateValidationRequest request
    );

    ValidationResponse getById(
            UUID id
    );

    ValidationResponse getByProposalId(
            UUID proposalId
    );
}
