package com.hopeful117.devlogai.storycontextanalysis.history;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoricalKnowledgeCandidateService {

    static final int MAX_HISTORICAL_ANALYSES = 10;
    static final int MAX_SCANNED_FACTS = 200;
    static final int MAX_HISTORICAL_FACTS = 100;
    static final int MAX_HISTORICAL_OBSERVATIONS = 50;

    private final AnalysisRepository analysisRepository;
    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;

    @Transactional(readOnly = true)
    public HistoricalKnowledgeCandidates retrieve(
            UUID projectId,
            UUID baselineAnalysisId,
            EngineeringStory story,
            List<String> requestedFiles,
            EngineeringContext engineeringContext
    ) {
        Set<String> anchors = provenanceAnchors(story, requestedFiles, engineeringContext);
        if (anchors.isEmpty()) {
            return HistoricalKnowledgeCandidates.empty();
        }

        List<UUID> analysisIds = analysisRepository.findHistoricalCandidates(
                        projectId,
                        baselineAnalysisId,
                        AnalysisStatus.COMPLETED,
                        PageRequest.of(0, MAX_HISTORICAL_ANALYSES)
                ).stream()
                .map(analysis -> analysis.getId())
                .filter(Objects::nonNull)
                .toList();
        if (analysisIds.isEmpty()) {
            return HistoricalKnowledgeCandidates.empty();
        }

        LinkedHashMap<String, Fact> retainedFactsBySemanticKey = new LinkedHashMap<>();
        for (Fact fact : factRepository.findHistoricalCandidates(
                analysisIds, PageRequest.of(0, MAX_SCANNED_FACTS))) {
            if (fact == null || fact.getId() == null || !matches(fact, anchors)) {
                continue;
            }
            String key = factSemanticKey(fact);
            retainedFactsBySemanticKey.putIfAbsent(key, fact);
            if (retainedFactsBySemanticKey.size() == MAX_HISTORICAL_FACTS) {
                break;
            }
        }
        if (retainedFactsBySemanticKey.isEmpty()) {
            return HistoricalKnowledgeCandidates.empty();
        }

        Set<UUID> retainedFactIds = retainedFactsBySemanticKey.values().stream()
                .map(Fact::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> historicalAnalysisIds = Set.copyOf(analysisIds);
        LinkedHashMap<UUID, Observation> retainedObservations = new LinkedHashMap<>();
        for (Observation observation : observationRepository.findHistoricalCandidates(
                analysisIds,
                retainedFactIds,
                PageRequest.of(0, MAX_HISTORICAL_OBSERVATIONS))) {
            if (isCompatible(observation, historicalAnalysisIds, retainedFactIds)) {
                retainedObservations.putIfAbsent(observation.getId(), observation);
            }
        }

        return new HistoricalKnowledgeCandidates(
                retainedFactsBySemanticKey.values().stream().map(this::toFactSnapshot).toList(),
                retainedObservations.values().stream().map(this::toObservationSnapshot).toList()
        );
    }

    private Set<String> provenanceAnchors(
            EngineeringStory story,
            List<String> requestedFiles,
            EngineeringContext engineeringContext
    ) {
        LinkedHashSet<String> anchors = new LinkedHashSet<>();
        addAnchor(anchors, story.getStoryPath());
        addCommitAnchor(anchors, story.getBaseCommit());
        addCommitAnchor(anchors, story.getTargetCommit());
        if (requestedFiles != null) {
            requestedFiles.forEach(file -> addAnchor(anchors, file));
        }
        if (engineeringContext != null && engineeringContext.evidence() != null) {
            engineeringContext.evidence().forEach(evidence -> {
                addAnchor(anchors, evidence.reference());
                addAnchor(anchors, evidence.originatingFile());
            });
        }
        return Set.copyOf(anchors);
    }

    private void addCommitAnchor(Set<String> anchors, String commit) {
        if (commit != null && !commit.isBlank()) {
            anchors.add("commit:" + commit);
            anchors.add("git:" + commit);
        }
    }

    private void addAnchor(Set<String> anchors, String reference) {
        if (reference != null && !reference.isBlank()) {
            anchors.add(reference);
            if (reference.startsWith("git:")) {
                anchors.add(reference.substring(4));
            }
            if (reference.startsWith("commit:")) {
                anchors.add(reference.substring(7));
            }
        }
    }

    private boolean matches(Fact fact, Set<String> anchors) {
        Collection<String> references = fact.getEvidenceReferences();
        return references != null && references.stream().anyMatch(anchors::contains);
    }

    private String factSemanticKey(Fact fact) {
        return fact.getType() + "|" + fact.getSource() + "|" + fact.getContent() + "|"
                + fact.getEvidenceReferences().stream().sorted().toList();
    }

    private boolean isCompatible(
            Observation observation,
            Set<UUID> historicalAnalysisIds,
            Set<UUID> retainedFactIds
    ) {
        if (observation == null || observation.getId() == null
                || observation.getAnalysis() == null
                || !historicalAnalysisIds.contains(observation.getAnalysis().getId())
                || observation.getSupportingFacts() == null
                || observation.getSupportingFacts().isEmpty()) {
            return false;
        }
        UUID observationAnalysisId = observation.getAnalysis().getId();
        return observation.getSupportingFacts().stream().allMatch(fact ->
                fact != null
                        && fact.getId() != null
                        && retainedFactIds.contains(fact.getId())
                        && fact.getAnalysis() != null
                        && observationAnalysisId.equals(fact.getAnalysis().getId()));
    }

    private AnalysisContext.FactSnapshot toFactSnapshot(Fact fact) {
        return new AnalysisContext.FactSnapshot(
                fact.getId(),
                fact.getType(),
                fact.getContent(),
                fact.getSource(),
                fact.getEvidenceReferences().stream().sorted().toList(),
                fact.getDetectedAt()
        );
    }

    private AnalysisContext.ObservationSnapshot toObservationSnapshot(Observation observation) {
        return new AnalysisContext.ObservationSnapshot(
                observation.getId(),
                observation.getType(),
                observation.getContent(),
                observation.getRuleId(),
                observation.getRuleVersion(),
                observation.getSupportingFacts().stream()
                        .map(Fact::getId)
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList(),
                observation.getCreatedAt()
        );
    }

    public record HistoricalKnowledgeCandidates(
            List<AnalysisContext.FactSnapshot> facts,
            List<AnalysisContext.ObservationSnapshot> observations
    ) {
        public HistoricalKnowledgeCandidates {
            facts = List.copyOf(facts);
            observations = List.copyOf(observations);
        }

        public static HistoricalKnowledgeCandidates empty() {
            return new HistoricalKnowledgeCandidates(List.of(), List.of());
        }
    }
}
