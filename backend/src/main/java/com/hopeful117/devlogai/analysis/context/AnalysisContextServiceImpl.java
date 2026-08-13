package com.hopeful117.devlogai.analysis.context;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.projectcontext.ProjectContextProvider;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.hopeful117.devlogai.engineeringevent.AnalysisEvolutionScopeRepository;
import com.hopeful117.devlogai.history.service.ProjectHistoryService;

@Service
@RequiredArgsConstructor
public class AnalysisContextServiceImpl implements AnalysisContextService {

    static final int MAX_FACTS = 100;
    static final int MAX_OBSERVATIONS = 50;

    private final AnalysisRepository analysisRepository;
    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;
    private final ProjectProfileService projectProfileService;
    private final ProjectContextProvider projectContextProvider;
    private final AnalysisEvolutionScopeRepository evolutionScopes;
    private final ProjectHistoryService historyService;

    @Override
    public AnalysisContext build(UUID analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", analysisId));
        UUID projectId = analysis.getProject().getId();

        ProjectContextSnapshot projectContext = projectContextProvider.build(projectId);

        List<Observation> observationCandidates = observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(
                        analysisId, PageRequest.of(0, MAX_OBSERVATIONS)
                );
        List<Fact> rankedFacts = factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                        analysisId, PageRequest.of(0, MAX_FACTS)
                );
        ContextSlice contextSlice = buildClosureSafeContextSlice(analysisId, observationCandidates, rankedFacts);
        List<AnalysisContext.FactSnapshot> facts = contextSlice.facts().stream()
                .map(this::toFactSnapshot)
                .toList();
        List<AnalysisContext.ObservationSnapshot> observations = contextSlice.observations().stream()
                .map(this::toObservationSnapshot)
                .toList();

        List<AnalysisContext.AnalysisSnapshot> relatedAnalyses = List.of();
        List<AnalysisContext.ArtifactSnapshot> architectureArtifacts = List.of();
        List<AnalysisContext.DecisionSnapshot> relatedDecisions = List.of();
        List<AnalysisContext.MilestoneSnapshot> recentMilestones = List.of();

        if (analysis.getType() == AnalysisType.ARCHITECTURE_REVIEW) {
            relatedAnalyses = filterRelatedAnalyses(projectContext.recentAnalyses(), analysisId);
            architectureArtifacts = projectContext.architectureArtifacts();
            relatedDecisions = projectContext.relatedDecisions();
        }

        if (analysis.getType() == AnalysisType.PROJECT_EVOLUTION) {
            relatedAnalyses = filterRelatedAnalyses(projectContext.recentAnalyses(), analysisId);
            recentMilestones = projectContext.recentMilestones();
        }

        AnalysisContext.EvolutionContext evolution = evolutionScopes.findById(analysisId)
                .map(scope -> new AnalysisContext.EvolutionContext(scope.getContextVersion(),
                        scope.getProject().getId(), scope.getSource().getId(), scope.getBaseCommit(),
                        scope.getTargetCommit(), scope.getComparisonPolicy().name(), scope.isMergeCommit(),
                        scope.getTargetCommittedAt(), historyService.getCommitContext(
                                scope.getSource().getId(), scope.getTargetCommit())))
                .orElse(null);
        return new AnalysisContext(
                projectContext.project(),
                toAnalysisSnapshot(analysis),
                projectProfileService.getByAnalysis(analysisId),
                facts,
                observations,
                projectContext.recentKnowledgeEvents(),
                relatedAnalyses,
                architectureArtifacts,
                relatedDecisions,
                recentMilestones,
                projectContext.validatedProposals(), evolution,
                projectContext.validatedEngineeringEvents(),
                projectContext.openChallenges(),
                projectContext.knowledgeRelations(),
                projectContext.engineeringStories(),
                projectContext.humanContextInputs()
        );
    }

    private List<AnalysisContext.AnalysisSnapshot> filterRelatedAnalyses(
            List<AnalysisContext.AnalysisSnapshot> recentAnalyses,
            UUID excludeAnalysisId
    ) {
        return recentAnalyses.stream()
                .filter(a -> !a.id().equals(excludeAnalysisId))
                .toList();
    }

    private AnalysisContext.AnalysisSnapshot toAnalysisSnapshot(Analysis analysis) {
        return new AnalysisContext.AnalysisSnapshot(
                analysis.getId(), analysis.getType(),
                analysis.getIntentId(), analysis.getIntentVersion(), analysis.getStatus(),
                analysis.getStartedAt(), analysis.getCompletedAt(), analysis.getCreatedAt()
        );
    }

    private AnalysisContext.FactSnapshot toFactSnapshot(Fact fact) {
        return new AnalysisContext.FactSnapshot(
                fact.getId(), fact.getType(), fact.getContent(), fact.getSource(),
                fact.getEvidenceReferences().stream().sorted().toList(), fact.getDetectedAt()
        );
    }

    private AnalysisContext.ObservationSnapshot toObservationSnapshot(Observation observation) {
        return new AnalysisContext.ObservationSnapshot(
                observation.getId(), observation.getType(), observation.getContent(),
                observation.getRuleId(), observation.getRuleVersion(),
                observation.getSupportingFacts().stream()
                        .map(Fact::getId)
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList(),
                observation.getCreatedAt()
        );
    }

    private ContextSlice buildClosureSafeContextSlice(
            UUID analysisId,
            List<Observation> observationCandidates,
            List<Fact> rankedFacts
    ) {
        Map<UUID, Fact> rankedFactsById = new LinkedHashMap<>();
        for (Fact fact : rankedFacts) {
            rankedFactsById.putIfAbsent(fact.getId(), fact);
        }

        List<Observation> observations = new java.util.ArrayList<>(observationCandidates);
        while (!observations.isEmpty()
                && requiredSupportingFactIds(observations).size() > MAX_FACTS) {
            observations.removeLast();
        }

        LinkedHashSet<UUID> requiredFactIds = requiredSupportingFactIds(observations);
        Map<UUID, Fact> requiredFactsById = new LinkedHashMap<>(rankedFactsById);
        if (!requiredFactIds.isEmpty() && !requiredFactsById.keySet().containsAll(requiredFactIds)) {
            factRepository.findByAnalysisIdAndIdIn(analysisId, requiredFactIds).stream()
                    .filter(Objects::nonNull)
                    .forEach(fact -> requiredFactsById.putIfAbsent(fact.getId(), fact));
        }

        List<Fact> requiredFacts = requiredFactIds.stream()
                .map(requiredFactsById::get)
                .filter(Objects::nonNull)
                .sorted(factOrder())
                .toList();

        Set<UUID> retainedFactIds = new LinkedHashSet<>(requiredFactIds);
        List<Fact> facts = new java.util.ArrayList<>(requiredFacts);
        for (Fact fact : rankedFacts) {
            if (facts.size() >= MAX_FACTS) break;
            if (retainedFactIds.add(fact.getId())) {
                facts.add(fact);
            }
        }
        return new ContextSlice(List.copyOf(facts), List.copyOf(observations));
    }

    private LinkedHashSet<UUID> requiredSupportingFactIds(List<Observation> observations) {
        LinkedHashSet<UUID> requiredFactIds = new LinkedHashSet<>();
        for (Observation observation : observations) {
            observation.getSupportingFacts().stream()
                    .map(Fact::getId)
                    .sorted(Comparator.comparing(UUID::toString))
                    .forEach(requiredFactIds::add);
        }
        return requiredFactIds;
    }

    private Comparator<Fact> factOrder() {
        return Comparator.comparing(Fact::getDetectedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Fact::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private record ContextSlice(
            List<Fact> facts,
            List<Observation> observations) { }
}
