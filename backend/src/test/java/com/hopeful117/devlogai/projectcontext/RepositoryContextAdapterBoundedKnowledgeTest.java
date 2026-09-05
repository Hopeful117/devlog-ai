package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryContextAdapterBoundedKnowledgeTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID BASELINE_ANALYSIS_ID = UUID.randomUUID();

    @Mock FactRepository factRepository;
    @Mock ObservationRepository observationRepository;
    @Mock ProjectContextProvider projectContextProvider;
    @Mock RepositoryContextService repositoryContextService;
    @Mock ProjectCommitRepository commitRepository;
    @Mock ProjectFreshnessService freshnessService;

    private RepositoryContextAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RepositoryContextAdapter(projectContextProvider,
                repositoryContextService, null, factRepository, observationRepository,
                commitRepository, freshnessService);
    }

    private Fact fact(String idSeed, String content, Instant detectedAt) {
        return Fact.builder().id(UUID.nameUUIDFromBytes(idSeed.getBytes()))
                .type(com.hopeful117.devlogai.fact.entity.FactType.TECHNOLOGY)
                .content(content).source("collector")
                .evidenceReferences(new java.util.LinkedHashSet<>(List.of(
                        "git:src:" + idSeed)))
                .detectedAt(detectedAt).build();
    }

    private ProjectContextSnapshot snapshotWithBaseline() {
        var profile = new ProjectProfileResponse(UUID.randomUUID(), PROJECT_ID,
                BASELINE_ANALYSIS_ID, "v1", "rv", Instant.now(), null,
                java.util.Map.of(), new com.hopeful117.devlogai.profile.dto.ProjectProfileResponse.Completeness(
                        com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus.COMPLETE,
                        true, false, 0, 0, 5, 0, 0),
                List.of(), null, List.of(), 3);
        return new ProjectContextSnapshot(null, profile,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    @org.junit.jupiter.api.Test
    void selectsOnlyIntentRelevantFactsWithinTheBoundedWindow() {
        var matching = fact("matching", "repository synchronization checkpoints",
                Instant.parse("2026-08-20T00:00:00Z"));
        var other = fact("other", "unrelated database migration detail",
                Instant.parse("2026-08-21T00:00:00Z"));
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                eq(BASELINE_ANALYSIS_ID), any(Pageable.class)))
                .thenReturn(List.of(other, matching));

        var result = adapter.boundedFacts(snapshotWithBaseline(),
                "How does DevLog persist repository knowledge?");

        verify(factRepository).findByAnalysisIdOrderByDetectedAtDescIdDesc(
                eq(BASELINE_ANALYSIS_ID),
                eq(PageRequest.of(0, 200)));
        assertEquals(1, result.size());
        assertEquals(matching.getId(), result.get(0).id());
        assertEquals("repository synchronization checkpoints",
                result.get(0).content());
        assertTrue(result.get(0).evidenceReferences().contains("git:src:matching"));
        assertEquals(matching.getDetectedAt(), result.get(0).detectedAt());
    }

    @org.junit.jupiter.api.Test
    void capsFactCandidatesAtEightRegardlessOfMatchCount() {
        var many = new java.util.ArrayList<Fact>();
        for (int index = 0; index < 30; index++)
            many.add(fact("fact-" + index, "repository knowledge item " + index,
                    Instant.now()));
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                any(), any())).thenReturn(many);

        var result = adapter.boundedFacts(snapshotWithBaseline(),
                "repository knowledge");

        assertEquals(8, result.size());
    }

    @org.junit.jupiter.api.Test
    void windowIsCappedAtTwoHundredRowsBeforeScoring() {
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                any(), any())).thenReturn(List.of());
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(
                any(), any())).thenReturn(List.of());

        adapter.boundedFacts(snapshotWithBaseline(), "anything");
        adapter.boundedObservations(snapshotWithBaseline(), "anything");

        verify(factRepository).findByAnalysisIdOrderByDetectedAtDescIdDesc(
                any(UUID.class), eq(PageRequest.of(0, 200)));
        verify(observationRepository).findByAnalysisIdOrderByCreatedAtDescIdDesc(
                any(UUID.class), eq(PageRequest.of(0, 200)));
    }

    @org.junit.jupiter.api.Test
    void observationsCarrySupportingFactProvenanceAndTime() {
        UUID factId = UUID.randomUUID();
        Observation observation = Observation.builder()
                .id(UUID.randomUUID())
                .content("freshness checkpoints were introduced for repositories")
                .createdAt(Instant.parse("2026-08-25T00:00:00Z"))
                .supportingFacts(new java.util.LinkedHashSet<>(List.of(
                        Fact.builder().id(factId).build())))
                .build();
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(
                any(), any())).thenReturn(List.of(observation));

        var result = adapter.boundedObservations(snapshotWithBaseline(),
                "why does freshness matter for repository context");

        assertEquals(1, result.size());
        assertEquals(observation.getId(), result.get(0).id());
        assertEquals(List.of(factId), result.get(0).supportingFactIds());
        assertEquals(observation.getCreatedAt(), result.get(0).createdAt());
    }

    @org.junit.jupiter.api.Test
    void noBaselineProfileDegradesToEmptyKnowledgeSafely() {
        var snapshot = new ProjectContextSnapshot(null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());

        assertTrue(adapter.boundedFacts(snapshot, "anything").isEmpty());
        assertTrue(adapter.boundedObservations(snapshot, "anything").isEmpty());
    }

    @org.junit.jupiter.api.Test
    void scopedToBaselineAnalysisOnly() {
        UUID otherAnalysis = UUID.randomUUID();
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                any(), any())).thenReturn(List.of());

        adapter.boundedFacts(snapshotWithBaseline(), "anything");

        verify(factRepository).findByAnalysisIdOrderByDetectedAtDescIdDesc(
                eq(BASELINE_ANALYSIS_ID), any(Pageable.class));
        // never queries another analysis scope
        org.mockito.Mockito.verify(factRepository, org.mockito.Mockito.never())
                .findByAnalysisIdOrderByDetectedAtDescIdDesc(eq(otherAnalysis),
                        any(Pageable.class));
    }
}