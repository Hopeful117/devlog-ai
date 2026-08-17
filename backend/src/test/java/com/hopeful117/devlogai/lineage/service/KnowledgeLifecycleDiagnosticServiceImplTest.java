package com.hopeful117.devlogai.lineage.service;

import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.lineage.dto.KnowledgeLifecycleDiagnosticResponse;
import com.hopeful117.devlogai.lineage.dto.KnowledgeLifecycleStageResponse;
import com.hopeful117.devlogai.lineage.dto.KnowledgeLifecycleStatus;
import com.hopeful117.devlogai.lineage.dto.LineageStageStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.validation.entity.Validation;
import com.hopeful117.devlogai.validation.entity.ValidationDecision;
import com.hopeful117.devlogai.validation.repository.ValidationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeLifecycleDiagnosticServiceImplTest {

    @Mock ValidatableProposalRepository proposalRepository;
    @Mock ValidationRepository validationRepository;
    @Mock DecisionRepository decisionRepository;
    @Mock InsightRepository insightRepository;
    @Mock EngineeringEventRepository engineeringEventRepository;
    @InjectMocks KnowledgeLifecycleDiagnosticServiceImpl service;

    private final UUID proposalId = UUID.randomUUID();

    private Validation validation(ValidationDecision decision) {
        return Validation.builder().id(UUID.randomUUID()).decision(decision).build();
    }

    private ValidatableProposal proposal(ProposalType type, ProposalStatus status) {
        return ValidatableProposal.builder().id(proposalId).type(type).status(status).build();
    }

    private void stubProposal(ProposalType type, ProposalStatus status) {
        when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal(type, status)));
    }

    private KnowledgeLifecycleStageResponse stage(KnowledgeLifecycleDiagnosticResponse r, String name) {
        return r.stages().stream().filter(s -> s.stage().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void proposedWithNoValidationIsPendingAndComplete() {
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.PROPOSED);
        when(validationRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());
        when(decisionRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.COMPLETE, r.lifecycleStatus());
        assertEquals(LineageStageStatus.PENDING, stage(r, "Validation").status());
        assertEquals(LineageStageStatus.PENDING, stage(r, "Promoted Knowledge").status());
        assertTrue(r.findings().isEmpty());
    }

    @Test
    void proposedWithValidationIsInconsistentAndBroken() {
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.PROPOSED);
        when(validationRepository.findByProposalId(proposalId)).thenReturn(Optional.of(validation(ValidationDecision.ACCEPTED)));
        when(decisionRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.BROKEN, r.lifecycleStatus());
        assertEquals(LineageStageStatus.INCONSISTENT, stage(r, "Validation").status());
        assertTrue(r.findings().stream().anyMatch(f -> f.contains("PROPOSED")));
    }

    @Test
    void rejectedWithNoValidationIsBroken() {
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.REJECTED);
        when(validationRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());
        when(decisionRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.BROKEN, r.lifecycleStatus());
        assertEquals(LineageStageStatus.MISSING, stage(r, "Validation").status());
        assertEquals(LineageStageStatus.NOT_APPLICABLE, stage(r, "Promoted Knowledge").status());
    }

    @Test
    void rejectedWithRejectedValidationIsComplete() {
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.REJECTED);
        when(validationRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(validation(ValidationDecision.REJECTED)));
        when(decisionRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.COMPLETE, r.lifecycleStatus());
        assertEquals(LineageStageStatus.PRESENT, stage(r, "Validation").status());
        assertEquals(LineageStageStatus.NOT_APPLICABLE, stage(r, "Promoted Knowledge").status());
        assertTrue(r.findings().isEmpty());
    }

    @Test
    void proposedWithPromotedArtifactIsInconsistentAndBroken() {
        UUID decisionId = UUID.randomUUID();
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.PROPOSED);
        when(validationRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());
        when(decisionRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(Decision.builder().id(decisionId).build()));

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.BROKEN, r.lifecycleStatus());
        assertEquals(LineageStageStatus.INCONSISTENT, stage(r, "Promoted Knowledge").status());
        assertTrue(r.findings().stream().anyMatch(f -> f.contains("PROPOSED") && f.contains("promoted")));
    }

    @Test
    void rejectedWithPromotedArtifactIsInconsistentAndBroken() {
        UUID decisionId = UUID.randomUUID();
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.REJECTED);
        when(validationRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(validation(ValidationDecision.REJECTED)));
        when(decisionRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(Decision.builder().id(decisionId).build()));

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.BROKEN, r.lifecycleStatus());
        assertEquals(LineageStageStatus.INCONSISTENT, stage(r, "Promoted Knowledge").status());
    }

    @Test
    void acceptedWithAcceptedValidationAndDecisionIsComplete() {
        UUID decisionId = UUID.randomUUID();
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.ACCEPTED);
        when(validationRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(validation(ValidationDecision.ACCEPTED)));
        when(decisionRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(Decision.builder().id(decisionId).build()));

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.COMPLETE, r.lifecycleStatus());
        assertEquals(LineageStageStatus.PRESENT, stage(r, "Validation").status());
        assertEquals(LineageStageStatus.PRESENT, stage(r, "Promoted Knowledge").status());
        assertEquals(decisionId, stage(r, "Promoted Knowledge").artifactId());
        assertTrue(r.findings().isEmpty());
    }

    @Test
    void acceptedWithAcceptedValidationAndMissingDecisionIsInvariantViolation() {
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.ACCEPTED);
        when(validationRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(validation(ValidationDecision.ACCEPTED)));
        when(decisionRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.BROKEN, r.lifecycleStatus());
        assertEquals(LineageStageStatus.MISSING, stage(r, "Promoted Knowledge").status());
        assertTrue(r.findings().stream().anyMatch(f -> f.contains("MUST produce exactly one")));
        assertTrue(r.findings().stream().anyMatch(f -> f.startsWith("An ACCEPTED")));
    }

    @Test
    void acceptedWithoutValidationIsBroken() {
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.ACCEPTED);
        when(validationRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());
        when(decisionRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.BROKEN, r.lifecycleStatus());
        assertEquals(LineageStageStatus.MISSING, stage(r, "Validation").status());
    }

    @Test
    void acceptedWithRejectedValidationIsInconsistent() {
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.ACCEPTED);
        when(validationRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(validation(ValidationDecision.REJECTED)));
        when(decisionRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(Decision.builder().id(UUID.randomUUID()).build()));

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.BROKEN, r.lifecycleStatus());
        assertEquals(LineageStageStatus.INCONSISTENT, stage(r, "Validation").status());
    }

    @Test
    void insightUsesInsightRepository() {
        UUID insightId = UUID.randomUUID();
        stubProposal(ProposalType.INSIGHT, ProposalStatus.ACCEPTED);
        when(validationRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(validation(ValidationDecision.ACCEPTED)));
        Insight insight = Insight.builder().id(insightId).build();
        when(insightRepository.findByProposalIdIn(List.of(proposalId))).thenReturn(List.of(insight));

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.COMPLETE, r.lifecycleStatus());
        assertEquals(insightId, stage(r, "Promoted Knowledge").artifactId());
    }

    @Test
    void engineeringEventUsesEventRepository() {
        UUID eventId = UUID.randomUUID();
        stubProposal(ProposalType.ENGINEERING_EVENT, ProposalStatus.ACCEPTED);
        when(validationRepository.findByProposalId(proposalId))
                .thenReturn(Optional.of(validation(ValidationDecision.ACCEPTED)));
        EngineeringEvent event = EngineeringEvent.builder().id(eventId)
                .category(EngineeringEventCategory.ENGINEERING_IMPROVEMENT).build();
        when(engineeringEventRepository.findByProposalIdIn(List.of(proposalId))).thenReturn(List.of(event));

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.COMPLETE, r.lifecycleStatus());
        assertEquals(eventId, stage(r, "Promoted Knowledge").artifactId());
    }

    @Test
    void unsupportedTypeIsNotApplicableAndNeverBroken() {
        stubProposal(ProposalType.CHALLENGE, ProposalStatus.ACCEPTED);

        KnowledgeLifecycleDiagnosticResponse r = service.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.NOT_APPLICABLE, r.lifecycleStatus());
        assertTrue(r.findings().isEmpty());
        verify(validationRepository, never()).findByProposalId(any());
    }

    @Test
    void missingProposalThrowsEntityNotFound() {
        when(proposalRepository.findById(proposalId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.diagnose(proposalId));
    }

    @Test
    void diagnosisIsReadOnly() {
        stubProposal(ProposalType.ENGINEERING_DECISION, ProposalStatus.ACCEPTED);
        when(validationRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());
        when(decisionRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());

        service.diagnose(proposalId);

        verify(proposalRepository, never()).save(any());
        verify(validationRepository, never()).save(any());
        verify(decisionRepository, never()).save(any());
    }
}