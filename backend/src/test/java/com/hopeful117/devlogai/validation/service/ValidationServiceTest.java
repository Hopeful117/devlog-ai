package com.hopeful117.devlogai.validation.service;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.validation.dto.request.CreateValidationRequest;
import com.hopeful117.devlogai.validation.dto.response.ValidationResponse;
import com.hopeful117.devlogai.validation.entity.Validation;
import com.hopeful117.devlogai.validation.entity.ValidationDecision;
import com.hopeful117.devlogai.validation.mapper.ValidationMapper;
import com.hopeful117.devlogai.validation.repository.ValidationRepository;
import com.hopeful117.devlogai.insight.service.InsightPromotionService;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ValidationServiceTest {
    @Mock
    ValidationRepository validationRepository;

    @Mock
    ValidationMapper validationMapper;

    @Mock
    ValidatableProposalRepository proposalRepository;

    @Mock
    InsightPromotionService insightPromotionService;

    @InjectMocks
    ValidationServiceImpl service;

    @Test
    void shouldAcceptProposal() {

        UUID proposalId = UUID.randomUUID();

        ValidatableProposal proposal =
                ValidatableProposal.builder()
                        .id(proposalId)
                        .status(ProposalStatus.PROPOSED)
                        .build();

        CreateValidationRequest request =
                new CreateValidationRequest(
                        proposalId,
                        ValidationDecision.ACCEPTED,
                        "Approved",
                        UUID.randomUUID(),
                        InsightSeverity.WARNING
                );

        Validation validation =
                Validation.builder()
                        .build();

        Validation savedValidation =
                Validation.builder()
                        .id(UUID.randomUUID())
                        .proposal(proposal)
                        .decision(ValidationDecision.ACCEPTED)
                        .build();

        ValidationResponse response =
                mock(ValidationResponse.class);

        when(proposalRepository.findById(proposalId))
                .thenReturn(Optional.of(proposal));

        when(validationRepository.existsByProposalId(proposalId))
                .thenReturn(false);

        when(validationMapper.toEntity(request))
                .thenReturn(validation);

        when(validationRepository.save(validation))
                .thenReturn(savedValidation);

        when(validationMapper.toResponse(savedValidation))
                .thenReturn(response);

        ValidationResponse result =
                service.validate(request);

        assertThat(result)
                .isSameAs(response);

        assertThat(proposal.getStatus())
                .isEqualTo(ProposalStatus.ACCEPTED);

        assertThat(validation.getProposal())
                .isSameAs(proposal);

        verify(proposalRepository)
                .save(proposal);

        verify(validationRepository)
                .save(validation);

        verify(insightPromotionService).promote(proposal, savedValidation, InsightSeverity.WARNING);
    }
    @Test
    void shouldRejectWhenProposalDoesNotExist() {

        UUID proposalId = UUID.randomUUID();

        CreateValidationRequest request =
                new CreateValidationRequest(
                        proposalId,
                        ValidationDecision.ACCEPTED,
                        "Approved",
                        UUID.randomUUID()
                );

        when(proposalRepository.findById(proposalId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.validate(request)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Proposal not found: " + proposalId);

        verify(proposalRepository)
                .findById(proposalId);

        verifyNoInteractions(validationRepository);
        verifyNoInteractions(validationMapper);
    }
    @Test
    void shouldRejectAlreadyAcceptedProposal() {

        UUID proposalId = UUID.randomUUID();

        ValidatableProposal proposal =
                ValidatableProposal.builder()
                        .id(proposalId)
                        .status(ProposalStatus.ACCEPTED)
                        .build();

        CreateValidationRequest request =
                new CreateValidationRequest(
                        proposalId,
                        ValidationDecision.REJECTED,
                        "Changed my mind",
                        UUID.randomUUID()
                );

        when(proposalRepository.findById(proposalId))
                .thenReturn(Optional.of(proposal));

        assertThatThrownBy(() ->
                service.validate(request)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Proposal has already been decided");

        verify(proposalRepository)
                .findById(proposalId);

        verifyNoInteractions(validationRepository);
        verifyNoInteractions(validationMapper);

        assertThat(proposal.getStatus())
                .isEqualTo(ProposalStatus.ACCEPTED);
    }
    @Test
    void shouldRejectAlreadyRejectedProposal() {

        UUID proposalId = UUID.randomUUID();

        ValidatableProposal proposal =
                ValidatableProposal.builder()
                        .id(proposalId)
                        .status(ProposalStatus.REJECTED)
                        .build();

        CreateValidationRequest request =
                new CreateValidationRequest(
                        proposalId,
                        ValidationDecision.ACCEPTED,
                        "Trying to accept again",
                        UUID.randomUUID()
                );

        when(proposalRepository.findById(proposalId))
                .thenReturn(Optional.of(proposal));

        assertThatThrownBy(() ->
                service.validate(request)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Proposal has already been decided");

        verify(proposalRepository)
                .findById(proposalId);

        verifyNoInteractions(validationRepository);
        verifyNoInteractions(validationMapper);

        assertThat(proposal.getStatus())
                .isEqualTo(ProposalStatus.REJECTED);
    }
    @Test
    void shouldRejectAlreadyValidatedProposal() {

        UUID proposalId = UUID.randomUUID();

        ValidatableProposal proposal =
                ValidatableProposal.builder()
                        .id(proposalId)
                        .status(ProposalStatus.PROPOSED)
                        .build();

        CreateValidationRequest request =
                new CreateValidationRequest(
                        proposalId,
                        ValidationDecision.ACCEPTED,
                        "Approved",
                        UUID.randomUUID()
                );

        when(proposalRepository.findById(proposalId))
                .thenReturn(Optional.of(proposal));

        when(validationRepository.existsByProposalId(proposalId))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.validate(request)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Proposal has already been validated");

        verify(proposalRepository)
                .findById(proposalId);

        verify(validationRepository)
                .existsByProposalId(proposalId);

        verifyNoInteractions(validationMapper);

        verify(proposalRepository, never())
                .save(any());

        verify(validationRepository, never())
                .save(any());

        assertThat(proposal.getStatus())
                .isEqualTo(ProposalStatus.PROPOSED);
    }
    @Test
    void shouldRejectWhenValidationDoesNotExist() {

        UUID validationId = UUID.randomUUID();

        when(validationRepository.findById(validationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getById(validationId)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Validation not found: " + validationId);

        verify(validationRepository)
                .findById(validationId);

        verifyNoInteractions(validationMapper);
    }
    @Test
    void shouldRejectWhenValidationDoesNotExistForProposal() {

        UUID proposalId = UUID.randomUUID();

        when(validationRepository.findByProposalId(proposalId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getByProposalId(proposalId)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Validation not found for proposal: " + proposalId
                );

        verify(validationRepository)
                .findByProposalId(proposalId);

        verifyNoInteractions(validationMapper);
    }


}
