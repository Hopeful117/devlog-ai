package com.hopeful117.devlogai.collection.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.collection.collector.CollectedFact;
import com.hopeful117.devlogai.collection.collector.KnowledgeCollector;
import com.hopeful117.devlogai.collection.observation.DerivedObservation;
import com.hopeful117.devlogai.collection.observation.ObservationEngine;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeCollectionServiceImpl implements KnowledgeCollectionService {

    private final AnalysisRepository analysisRepository;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private final List<KnowledgeCollector> collectors;
    private final ObservationEngine observationEngine;
    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;

    @Override
    @Transactional
    public KnowledgeCollectionResult collect(UUID analysisId) {
        Analysis analysis = analysisRepository.findWithProjectById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", analysisId));
        List<Source> sources = sourceRepository
                .findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(
                        analysis.getProject().getId()
                );
        List<KnowledgeCollector> orderedCollectors = collectors.stream()
                .sorted(Comparator.comparing(KnowledgeCollector::name))
                .toList();

        List<Fact> collectedFacts = new ArrayList<>();
        Map<UUID, String> revisions = new LinkedHashMap<>();
        for (Source source : sources) {
            SynchronizedWorkspace workspace = workspaceManager.synchronize(
                    source,
                    analysis.getTargetRevision()
            );
            revisions.put(source.getId(), workspace.resolvedRevision());
            for (KnowledgeCollector collector : orderedCollectors) {
                if (collector.supports(source.getType())) {
                    collector.collect(source, workspace).stream()
                            .map(fact -> toEntity(analysis, fact))
                            .forEach(collectedFacts::add);
                }
            }
            source.setLastSynchronizedAt(Instant.now());
            sourceRepository.save(source);
        }

        List<Fact> savedFacts = factRepository.saveAll(collectedFacts);
        List<Observation> observations = toObservations(
                analysis,
                savedFacts,
                observationEngine.derive(List.copyOf(savedFacts))
        );
        observationRepository.saveAll(observations);

        return new KnowledgeCollectionResult(
                sources.size(),
                savedFacts.size(),
                observations.size(),
                revisions
        );
    }

    private Fact toEntity(Analysis analysis, CollectedFact collected) {
        return Fact.builder()
                .analysis(analysis)
                .type(collected.type())
                .content(collected.content())
                .source(collected.source())
                .evidenceReferences(new LinkedHashSet<>(collected.evidenceReferences()))
                .build();
    }

    private List<Observation> toObservations(
            Analysis analysis,
            List<Fact> facts,
            List<DerivedObservation> derived
    ) {
        Map<UUID, Fact> factsById = facts.stream()
                .collect(Collectors.toMap(Fact::getId, Function.identity()));
        return derived.stream().map(observation -> Observation.builder()
                        .analysis(analysis)
                        .type(observation.type())
                        .content(observation.content())
                        .supportingFacts(observation.supportingFactIds().stream()
                                .map(id -> requireFact(factsById, id))
                                .collect(Collectors.toCollection(LinkedHashSet::new)))
                        .build())
                .toList();
    }

    private Fact requireFact(Map<UUID, Fact> facts, UUID factId) {
        Fact fact = facts.get(factId);
        if (fact == null) {
            throw new IllegalArgumentException(
                    "Observation references a fact outside the current collection: " + factId
            );
        }
        return fact;
    }
}
