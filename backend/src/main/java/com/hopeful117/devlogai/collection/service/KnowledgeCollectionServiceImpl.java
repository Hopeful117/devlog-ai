package com.hopeful117.devlogai.collection.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.entity.CollectionWarningEntity;
import com.hopeful117.devlogai.analysis.diagnostics.entity.WarningSeverity;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.diagnostics.repository.CollectionWarningRepository;
import com.hopeful117.devlogai.collection.collector.CollectedFact;
import com.hopeful117.devlogai.collection.collector.CollectionContext;
import com.hopeful117.devlogai.collection.collector.CollectionResult;
import com.hopeful117.devlogai.collection.collector.KnowledgeCollector;
import com.hopeful117.devlogai.collection.collector.NonFatalCollectionException;
import com.hopeful117.devlogai.collection.collector.CollectorRunner;
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
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class KnowledgeCollectionServiceImpl implements KnowledgeCollectionService {

    private final AnalysisRepository analysisRepository;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private final List<KnowledgeCollector> collectors;
    private final CollectorRunner collectorRunner;
    private final ObservationEngine observationEngine;
    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;
    private final CollectionWarningRepository collectionWarningRepository;
    private final AnalysisExecutionDiagnosticRepository diagnosticRepository;

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
                .sorted(Comparator.comparing(KnowledgeCollector::type))
                .toList();

        List<Fact> collectedFacts = new ArrayList<>();
        List<CollectionDiagnostic> warnings = new ArrayList<>();
        List<CollectionWarningEntity> warningEntities = new ArrayList<>();
        Map<String, Object> collectorVersions = new LinkedHashMap<>();
        int collectorCount = 0;
        int successfulCollectors = 0;
        int collectorsWithWarnings = 0;
        int failedCollectors = 0;
        Set<String> fingerprints = new HashSet<>(
                factRepository.findFingerprintsByAnalysisId(analysisId));
        Map<UUID, String> revisions = new LinkedHashMap<>();
        for (Source source : sources) {
            SynchronizedWorkspace workspace = workspaceManager.synchronize(
                    source,
                    analysis.getTargetRevision()
            );
            revisions.put(source.getId(), workspace.resolvedRevision());
            CollectionContext context = new CollectionContext(
                    analysisId,
                    source.getId(),
                    analysis.getProject().getId(),
                    workspace.path(),
                    workspace.resolvedRevision(),
                    source.getType(),
                    Instant.now()
            );
            for (KnowledgeCollector collector : orderedCollectors) {
                if (!collector.supports(context)) continue;
                collectorCount++;
                collectorVersions.put(collector.type().name(), collector.version());
                try {
                    CollectionResult result = collectorRunner.run(collector, context);
                    validateResult(collector, result);
                    result.facts().stream()
                            .filter(fact -> fingerprints.add(fact.fingerprint()))
                            .map(fact -> toEntity(analysis, fact))
                            .forEach(collectedFacts::add);
                    if (result.warnings().isEmpty()) successfulCollectors++;
                    else collectorsWithWarnings++;
                    result.warnings().forEach(warning -> {
                        warnings.add(new CollectionDiagnostic(
                                source.getId(), result.collectorType(), result.collectorVersion(),
                                warning.code(), warning.message()));
                        warningEntities.add(toWarningEntity(
                                analysis, source, result.collectorType(), result.collectorVersion(),
                                warning.code(), warning.message(), WarningSeverity.WARNING));
                    });
                } catch (NonFatalCollectionException failure) {
                    failedCollectors++;
                    warnings.add(new CollectionDiagnostic(
                            source.getId(), collector.type(), collector.version(),
                            failure.code(), failure.getMessage()));
                    warningEntities.add(toWarningEntity(
                            analysis, source, collector.type(), collector.version(),
                            failure.code(), failure.getMessage(), WarningSeverity.ERROR));
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
        collectionWarningRepository.saveAll(warningEntities);

        int errorCount = (int) warningEntities.stream()
                .filter(warning -> warning.getSeverity() == WarningSeverity.ERROR)
                .count();
        boolean truncated = warningEntities.stream()
                .map(CollectionWarningEntity::getCode)
                .anyMatch(code -> code.contains("LIMIT") || code.contains("TRUNCAT"));
        Map<String, Object> resolvedRevisions = revisions.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        diagnosticRepository.save(AnalysisExecutionDiagnostic.builder()
                .analysis(analysis)
                .sourceCount(sources.size())
                .factCount(savedFacts.size())
                .observationCount(observations.size())
                .warningCount(warningEntities.size())
                .errorCount(errorCount)
                .collectorCount(collectorCount)
                .successfulCollectors(successfulCollectors)
                .collectorsWithWarnings(collectorsWithWarnings)
                .failedCollectors(failedCollectors)
                .collectionComplete(errorCount == 0 && !truncated)
                .truncated(truncated)
                .resolvedRevisions(resolvedRevisions)
                .collectorVersions(collectorVersions)
                .collectedAt(Instant.now())
                .build());

        return new KnowledgeCollectionResult(
                sources.size(),
                savedFacts.size(),
                observations.size(),
                revisions,
                warnings
        );
    }

    private CollectionWarningEntity toWarningEntity(
            Analysis analysis,
            Source source,
            com.hopeful117.devlogai.collection.collector.CollectorType collectorType,
            String collectorVersion,
            String code,
            String message,
            WarningSeverity severity
    ) {
        return CollectionWarningEntity.builder()
                .analysis(analysis)
                .source(source)
                .collectorType(collectorType)
                .collectorVersion(collectorVersion)
                .code(code)
                .severity(severity)
                .message(message)
                .metadata(Map.of())
                .occurredAt(Instant.now())
                .build();
    }

    private void validateResult(KnowledgeCollector collector, CollectionResult result) {
        if (result.collectorType() != collector.type()
                || !result.collectorVersion().equals(collector.version())) {
            throw new IllegalStateException("Collector returned inconsistent metadata: " + collector.type());
        }
        result.facts().forEach(fact -> {
            if (!fact.source().equals(result.collectorVersion())) {
                throw new IllegalStateException("Fact source does not identify its Collector version");
            }
            fact.evidenceReferences().forEach(this::validateEvidenceReference);
        });
    }

    private void validateEvidenceReference(String reference) {
        String normalized = reference.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")
                || normalized.equals("..") || normalized.startsWith("../")
                || normalized.contains("/../")) {
            throw new IllegalArgumentException("Fact evidence must be relative to the workspace");
        }
    }

    private Fact toEntity(Analysis analysis, CollectedFact collected) {
        return Fact.builder()
                .analysis(analysis)
                .type(collected.type())
                .content(collected.content())
                .source(collected.source())
                .fingerprint(collected.fingerprint())
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
                        .ruleId(observation.ruleId())
                        .ruleVersion(observation.ruleVersion())
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
