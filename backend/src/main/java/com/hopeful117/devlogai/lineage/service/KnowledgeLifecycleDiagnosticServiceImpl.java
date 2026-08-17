package com.hopeful117.devlogai.lineage.service;

import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeLifecycleDiagnosticServiceImpl implements KnowledgeLifecycleDiagnosticService {

    private final ValidatableProposalRepository proposalRepository;
    private final ValidationRepository validationRepository;
    private final DecisionRepository decisionRepository;
    private final InsightRepository insightRepository;
    private final EngineeringEventRepository engineeringEventRepository;

    private static final String VALIDATION_STAGE = "Validation";
    private static final String PROMOTED_STAGE = "Promoted Knowledge";

    @Override
    public KnowledgeLifecycleDiagnosticResponse diagnose(UUID proposalId) {
        ValidatableProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new EntityNotFoundException("Proposal", proposalId));

        if (!isSupported(proposal.getType())) {
            return new KnowledgeLifecycleDiagnosticResponse(
                    proposalId,
                    proposal.getType(),
                    proposal.getStatus(),
                    KnowledgeLifecycleStatus.NOT_APPLICABLE,
                    List.of(new KnowledgeLifecycleStageResponse(
                            PROMOTED_STAGE, LineageStageStatus.NOT_APPLICABLE, null, "Unsupported proposal type")),
                    List.of());
        }

        Validation validation = validationRepository.findByProposalId(proposalId).orElse(null);
        List<UUID> promoted = resolvePromoted(proposalId, proposal.getType());

        return evaluate(proposal, validation, promoted);
    }

    private boolean isSupported(ProposalType type) {
        return type == ProposalType.ENGINEERING_DECISION
                || type == ProposalType.INSIGHT
                || type == ProposalType.ENGINEERING_EVENT;
    }

    private List<UUID> resolvePromoted(UUID proposalId, ProposalType type) {
        return switch (type) {
            case ENGINEERING_DECISION -> decisionRepository.findByProposalId(proposalId)
                    .map(Decision::getId)
                    .map(List::of)
                    .orElseGet(List::of);
            case INSIGHT -> insightRepository.findByProposalIdIn(List.of(proposalId))
                    .stream().map(Insight::getId).toList();
            case ENGINEERING_EVENT -> engineeringEventRepository.findByProposalIdIn(List.of(proposalId))
                    .stream().map(EngineeringEvent::getId).toList();
            default -> List.of();
        };
    }

    private KnowledgeLifecycleDiagnosticResponse evaluate(
            ValidatableProposal proposal,
            Validation validation,
            List<UUID> promoted
    ) {
        ProposalStatus status = proposal.getStatus();

        ValidationStageResult validationStage = validationStage(status, validation);
        PromotedStageResult promotedStage = promotedStage(status, promoted, artifactLabel(proposal.getType()));
        boolean broken = validationStage.broken || promotedStage.broken;

        List<String> findings = validationStage.findings;
        findings = new java.util.ArrayList<>(findings);
        findings.addAll(promotedStage.findings);

        KnowledgeLifecycleStatus overall = broken
                ? KnowledgeLifecycleStatus.BROKEN
                : KnowledgeLifecycleStatus.COMPLETE;

        return new KnowledgeLifecycleDiagnosticResponse(
                proposal.getId(),
                proposal.getType(),
                status,
                overall,
                List.of(
                        new KnowledgeLifecycleStageResponse(
                                VALIDATION_STAGE, validationStage.status, validationStage.artifactId,
                                validationStage.detail),
                        new KnowledgeLifecycleStageResponse(
                                PROMOTED_STAGE, promotedStage.status, promotedSingleId(promoted),
                                promotedStage.detail)
                ),
                findings);
    }

    private ValidationStageResult validationStage(ProposalStatus status, Validation validation) {
        if (validation == null) {
            if (status == ProposalStatus.PROPOSED) {
                return new ValidationStageResult(
                        LineageStageStatus.PENDING, null, null, List.of(), false);
            }
            return new ValidationStageResult(
                    LineageStageStatus.MISSING, null,
                    status + " proposal has no Validation",
                    List.of(status + " proposal has no Validation"),
                    true);
        }

        boolean consistent = (status == ProposalStatus.ACCEPTED
                && validation.getDecision() == ValidationDecision.ACCEPTED)
                || (status == ProposalStatus.REJECTED
                && validation.getDecision() == ValidationDecision.REJECTED);

        if (!consistent) {
            return new ValidationStageResult(
                    LineageStageStatus.INCONSISTENT, validation.getId(), null,
                    List.of(status + " proposal Validation decision is " + validation.getDecision()),
                    true);
        }
        return new ValidationStageResult(
                LineageStageStatus.PRESENT, validation.getId(), null, List.of(), false);
    }

    private PromotedStageResult promotedStage(ProposalStatus status, List<UUID> promoted, String artifact) {
        int count = promoted.size();
        if (status == ProposalStatus.PROPOSED) {
            if (count > 0) {
                String finding = "PROPOSED proposal has promoted " + artifact;
                return new PromotedStageResult(
                        LineageStageStatus.INCONSISTENT, finding, List.of(finding), true);
            }
            return new PromotedStageResult(
                    LineageStageStatus.PENDING, null, List.of(), false);
        }
        if (status == ProposalStatus.REJECTED) {
            if (count == 0) {
                return new PromotedStageResult(
                        LineageStageStatus.NOT_APPLICABLE, "No promotion expected", List.of(), false);
            }
            return new PromotedStageResult(
                    LineageStageStatus.INCONSISTENT, "Rejected proposal has promoted " + artifact,
                    List.of("Rejected proposal has promoted " + artifact),
                    true);
        }
        // ACCEPTED
        if (count == 0) {
            String finding = "An ACCEPTED " + artifact + " proposal MUST produce exactly one trusted "
                    + artifact + ".";
            return new PromotedStageResult(
                    LineageStageStatus.MISSING, finding, List.of(finding), true);
        }
        if (count > 1) {
            return new PromotedStageResult(
                    LineageStageStatus.INCONSISTENT, "ACCEPTED proposal produced multiple " + artifact,
                    List.of("ACCEPTED proposal produced multiple " + artifact),
                    true);
        }
        return new PromotedStageResult(
                LineageStageStatus.PRESENT, null, List.of(), false);
    }

    private String artifactLabel(ProposalType type) {
        return switch (type) {
            case ENGINEERING_DECISION -> "ENGINEERING_DECISION";
            case INSIGHT -> "INSIGHT";
            case ENGINEERING_EVENT -> "ENGINEERING_EVENT";
            default -> type.name();
        };
    }

    private UUID promotedSingleId(List<UUID> promoted) {
        return promoted.size() == 1 ? promoted.getFirst() : null;
    }

    private record ValidationStageResult(
            LineageStageStatus status,
            UUID artifactId,
            String detail,
            List<String> findings,
            boolean broken
    ) {
    }

    private record PromotedStageResult(
            LineageStageStatus status,
            String detail,
            List<String> findings,
            boolean broken
    ) {
    }
}