package com.hopeful117.devlogai.storycontextanalysis.history;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalKnowledgeCandidateServiceTest {

    private static final String MATCHING_REFERENCE = "src/main/java/StoryService.java";

    @Mock private AnalysisRepository analysisRepository;
    @Mock private FactRepository factRepository;
    @Mock private ObservationRepository observationRepository;

    @InjectMocks private HistoricalKnowledgeCandidateService service;

    @Test
    void retrievesOnlyExactBoundedCandidatesAndPreservesObservationClosure() {
        UUID projectId = UUID.randomUUID();
        UUID baselineAnalysisId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis newestAnalysis = analysis(UUID.randomUUID(), project, "2026-09-08T10:00:00Z");
        Analysis olderAnalysis = analysis(UUID.randomUUID(), project, "2026-09-01T10:00:00Z");
        Fact newerUnrelated = fact(
                UUID.randomUUID(), newestAnalysis, "unrelated", "src/main/java/Other.java",
                "2026-09-08T11:00:00Z");
        Fact olderMatching = fact(
                UUID.randomUUID(), olderAnalysis, "matching", MATCHING_REFERENCE,
                "2026-09-01T11:00:00Z");
        Fact partialMatch = fact(
                UUID.randomUUID(), olderAnalysis, "partial", MATCHING_REFERENCE + ":42",
                "2026-09-01T10:30:00Z");
        Observation valid = observation(
                UUID.randomUUID(), olderAnalysis, Set.of(olderMatching),
                "2026-09-01T12:00:00Z");
        Observation missingSupport = observation(
                UUID.randomUUID(), olderAnalysis, Set.of(olderMatching, partialMatch),
                "2026-09-01T11:30:00Z");
        Observation crossAnalysisSupport = observation(
                UUID.randomUUID(), olderAnalysis, Set.of(olderMatching, newerUnrelated),
                "2026-09-01T11:00:00Z");
        EngineeringStory story = EngineeringStory.builder()
                .project(project)
                .storyPath("docs/stories/0117/story.md")
                .build();

        when(analysisRepository.findHistoricalCandidates(
                eq(projectId), eq(baselineAnalysisId), eq(AnalysisStatus.COMPLETED), any()))
                .thenReturn(List.of(newestAnalysis, olderAnalysis));
        when(factRepository.findHistoricalCandidates(anyCollection(), any()))
                .thenReturn(List.of(newerUnrelated, olderMatching, olderMatching, partialMatch));
        when(observationRepository.findHistoricalCandidates(anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(valid, missingSupport, crossAnalysisSupport));

        var result = service.retrieve(
                projectId,
                baselineAnalysisId,
                story,
                List.of(MATCHING_REFERENCE),
                engineeringContext("canonical:story", "src/main/java/Scoped.java")
        );

        assertEquals(List.of(olderMatching.getId()),
                result.facts().stream().map(value -> value.id()).toList());
        assertEquals(List.of(valid.getId()),
                result.observations().stream().map(value -> value.id()).toList());
        assertEquals(List.of(olderMatching.getId()),
                result.observations().getFirst().supportingFactIds());

        assertPageSize(analysisRepository, HistoricalKnowledgeCandidateService.MAX_HISTORICAL_ANALYSES,
                projectId, baselineAnalysisId);
        ArgumentCaptor<Pageable> factPage = ArgumentCaptor.forClass(Pageable.class);
        verify(factRepository).findHistoricalCandidates(anyCollection(), factPage.capture());
        assertEquals(HistoricalKnowledgeCandidateService.MAX_SCANNED_FACTS,
                factPage.getValue().getPageSize());
        ArgumentCaptor<Pageable> observationPage = ArgumentCaptor.forClass(Pageable.class);
        verify(observationRepository).findHistoricalCandidates(
                anyCollection(), anyCollection(), observationPage.capture());
        assertEquals(HistoricalKnowledgeCandidateService.MAX_HISTORICAL_OBSERVATIONS,
                observationPage.getValue().getPageSize());
        verify(factRepository, never()).findByAnalysisIdOrderByDetectedAtDesc(any());
        verify(observationRepository, never()).findByAnalysisIdOrderByCreatedAtDesc(any());
    }

    @Test
    void appliesStableIdentityDeduplicationAndHistoricalFactBound() {
        UUID projectId = UUID.randomUUID();
        UUID baselineAnalysisId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis historicalAnalysis = analysis(
                UUID.randomUUID(), project, "2026-09-01T10:00:00Z");
        List<Fact> facts = java.util.stream.IntStream
                .range(0, HistoricalKnowledgeCandidateService.MAX_HISTORICAL_FACTS + 5)
                .mapToObj(index -> fact(
                        UUID.nameUUIDFromBytes(("fact-" + index).getBytes()),
                        historicalAnalysis,
                        "fact-" + index,
                        MATCHING_REFERENCE,
                        "2026-09-01T11:00:00Z"))
                .toList();
        List<Fact> duplicatedInput = new java.util.ArrayList<>(facts);
        duplicatedInput.addFirst(facts.getFirst());

        when(analysisRepository.findHistoricalCandidates(
                eq(projectId), eq(baselineAnalysisId), eq(AnalysisStatus.COMPLETED), any()))
                .thenReturn(List.of(historicalAnalysis));
        when(factRepository.findHistoricalCandidates(anyCollection(), any()))
                .thenReturn(duplicatedInput);
        when(observationRepository.findHistoricalCandidates(anyCollection(), anyCollection(), any()))
                .thenReturn(List.of());

        var result = service.retrieve(
                projectId,
                baselineAnalysisId,
                EngineeringStory.builder().project(project).build(),
                List.of(MATCHING_REFERENCE),
                engineeringContext(null, null)
        );

        assertEquals(HistoricalKnowledgeCandidateService.MAX_HISTORICAL_FACTS,
                result.facts().size());
        assertEquals(result.facts().size(), result.facts().stream().map(value -> value.id()).distinct().count());
        assertEquals(facts.stream().limit(HistoricalKnowledgeCandidateService.MAX_HISTORICAL_FACTS)
                        .map(Fact::getId).toList(),
                result.facts().stream().map(value -> value.id()).toList());
    }

    @Test
    void returnsEmptyWithoutProvenanceAnchorsOrHistoricalAnalyses() {
        UUID projectId = UUID.randomUUID();
        UUID baselineAnalysisId = UUID.randomUUID();
        EngineeringStory unscopedStory = EngineeringStory.builder()
                .project(Project.builder().id(projectId).build())
                .build();

        var withoutAnchors = service.retrieve(
                projectId, baselineAnalysisId, unscopedStory, List.of(),
                engineeringContext(null, null));

        assertTrue(withoutAnchors.facts().isEmpty());
        assertTrue(withoutAnchors.observations().isEmpty());
        verifyNoInteractions(analysisRepository, factRepository, observationRepository);

        EngineeringStory scopedStory = EngineeringStory.builder()
                .project(unscopedStory.getProject())
                .storyPath("docs/stories/0117/story.md")
                .build();
        when(analysisRepository.findHistoricalCandidates(
                eq(projectId), eq(baselineAnalysisId), eq(AnalysisStatus.COMPLETED), any()))
                .thenReturn(List.of());

        var withoutAnalyses = service.retrieve(
                projectId, baselineAnalysisId, scopedStory, List.of(),
                engineeringContext(null, null));

        assertTrue(withoutAnalyses.facts().isEmpty());
        assertTrue(withoutAnalyses.observations().isEmpty());
        verifyNoInteractions(factRepository, observationRepository);
    }

    private void assertPageSize(
            AnalysisRepository repository,
            int expectedSize,
            UUID projectId,
            UUID baselineAnalysisId
    ) {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findHistoricalCandidates(
                eq(projectId), eq(baselineAnalysisId), eq(AnalysisStatus.COMPLETED), page.capture());
        assertEquals(expectedSize, page.getValue().getPageSize());
        assertEquals(0, page.getValue().getPageNumber());
    }

    private Analysis analysis(UUID id, Project project, String completedAt) {
        Instant completed = Instant.parse(completedAt);
        return Analysis.builder()
                .id(id)
                .project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW)
                .status(AnalysisStatus.COMPLETED)
                .createdAt(completed.minusSeconds(60))
                .completedAt(completed)
                .build();
    }

    private Fact fact(
            UUID id,
            Analysis analysis,
            String content,
            String reference,
            String detectedAt
    ) {
        return Fact.builder()
                .id(id)
                .analysis(analysis)
                .type(FactType.DOCUMENTATION_CHANGE)
                .content(content)
                .source(reference)
                .evidenceReferences(new LinkedHashSet<>(Set.of(reference)))
                .detectedAt(Instant.parse(detectedAt))
                .build();
    }

    private Observation observation(
            UUID id,
            Analysis analysis,
            Set<Fact> supportingFacts,
            String createdAt
    ) {
        return Observation.builder()
                .id(id)
                .analysis(analysis)
                .type(ObservationType.ARCHITECTURE_DOCUMENTATION_PRESENT)
                .content("Historical observation")
                .ruleId("HISTORICAL_RULE")
                .ruleVersion("v1")
                .supportingFacts(new LinkedHashSet<>(supportingFacts))
                .createdAt(Instant.parse(createdAt))
                .build();
    }

    private EngineeringContext engineeringContext(String reference, String originatingFile) {
        List<EngineeringEvidence> evidence = reference == null && originatingFile == null
                ? List.of()
                : List.of(new EngineeringEvidence(
                        "SOURCE_FILE", "CODE", "Scoped evidence", "REPOSITORY",
                        originatingFile, "provenance-id", reference, 100,
                        "Story scope", Instant.parse("2026-09-09T10:00:00Z"),
                        List.of(), Map.of(), null, null, null,
                        TrustTier.TECHNICAL_EVIDENCE));
        return new EngineeringContext(null, "engineering-story-context-analysis", evidence,
                null, List.of(), null);
    }
}
