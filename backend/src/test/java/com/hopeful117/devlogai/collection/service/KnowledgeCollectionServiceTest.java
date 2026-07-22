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
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeCollectionServiceTest {

    @TempDir Path workspacePath;

    @Mock private AnalysisRepository analysisRepository;
    @Mock private SourceRepository sourceRepository;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private KnowledgeCollector collector;
    @Mock private com.hopeful117.devlogai.collection.collector.CollectorRunner collectorRunner;
    @Mock private ObservationEngine observationEngine;
    @Mock private FactRepository factRepository;
    @Mock private ObservationRepository observationRepository;

    private KnowledgeCollectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeCollectionServiceImpl(
                analysisRepository,
                sourceRepository,
                workspaceManager,
                List.of(collector),
                collectorRunner,
                observationEngine,
                factRepository,
                observationRepository
        );
    }

    @Test
    void shouldCollectFactsThenDeriveObservationsForAnalysisProject() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        Analysis analysis = Analysis.builder()
                .id(analysisId)
                .project(Project.builder().id(projectId).build())
                .targetRevision("release-1")
                .build();
        Source source = Source.builder()
                .id(sourceId)
                .project(analysis.getProject())
                .type(SourceType.GIT_REPOSITORY)
                .active(true)
                .build();
        SynchronizedWorkspace workspace = new SynchronizedWorkspace(
                sourceId, workspacePath, "abc123"
        );
        CollectedFact collected = CollectedFact.create(
                "git-collector-v1", FactType.COMMIT, "revision=abc123",
                List.of("git:abc123"), "abc123");
        when(analysisRepository.findWithProjectById(analysisId)).thenReturn(Optional.of(analysis));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of(source));
        when(collector.type()).thenReturn(com.hopeful117.devlogai.collection.collector.CollectorType.GIT);
        when(collector.version()).thenReturn("git-collector-v1");
        when(collector.supports(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(workspaceManager.synchronize(source, "release-1")).thenReturn(workspace);
        when(collectorRunner.run(org.mockito.ArgumentMatchers.eq(collector),
                org.mockito.ArgumentMatchers.any())).thenReturn(
                com.hopeful117.devlogai.collection.collector.CollectionResult.of(
                        com.hopeful117.devlogai.collection.collector.CollectorType.GIT,
                        "git-collector-v1", List.of(collected), List.of()));
        when(factRepository.findFingerprintsByAnalysisId(analysisId)).thenReturn(Set.of());
        when(factRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Fact> facts = invocation.getArgument(0);
            facts.getFirst().setId(factId);
            return facts;
        });
        when(observationEngine.derive(anyList())).thenReturn(List.of(
                new DerivedObservation(
                        ObservationType.OTHER,
                        "Derived deterministically",
                        Set.of(factId)
                )
        ));

        KnowledgeCollectionResult result = service.collect(analysisId);

        assertEquals(1, result.sourceCount());
        assertEquals(1, result.factCount());
        assertEquals(1, result.observationCount());
        assertEquals("abc123", result.resolvedRevisions().get(sourceId));
        assertNotNull(source.getLastSynchronizedAt());

        ArgumentCaptor<List<Fact>> facts = ArgumentCaptor.forClass(List.class);
        verify(factRepository).saveAll(facts.capture());
        assertEquals(analysis, facts.getValue().getFirst().getAnalysis());
        assertEquals(FactType.COMMIT, facts.getValue().getFirst().getType());

        ArgumentCaptor<List<Observation>> observations = ArgumentCaptor.forClass(List.class);
        verify(observationRepository).saveAll(observations.capture());
        assertEquals(analysis, observations.getValue().getFirst().getAnalysis());
        assertEquals(factId, observations.getValue().getFirst()
                .getSupportingFacts().iterator().next().getId());
    }

    @Test
    void shouldNotTouchAnotherProjectOrInactiveSources() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Analysis analysis = Analysis.builder()
                .id(analysisId)
                .project(Project.builder().id(projectId).build())
                .build();
        when(analysisRepository.findWithProjectById(analysisId)).thenReturn(Optional.of(analysis));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of());
        when(factRepository.saveAll(List.of())).thenReturn(List.of());
        when(observationEngine.derive(List.of())).thenReturn(List.of());

        KnowledgeCollectionResult result = service.collect(analysisId);

        assertEquals(0, result.sourceCount());
        assertEquals(0, result.factCount());
        verify(workspaceManager, never()).synchronize(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(collectorRunner, never()).run(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectObservationReferencingFactOutsideCurrentCollection() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Analysis analysis = Analysis.builder()
                .id(analysisId)
                .project(Project.builder().id(projectId).build())
                .build();
        when(analysisRepository.findWithProjectById(analysisId)).thenReturn(Optional.of(analysis));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of());
        when(factRepository.saveAll(List.of())).thenReturn(List.of());
        when(observationEngine.derive(List.of())).thenReturn(List.of(
                new DerivedObservation(ObservationType.OTHER, "invalid", Set.of(UUID.randomUUID()))
        ));

        assertThrows(IllegalArgumentException.class, () -> service.collect(analysisId));
        verify(observationRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldNotPersistAnExistingFingerprintAgain() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Analysis analysis = Analysis.builder()
                .id(analysisId)
                .project(Project.builder().id(projectId).build())
                .build();
        Source source = Source.builder()
                .id(sourceId).project(analysis.getProject())
                .type(SourceType.GIT_REPOSITORY).active(true).build();
        CollectedFact fact = CollectedFact.create(
                "git-collector-v1", FactType.COMMIT, "revision=abc123",
                List.of("git:abc123"), "abc123");
        when(analysisRepository.findWithProjectById(analysisId)).thenReturn(Optional.of(analysis));
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of(source));
        when(workspaceManager.synchronize(source, null)).thenReturn(
                new SynchronizedWorkspace(sourceId, workspacePath, "abc123"));
        when(collector.type()).thenReturn(
                com.hopeful117.devlogai.collection.collector.CollectorType.GIT);
        when(collector.version()).thenReturn("git-collector-v1");
        when(collector.supports(any())).thenReturn(true);
        when(collectorRunner.run(org.mockito.ArgumentMatchers.eq(collector), any())).thenReturn(
                com.hopeful117.devlogai.collection.collector.CollectionResult.of(
                        com.hopeful117.devlogai.collection.collector.CollectorType.GIT,
                        "git-collector-v1", List.of(fact), List.of()));
        when(factRepository.findFingerprintsByAnalysisId(analysisId))
                .thenReturn(Set.of(fact.fingerprint()));
        when(factRepository.saveAll(List.of())).thenReturn(List.of());
        when(observationEngine.derive(List.of())).thenReturn(List.of());

        KnowledgeCollectionResult result = service.collect(analysisId);

        assertEquals(0, result.factCount());
        verify(factRepository).saveAll(List.of());
    }

    @Test
    void shouldFailWhenAnalysisDoesNotExist() {
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findWithProjectById(analysisId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.collect(analysisId));
        verify(sourceRepository, never())
                .findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(org.mockito.ArgumentMatchers.any());
    }
}
