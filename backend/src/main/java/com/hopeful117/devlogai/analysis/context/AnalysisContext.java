package com.hopeful117.devlogai.analysis.context;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisContext(
        ProjectSnapshot project,
        AnalysisSnapshot analysis,
        List<FactSnapshot> facts,
        List<ObservationSnapshot> observations,
        List<KnowledgeEventSnapshot> recentKnowledgeEvents,
        List<AnalysisSnapshot> relatedAnalyses,
        List<ArtifactSnapshot> architectureArtifacts,
        List<DecisionSnapshot> relatedDecisions,
        List<MilestoneSnapshot> recentMilestones,
        List<ValidatedProposalSnapshot> validatedProposals
) {
    public AnalysisContext {
        facts = List.copyOf(facts);
        observations = List.copyOf(observations);
        recentKnowledgeEvents = List.copyOf(recentKnowledgeEvents);
        relatedAnalyses = List.copyOf(relatedAnalyses);
        architectureArtifacts = List.copyOf(architectureArtifacts);
        relatedDecisions = List.copyOf(relatedDecisions);
        recentMilestones = List.copyOf(recentMilestones);
        validatedProposals = List.copyOf(validatedProposals);
    }

    public record ProjectSnapshot(
            UUID id,
            String name,
            String slug,
            String description,
            ProjectStatus status
    ) {
    }

    public record AnalysisSnapshot(
            UUID id,
            AnalysisType type,
            AnalysisStatus status,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt
    ) {
    }

    public record FactSnapshot(
            UUID id,
            FactType type,
            String content,
            String source,
            List<String> evidenceReferences,
            Instant detectedAt
    ) {
        public FactSnapshot {
            evidenceReferences = List.copyOf(evidenceReferences);
        }
    }

    public record ObservationSnapshot(
            UUID id,
            ObservationType type,
            String content,
            List<UUID> supportingFactIds,
            Instant createdAt
    ) {
        public ObservationSnapshot {
            supportingFactIds = List.copyOf(supportingFactIds);
        }
    }

    public record KnowledgeEventSnapshot(
            UUID id,
            KnowledgeEventType type,
            String title,
            String description,
            Instant createdAt
    ) {
    }

    public record ArtifactSnapshot(
            UUID id,
            ArtifactType type,
            String name,
            String path,
            String description,
            Instant createdAt
    ) {
    }

    public record DecisionSnapshot(
            UUID id,
            String title,
            String context,
            String choice,
            String rationale,
            String consequences,
            Instant createdAt
    ) {
    }

    public record MilestoneSnapshot(
            UUID id,
            String name,
            String description,
            MilestoneStatus status,
            Instant startedAt,
            Instant completedAt
    ) {
    }

    public record ValidatedProposalSnapshot(
            UUID id,
            ProposalType type,
            String payload,
            Instant createdAt,
            Instant decidedAt
    ) {
    }
}
