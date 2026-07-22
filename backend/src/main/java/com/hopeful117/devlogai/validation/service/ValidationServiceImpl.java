package com.hopeful117.devlogai.validation.service;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.insight.service.InsightPromotionService;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.validation.dto.request.CreateValidationRequest;
import com.hopeful117.devlogai.validation.dto.response.ValidationResponse;
import com.hopeful117.devlogai.validation.entity.Validation;
import com.hopeful117.devlogai.validation.entity.ValidationDecision;
import com.hopeful117.devlogai.validation.mapper.ValidationMapper;
import com.hopeful117.devlogai.validation.repository.ValidationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional
public class ValidationServiceImpl implements ValidationService {
    private final ValidationRepository validationRepository;

    private final ValidatableProposalRepository proposalRepository;

    private final ValidationMapper validationMapper;
    private final InsightPromotionService insightPromotionService;

    @Override
    @Transactional
    public ValidationResponse validate(
            CreateValidationRequest request
    ) {

        ValidatableProposal proposal =
                proposalRepository.findById(request.proposalId())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Proposal", request.proposalId()));

        if (proposal.getStatus() != ProposalStatus.PROPOSED) {
            throw new ConflictException(
                    "Proposal has already been decided"
            );
        }

        if (validationRepository.existsByProposalId(
                request.proposalId()
        )) {
            throw new ConflictException(
                    "Proposal has already been validated"
            );
        }

        Validation validation =
                validationMapper.toEntity(request);

        validation.setProposal(proposal);

        proposal.setStatus(
                request.decision() == ValidationDecision.ACCEPTED
                        ? ProposalStatus.ACCEPTED
                        : ProposalStatus.REJECTED
        );


        proposalRepository.save(proposal);

        Validation savedValidation =
                validationRepository.save(validation);

        if (request.decision() == ValidationDecision.ACCEPTED) {
            insightPromotionService.promote(proposal, savedValidation, request.insightSeverity());
        }

        return validationMapper.toResponse(savedValidation);
    }

    @Override
    public ValidationResponse getById(UUID id) {

        return validationRepository.findById(id)
                .map(validationMapper::toResponse)
                        .orElseThrow(() -> new EntityNotFoundException("Validation", id));
    }

    @Override
    public ValidationResponse getByProposalId(
            UUID proposalId
    ) {

        return validationRepository
                .findByProposalId(proposalId)
                .map(validationMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Validation for proposal", proposalId));
    }
}
