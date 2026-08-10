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
import java.util.List;
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

        List<AnalysisContext.FactSnapshot> facts = factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                        analysisId, PageRequest.of(0, MAX_FACTS)
                ).stream()
                .map(this::toFactSnapshot)
                .toList();

        List<AnalysisContext.ObservationSnapshot> observations = observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(
                        analysisId, PageRequest.of(0, MAX_OBSERVATIONS)
                ).stream()
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
                projectContext.engineeringStories()
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
}
