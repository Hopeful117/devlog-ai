package com.hopeful117.devlogai.proposal.controller;

import com.hopeful117.devlogai.proposal.dto.request.CreateValidatableProposalRequest;
import com.hopeful117.devlogai.proposal.dto.response.ValidatableProposalResponse;
import com.hopeful117.devlogai.proposal.service.ValidatableProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/proposals")
@RequiredArgsConstructor
public class ValidatableProposalController {
    private final ValidatableProposalService proposalService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ValidatableProposalResponse create(
            @Valid @RequestBody CreateValidatableProposalRequest request
    ) {
        return proposalService.create(request);
    }

    @GetMapping("/{id}")
    public ValidatableProposalResponse getById(
            @PathVariable UUID id
    ) {
        return proposalService.getById(id);
    }

    @GetMapping("/project/{projectId}")
    public List<ValidatableProposalResponse> getByProjectId(
            @PathVariable UUID projectId
    ) {
        return proposalService.getByProjectId(projectId);
    }

    @GetMapping("/analysis/{analysisId}")
    public List<ValidatableProposalResponse> getByAnalysisId(
            @PathVariable UUID analysisId
    ) {
        return proposalService.getByAnalysisId(analysisId);
    }
}
