package com.hopeful117.devlogai.proposal.review;

import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.exception.InvalidParameterException;
import com.hopeful117.devlogai.validation.entity.Validation;
import com.hopeful117.devlogai.validation.repository.ValidationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProposalReviewService {
    private final AnalysisRepository analyses;
    private final ValidatableProposalRepository proposals;
    private final FactRepository facts;
    private final ObservationRepository observations;
    private final ValidationRepository validations;
    private final InsightRepository insights;
    private final EngineeringEventRepository engineeringEvents;
    private final ProposalReviewPolicy policy;

    public ProposalReviewResponse get(UUID analysisId, int pageNumber, Integer requestedSize) {
        if (pageNumber < 0) throw new InvalidParameterException("page", pageNumber);
        int size = policy.effectiveSize(requestedSize);
        var analysis = analyses.findWithProjectById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", analysisId));
        var proposalPage = proposals.findReviewPage(analysisId, PageRequest.of(pageNumber, size));
        List<ValidatableProposal> values = proposalPage.getContent();
        Set<UUID> proposalIds = values.stream().map(ValidatableProposal::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> factIds = boundedIds(values, ValidatableProposal::getSupportingFactIds,
                policy.getMaxFactsPerProposal());
        Set<UUID> observationIds = boundedIds(values,
                ValidatableProposal::getSupportingObservationIds,
                policy.getMaxObservationsPerProposal());
        Map<UUID, Fact> factMap = mapById(factIds.isEmpty() ? List.of()
                : facts.findByAnalysisIdAndIdIn(analysisId, factIds), Fact::getId);
        Map<UUID, Observation> observationMap = mapById(observationIds.isEmpty() ? List.of()
                : observations.findByAnalysisIdAndIdIn(analysisId, observationIds), Observation::getId);
        Map<UUID, Validation> validationMap = (proposalIds.isEmpty() ? List.<Validation>of()
                : validations.findByProposalIdIn(proposalIds)).stream()
                .collect(Collectors.toMap(v -> v.getProposal().getId(), Function.identity()));
        Map<UUID, Insight> insightMap = (proposalIds.isEmpty() ? List.<Insight>of()
                : insights.findByProposalIdIn(proposalIds)).stream()
                .collect(Collectors.toMap(i -> i.getProposal().getId(), Function.identity()));
        Map<UUID, EngineeringEvent> eventMap = (proposalIds.isEmpty() ? List.<EngineeringEvent>of()
                : engineeringEvents.findByProposalIdIn(proposalIds)).stream()
                .collect(Collectors.toMap(i -> i.getProposal().getId(), Function.identity()));
        List<ProposalReviewResponse.Item> items = values.stream()
                .map(value -> item(value, factMap, observationMap,
                        validationMap.get(value.getId()), insightMap.get(value.getId()),
                        eventMap.get(value.getId()))).toList();
        var counts = new ProposalReviewResponse.Counts(
                proposals.countByAnalysisId(analysisId),
                proposals.countByAnalysisIdAndStatus(analysisId, ProposalStatus.PROPOSED),
                proposals.countByAnalysisIdAndStatus(analysisId, ProposalStatus.ACCEPTED),
                proposals.countByAnalysisIdAndStatus(analysisId, ProposalStatus.REJECTED));
        var page = new ProposalReviewResponse.Page(pageNumber, size,
                proposalPage.getTotalPages(), proposalPage.hasPrevious(), proposalPage.hasNext());
        return new ProposalReviewResponse(ProposalReviewResponse.PROJECTION_VERSION, analysisId,
                analysis.getProject().getId(), counts, page, items);
    }

    private ProposalReviewResponse.Item item(ValidatableProposal p, Map<UUID, Fact> factMap,
            Map<UUID, Observation> observationMap, Validation validation, Insight insight,
            EngineeringEvent engineeringEvent) {
        List<String> references = safe(p.getEvidenceReferences()).stream()
                .limit(policy.getMaxEvidenceReferencesPerProposal()).toList();
        return new ProposalReviewResponse.Item(p.getId(), p.getProject().getId(),
                p.getAnalysis().getId(), p.getSourceIndex(), p.getType(), p.getStatus(),
                p.getPayload(), p.getConfidence(), references,
                evidence(safe(p.getSupportingFactIds()), factMap, true,
                        policy.getMaxFactsPerProposal()),
                evidence(safe(p.getSupportingObservationIds()), observationMap, false,
                        policy.getMaxObservationsPerProposal()),
                decision(validation), resultingInsight(insight), resultingEvent(engineeringEvent),
                p.getCreatedAt(), p.getDecidedAt());
    }

    private List<ProposalReviewResponse.Evidence> evidence(List<UUID> ids, Map<?, ?> values,
            boolean fact, int maximum) {
        return ids.stream().distinct().limit(maximum).map(id -> {
            Object value = values.get(id);
            if (value == null) return new ProposalReviewResponse.Evidence(
                    id, "MISSING", null, null, null);
            if (fact) {
                Fact f = (Fact) value;
                return new ProposalReviewResponse.Evidence(id, "AVAILABLE", f.getType().name(),
                        truncate(f.getContent()), f.getSource());
            }
            Observation o = (Observation) value;
            return new ProposalReviewResponse.Evidence(id, "AVAILABLE", o.getType().name(),
                    truncate(o.getContent()), o.getRuleId() + "/" + o.getRuleVersion());
        }).toList();
    }

    private ProposalReviewResponse.Decision decision(Validation value) {
        return value == null ? null : new ProposalReviewResponse.Decision(value.getId(),
                value.getDecision().name(), value.getValidatedBy(), value.getComment(),
                value.getValidatedAt());
    }

    private ProposalReviewResponse.ResultingInsight resultingInsight(Insight value) {
        return value == null ? null : new ProposalReviewResponse.ResultingInsight(value.getId(),
                value.getType().name(), value.getSeverity().name(), value.getTitle());
    }

    private ProposalReviewResponse.ResultingEngineeringEvent resultingEvent(EngineeringEvent value) {
        return value == null ? null : new ProposalReviewResponse.ResultingEngineeringEvent(
                value.getId(), value.getCategory().name(), value.getTitle(),
                value.getBaseCommit(), value.getTargetCommit());
    }

    private String truncate(String value) {
        if (value == null) return null;
        int maximum = policy.getMaxContentCharacters();
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maximum) return value;
        return value.substring(0, value.offsetByCodePoints(0, maximum)) + "…";
    }

    private Set<UUID> boundedIds(List<ValidatableProposal> values,
            Function<ValidatableProposal, List<UUID>> getter, int maximum) {
        Set<UUID> result = new LinkedHashSet<>();
        values.forEach(value -> safe(getter.apply(value)).stream().limit(maximum).forEach(result::add));
        return result;
    }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private <T> Map<UUID, T> mapById(List<T> values, Function<T, UUID> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
