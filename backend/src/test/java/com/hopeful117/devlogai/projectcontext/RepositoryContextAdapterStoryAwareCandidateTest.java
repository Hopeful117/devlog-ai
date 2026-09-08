package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryContextAdapterStoryAwareCandidateTest {

    private static final UUID PROJECT_ID = UUID.fromString("10000000-0000-0000-0000-000000000115");
    private static final UUID ANALYSIS_ID = UUID.fromString("20000000-0000-0000-0000-000000000115");
    private static final UUID STORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000115");

    @Mock ProjectContextProvider projectContextProvider;
    @Mock RepositoryContextService repositoryContextService;
    @Mock InsightRepository insightRepository;
    @Mock FactRepository factRepository;
    @Mock ObservationRepository observationRepository;
    @Mock ProjectCommitRepository commitRepository;
    @Mock ProjectFreshnessService freshnessService;

    private RepositoryContextAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RepositoryContextAdapter(projectContextProvider, repositoryContextService,
                insightRepository, factRepository, observationRepository, commitRepository,
                freshnessService);
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                PROJECT_ID, List.of(InsightStatus.ACTIVE))).thenReturn(List.of());
        when(repositoryContextService.build(any(), any(), any(), anyList()))
                .thenReturn(emptyRepositoryContext());
    }

    @Test
    void storyAndRequestedFileRelevanceAreAppliedBeforeFactCandidateCap() {
        List<Fact> candidates = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            candidates.add(fact(index + 1, "engineering generic " + index,
                    "collector", "docs/generic-" + index + ".md",
                    Instant.parse("2026-01-02T00:00:00Z").plusSeconds(index)));
        }
        Fact olderRelevant = fact(20, "OAuth boundary", "backend/security/OAuthGateway.java",
                "backend/security/OAuthGateway.java", Instant.parse("2025-01-01T00:00:00Z"));
        candidates.add(olderRelevant);
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                eq(ANALYSIS_ID), eq(PageRequest.of(0, 200)))).thenReturn(candidates);
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(
                eq(ANALYSIS_ID), eq(PageRequest.of(0, 200)))).thenReturn(List.of());

        adapter.buildRepositoryContext(PROJECT_ID, "engineering-story-context-analysis",
                snapshot(), List.of("backend/security/OAuthGateway.java"), STORY_ID);

        AnalysisContext selectedContext = capturedContext();
        assertEquals(8, selectedContext.facts().size());
        assertEquals(olderRelevant.getId(), selectedContext.facts().getFirst().id());
        assertTrue(selectedContext.facts().stream()
                .anyMatch(fact -> fact.id().equals(olderRelevant.getId())));
        verify(factRepository).findByAnalysisIdOrderByDetectedAtDescIdDesc(
                ANALYSIS_ID, PageRequest.of(0, 200));
    }

    @Test
    void selectedStoryRelevantObservationRetainsSupportFromSameBaseline() {
        Fact support = fact(30, "unmatched support", "collector", "src/support.java",
                Instant.parse("2025-01-01T00:00:00Z"));
        Observation relevant = observation(31, "OAuth gateway boundary", support);
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(any(), any()))
                .thenReturn(List.of(support));
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(any(), any()))
                .thenReturn(List.of(relevant));

        adapter.buildRepositoryContext(PROJECT_ID, "engineering-story-context-analysis",
                snapshot(), List.of(), STORY_ID);

        AnalysisContext selectedContext = capturedContext();
        assertEquals(List.of(relevant.getId()), selectedContext.observations().stream()
                .map(AnalysisContext.ObservationSnapshot::id).toList());
        assertEquals(List.of(support.getId()), selectedContext.facts().stream()
                .map(AnalysisContext.FactSnapshot::id).toList());
        assertTrue(selectedContext.observations().stream()
                .flatMap(observation -> observation.supportingFactIds().stream())
                .allMatch(id -> selectedContext.facts().stream()
                        .anyMatch(fact -> fact.id().equals(id))));
    }

    @Test
    void requestedFilePathIndependentlyRaisesFactRelevance() {
        Fact newer = fact(32, "engineering generic", "collector", "docs/generic.md",
                Instant.parse("2026-01-01T00:00:00Z"));
        Fact olderFileMatch = fact(33, "engineering generic", "collector",
                "backend/payments/LedgerWriter.java",
                Instant.parse("2025-01-01T00:00:00Z"));
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(any(), any()))
                .thenReturn(List.of(newer, olderFileMatch));
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(any(), any()))
                .thenReturn(List.of());

        adapter.buildRepositoryContext(PROJECT_ID, "engineering-story-context-analysis",
                snapshot(), List.of("backend/payments/LedgerWriter.java"), STORY_ID);

        AnalysisContext selectedContext = capturedContext();
        assertEquals(olderFileMatch.getId(), selectedContext.facts().getFirst().id());
    }

    @Test
    void noStoryOrIntentMatchProducesEmptyKnowledgeInsteadOfRecentFallback() {
        when(factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(any(), any()))
                .thenReturn(List.of(fact(40, "banana", "collector", "fruit.txt", Instant.EPOCH)));
        when(observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(any(), any()))
                .thenReturn(List.of(observation(41, "orange", null)));

        adapter.buildRepositoryContext(PROJECT_ID, "architecture analysis",
                snapshot(), List.of(), STORY_ID);

        AnalysisContext selectedContext = capturedContext();
        assertTrue(selectedContext.facts().isEmpty());
        assertTrue(selectedContext.observations().isEmpty());
    }

    private AnalysisContext capturedContext() {
        ArgumentCaptor<AnalysisContext> captor = ArgumentCaptor.forClass(AnalysisContext.class);
        verify(repositoryContextService).build(captor.capture(), any(), any(), anyList());
        return captor.getValue();
    }

    private ProjectContextSnapshot snapshot() {
        var project = new AnalysisContext.ProjectSnapshot(
                PROJECT_ID, "Project", "project", null, ProjectStatus.ACTIVE);
        var profile = new ProjectProfileResponse(
                UUID.fromString("40000000-0000-0000-0000-000000000115"), PROJECT_ID,
                ANALYSIS_ID, "v1", "revision", Instant.EPOCH, null, Map.of(),
                new ProjectProfileResponse.Completeness(ProfileCompletenessStatus.COMPLETE,
                        true, false, 0, 0, 1, 0, 0),
                List.of(), null, List.of(), 1);
        var story = new ProjectContextSnapshot.EngineeringStorySnapshot(
                STORY_ID, PROJECT_ID, 115, "OAuth gateway", "IN_PROGRESS",
                "docs/stories/0115-oauth/story.md", null, null, Instant.EPOCH, null);
        return new ProjectContextSnapshot(project, profile,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(story), List.of());
    }

    private Fact fact(
            long id,
            String content,
            String source,
            String evidenceReference,
            Instant detectedAt
    ) {
        return Fact.builder()
                .id(new UUID(0, id))
                .type(FactType.OTHER)
                .content(content)
                .source(source)
                .evidenceReferences(new LinkedHashSet<>(List.of(evidenceReference)))
                .detectedAt(detectedAt)
                .build();
    }

    private Observation observation(long id, String content, Fact supportingFact) {
        LinkedHashSet<Fact> supportingFacts = supportingFact == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(List.of(supportingFact));
        return Observation.builder()
                .id(new UUID(0, id))
                .type(ObservationType.OTHER)
                .content(content)
                .ruleId("rule")
                .ruleVersion("v1")
                .supportingFacts(supportingFacts)
                .createdAt(Instant.ofEpochSecond(id))
                .build();
    }

    private RepositoryContext emptyRepositoryContext() {
        return new RepositoryContext(
                "repository-context-engine-v1", ContextProfile.ENGINEERING_STORY,
                List.of("engineering-story-v1"), "context-intelligence-v1", List.of(),
                List.of(), Map.of(), new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                0, 0, 0, false, List.of(), List.of(), "b".repeat(64));
    }
}
