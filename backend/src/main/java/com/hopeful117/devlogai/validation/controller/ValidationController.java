package com.hopeful117.devlogai.validation.controller;

import com.hopeful117.devlogai.validation.dto.request.CreateValidationRequest;
import com.hopeful117.devlogai.validation.dto.response.ValidationResponse;
import com.hopeful117.devlogai.validation.service.ValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/validations")
@RequiredArgsConstructor
public class ValidationController {

    private final ValidationService validationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ValidationResponse validate(
            @Valid @RequestBody CreateValidationRequest request
    ) {
        return validationService.validate(request);
    }

    @GetMapping("/{id}")
    public ValidationResponse getById(
            @PathVariable UUID id
    ) {
        return validationService.getById(id);
    }

    @GetMapping("/proposal/{proposalId}")
    public ValidationResponse getByProposalId(
            @PathVariable UUID proposalId
    ) {
        return validationService.getByProposalId(proposalId);
    }
}