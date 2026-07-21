package com.hopeful117.devlogai.analysis.context;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.artifact.repository.ArtifactRepository;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalysisContextServiceImpl implements AnalysisContextService {

    static final int MAX_FACTS = 100;
    static final int MAX_OBSERVATIONS = 50;
    static final int MAX_RECENT_EVENTS = 20;
    static final int MAX_RELATED_ANALYSES = 10;
    static final int MAX_ARCHITECTURE_ARTIFACTS = 20;
    static final int MAX_ARCHITECTURE_DECISIONS = 20;
    static final int MAX_RECENT_MILESTONES = 10;
    static final int MAX_VALIDATED_PROPOSALS = 20;

    private static final List<ArtifactType> ARCHITECTURE_ARTIFACT_TYPES = List.of(
            ArtifactType.API,
            ArtifactType.CONFIGURATION,
            ArtifactType.DATABASE,
            ArtifactType.INFRASTRUCTURE
    );

    private final AnalysisRepository analysisRepository;
    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;
    private final KnowledgeEventRepository knowledgeEventRepository;
    private final ValidatableProposalRepository proposalRepository;
    private final ArtifactRepository artifactRepository;
    private final DecisionRepository decisionRepository;
    private final MilestoneRepository milestoneRepository;

    @Override
    public AnalysisContext build(UUID analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", analysisId));
        Project project = analysis.getProject();

        List<AnalysisContext.AnalysisSnapshot> relatedAnalyses = List.of();
        List<AnalysisContext.ArtifactSnapshot> architectureArtifacts = List.of();
        List<AnalysisContext.DecisionSnapshot> relatedDecisions = List.of();
        List<AnalysisContext.MilestoneSnapshot> recentMilestones = List.of();

        if (analysis.getType() == AnalysisType.ARCHITECTURE_REVIEW) {
            relatedAnalyses = findRelatedAnalyses(project.getId(), analysis.getId());
            architectureArtifacts = findArchitectureArtifacts(project.getId());
            relatedDecisions = findRelatedDecisions(project.getId());
        }

        if (analysis.getType() == AnalysisType.PROJECT_EVOLUTION) {
            relatedAnalyses = findRelatedAnalyses(project.getId(), analysis.getId());
            recentMilestones = findRecentMilestones(project.getId());
        }

        return new AnalysisContext(
                toProjectSnapshot(project),
                toAnalysisSnapshot(analysis),
                factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                                analysisId, PageRequest.of(0, MAX_FACTS)
                        ).stream()
                        .map(this::toFactSnapshot)
                        .toList(),
                observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(
                                analysisId, PageRequest.of(0, MAX_OBSERVATIONS)
                        ).stream()
                        .map(this::toObservationSnapshot)
                        .toList(),
                knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                                project.getId(), PageRequest.of(0, MAX_RECENT_EVENTS)
                        ).stream()
                        .map(this::toKnowledgeEventSnapshot)
                        .toList(),
                relatedAnalyses,
                architectureArtifacts,
                relatedDecisions,
                recentMilestones,
                proposalRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
                                project.getId(), ProposalStatus.ACCEPTED,
                                PageRequest.of(0, MAX_VALIDATED_PROPOSALS)
                        ).stream()
                        .map(this::toValidatedProposalSnapshot)
                        .toList()
        );
    }

    private List<AnalysisContext.AnalysisSnapshot> findRelatedAnalyses(
            UUID projectId,
            UUID analysisId
    ) {
        return analysisRepository.findByProjectIdAndIdNotOrderByCreatedAtDescIdDesc(
                        projectId, analysisId,
                        PageRequest.of(0, MAX_RELATED_ANALYSES)
                ).stream()
                .map(this::toAnalysisSnapshot)
                .toList();
    }

    private List<AnalysisContext.ArtifactSnapshot> findArchitectureArtifacts(UUID projectId) {
        return artifactRepository.findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
                        projectId, ARCHITECTURE_ARTIFACT_TYPES,
                        PageRequest.of(0, MAX_ARCHITECTURE_ARTIFACTS)
                ).stream()
                .map(this::toArtifactSnapshot)
                .toList();
    }

    private List<AnalysisContext.DecisionSnapshot> findRelatedDecisions(UUID projectId) {
        return decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(
                        projectId, PageRequest.of(0, MAX_ARCHITECTURE_DECISIONS)
                ).stream()
                .map(this::toDecisionSnapshot)
                .toList();
    }

    private List<AnalysisContext.MilestoneSnapshot> findRecentMilestones(UUID projectId) {
        return milestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc(
                        projectId, PageRequest.of(0, MAX_RECENT_MILESTONES)
                ).stream()
                .map(this::toMilestoneSnapshot)
                .toList();
    }

    private AnalysisContext.ProjectSnapshot toProjectSnapshot(Project project) {
        return new AnalysisContext.ProjectSnapshot(
                project.getId(), project.getName(), project.getSlug(),
                project.getDescription(), project.getStatus()
        );
    }

    private AnalysisContext.AnalysisSnapshot toAnalysisSnapshot(Analysis analysis) {
        return new AnalysisContext.AnalysisSnapshot(
                analysis.getId(), analysis.getType(), analysis.getStatus(),
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
                observation.getSupportingFacts().stream()
                        .map(Fact::getId)
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList(),
                observation.getCreatedAt()
        );
    }

    private AnalysisContext.KnowledgeEventSnapshot toKnowledgeEventSnapshot(KnowledgeEvent event) {
        return new AnalysisContext.KnowledgeEventSnapshot(
                event.getId(), event.getType(), event.getTitle(), event.getDescription(),
                event.getCreatedAt()
        );
    }

    private AnalysisContext.ArtifactSnapshot toArtifactSnapshot(Artifact artifact) {
        return new AnalysisContext.ArtifactSnapshot(
                artifact.getId(), artifact.getType(), artifact.getName(), artifact.getPath(),
                artifact.getDescription(), artifact.getCreatedAt()
        );
    }

    private AnalysisContext.DecisionSnapshot toDecisionSnapshot(Decision decision) {
        return new AnalysisContext.DecisionSnapshot(
                decision.getId(), decision.getTitle(), decision.getContext(),
                decision.getChoice(), decision.getRationale(), decision.getConsequences(),
                decision.getCreatedAt()
        );
    }

    private AnalysisContext.MilestoneSnapshot toMilestoneSnapshot(Milestone milestone) {
        return new AnalysisContext.MilestoneSnapshot(
                milestone.getId(), milestone.getName(), milestone.getDescription(),
                milestone.getStatus(), milestone.getStartedAt(), milestone.getCompletedAt()
        );
    }

    private AnalysisContext.ValidatedProposalSnapshot toValidatedProposalSnapshot(
            ValidatableProposal proposal
    ) {
        return new AnalysisContext.ValidatedProposalSnapshot(
                proposal.getId(), proposal.getType(), proposal.getPayload().toString(),
                proposal.getCreatedAt(), proposal.getDecidedAt()
        );
    }
}
