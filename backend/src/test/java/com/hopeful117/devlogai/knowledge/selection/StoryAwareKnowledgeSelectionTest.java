package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryAwareKnowledgeSelectionTest {

    private static final UUID PROJECT_ID = UUID.fromString("10000000-0000-0000-0000-000000000115");
    private static final UUID ANALYSIS_ID = UUID.fromString("20000000-0000-0000-0000-000000000115");

    @Mock AnalysisExecutionDiagnosticRepository diagnostics;
    @Mock InsightRepository insightRepository;
    @Mock ObjectMapper objectMapper;
    @Mock RepositoryContextService repositoryContextService;
    @Mock ProjectProfileResponse projectProfile;

    private KnowledgeSelectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeSelectionServiceImpl(
                diagnostics, insightRepository, objectMapper, repositoryContextService, 15);
        when(diagnostics.findById(ANALYSIS_ID)).thenReturn(Optional.of(
                AnalysisExecutionDiagnostic.builder()
                        .analysisId(ANALYSIS_ID).collectionComplete(true).build()));
        when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenReturn("stable-story-aware-selection");
    }

    @Test
    void storyTitleAndPathRankRelevantFactBeforeExistingSignals() {
        var relevant = fact(1, FactType.OTHER, "OAuth boundary", "collector",
                List.of("backend/src/main/java/security/OAuthGateway.java"));
        var unrelated = fact(2, FactType.REPOSITORY_STRUCTURE_SUMMARY,
                "Repository modules", "collector", List.of("README.md"));
        AnalysisContext context = context(List.of(unrelated, relevant), List.of(),
                List.of(story("OAuth gateway", "docs/stories/0115-security/story.md")));
        stubSelection(context, List.of());

        SelectedKnowledge result = service.select(context, storyIntent(), null);

        assertEquals(relevant.id(), result.selectedFacts().getFirst().id());
        assertEquals("knowledge-selection-v5", result.selectionMetadata().selectionVersion());
        assertTrue(result.selectionMetadata().appliedRules()
                .contains("ENGINEERING_STORY_RELEVANCE"));
    }

    @Test
    void storyRelevantObservationRetainsItsOtherwiseUnmatchedSupportingFact() {
        var required = fact(3, FactType.OTHER, "low ranked support", "collector",
                List.of("backend/support.java"));
        var preferred = observation(4, "OAuth authentication boundary", List.of(required.id()));
        var unrelated = observation(5, "Unrelated database detail", List.of());
        AnalysisContext context = context(List.of(required), List.of(unrelated, preferred),
                List.of(story("OAuth authentication", "docs/story.md")));
        stubSelection(context, List.of());

        SelectedKnowledge result = service.select(context, storyIntent(), null);

        assertEquals(preferred.id(), result.selectedObservations().getFirst().id());
        assertTrue(result.selectedFacts().stream().anyMatch(fact -> fact.id().equals(required.id())));
        assertTrue(result.selectedObservations().stream()
                .flatMap(observation -> observation.supportingFactIds().stream())
                .allMatch(factId -> result.selectedFacts().stream()
                        .anyMatch(fact -> fact.id().equals(factId))));
    }

    @Test
    void existingIntentAndGuidanceSignalsOrderEqualStoryMatches() {
        var intentPreferred = fact(6, FactType.REPOSITORY_STRUCTURE_SUMMARY,
                "OAuth modules", "collector", List.of("a"));
        var guidancePreferred = fact(7, FactType.OTHER,
                "OAuth migration priority", "collector", List.of("b"));
        var plain = fact(8, FactType.OTHER, "OAuth detail", "collector", List.of("c"));
        AnalysisContext context = context(List.of(plain, guidancePreferred, intentPreferred),
                List.of(), List.of(story("OAuth", "docs/story.md")));
        stubSelection(context, List.of());
        var guidance = new UserGuidance("migration", null, null, null, null, List.of());

        SelectedKnowledge result = service.select(context, storyIntent(), guidance);

        assertEquals(intentPreferred.id(), result.selectedFacts().getFirst().id());
        assertTrue(result.selectedFacts().indexOf(guidancePreferred)
                < result.selectedFacts().indexOf(plain));
    }

    @Test
    void olderStoryRelevantActiveInsightBeatsNewerUnrelatedInsight() {
        Insight relevant = insight(9, "OAuth authorization boundary",
                Instant.parse("2025-01-01T00:00:00Z"));
        Insight unrelated = insight(10, "Database migration",
                Instant.parse("2026-01-01T00:00:00Z"));
        AnalysisContext context = context(List.of(), List.of(),
                List.of(story("OAuth authorization", "docs/story.md")));
        stubSelection(context, List.of(unrelated, relevant));

        SelectedKnowledge result = service.select(context, storyIntent(), null);

        assertEquals(List.of(relevant.getId()), result.selectedInsights().stream()
                .map(SelectedKnowledge.InsightSnapshot::id).toList());
        verify(insightRepository).findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                PROJECT_ID, List.of(InsightStatus.ACTIVE));
    }

    @Test
    void absentOrAmbiguousCurrentStoryLeavesExistingInsightRecencyOrdering() {
        Insight older = insight(11, "OAuth authorization",
                Instant.parse("2025-01-01T00:00:00Z"));
        Insight newer = insight(12, "Database migration",
                Instant.parse("2026-01-01T00:00:00Z"));
        AnalysisContext absent = context(List.of(), List.of(), List.of());
        stubSelection(absent, List.of(older, newer));

        SelectedKnowledge absentResult = service.select(absent, storyIntent(), null);

        assertEquals(newer.getId(), absentResult.selectedInsights().getFirst().id());

        AnalysisContext ambiguous = context(List.of(), List.of(), List.of(
                story("OAuth", "docs/one.md"), story("Database", "docs/two.md")));
        when(repositoryContextService.build(eq(ambiguous), eq(storyIntent()),
                org.mockito.ArgumentMatchers.isNull(), anyList(), anyList(), any()))
                .thenReturn(emptyRepositoryContext());

        SelectedKnowledge ambiguousResult = service.select(ambiguous, storyIntent(), null);

        assertEquals(newer.getId(), ambiguousResult.selectedInsights().getFirst().id());
    }

    @Test
    void repeatedStoryAwareSelectionIsDeterministic() {
        var facts = List.of(
                fact(13, FactType.OTHER, "OAuth alpha", "collector", List.of("a")),
                fact(14, FactType.OTHER, "OAuth beta", "collector", List.of("b")));
        AnalysisContext context = context(facts, List.of(),
                List.of(story("OAuth", "docs/story.md")));
        stubSelection(context, List.of());

        SelectedKnowledge first = service.select(context, storyIntent(), null);
        SelectedKnowledge second = service.select(context, storyIntent(), null);

        assertEquals(first, second);
    }

    private void stubSelection(AnalysisContext context, List<Insight> insights) {
        when(insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                PROJECT_ID, List.of(InsightStatus.ACTIVE))).thenReturn(insights);
        when(repositoryContextService.build(eq(context), eq(storyIntent()),
                org.mockito.ArgumentMatchers.nullable(UserGuidance.class), anyList(), anyList(), any()))
                .thenReturn(emptyRepositoryContext());
    }

    private AnalysisContext context(
            List<AnalysisContext.FactSnapshot> facts,
            List<AnalysisContext.ObservationSnapshot> observations,
            List<ProjectContextSnapshot.EngineeringStorySnapshot> stories
    ) {
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(PROJECT_ID, "Project", "project", null,
                        ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(ANALYSIS_ID, AnalysisType.STORY_CONTEXT_ANALYSIS,
                        "engineering-story-context-analysis", "v1", AnalysisStatus.IN_PROGRESS,
                        Instant.EPOCH, null, Instant.EPOCH),
                projectProfile, facts, observations,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), stories, List.of());
    }

    private IntentDefinition storyIntent() {
        return new IntentDefinition(
                "engineering-story-context-analysis", "v1", "Analyze Story context",
                List.of(), List.of("grounded"), Map.of("type", "object"),
                "story-context-analysis-prompt-v1", List.of("engineering-story-v1"));
    }

    private AnalysisContext.FactSnapshot fact(
            long id,
            FactType type,
            String content,
            String source,
            List<String> references
    ) {
        return new AnalysisContext.FactSnapshot(new UUID(0, id), type, content, source,
                references, Instant.ofEpochSecond(id));
    }

    private AnalysisContext.ObservationSnapshot observation(
            long id,
            String content,
            List<UUID> supportingFacts
    ) {
        return new AnalysisContext.ObservationSnapshot(new UUID(0, id), ObservationType.OTHER,
                content, "rule", "v1", supportingFacts, Instant.ofEpochSecond(id));
    }

    private ProjectContextSnapshot.EngineeringStorySnapshot story(String title, String path) {
        return new ProjectContextSnapshot.EngineeringStorySnapshot(
                new UUID(0, title.hashCode()), PROJECT_ID, 115, title, "IN_PROGRESS", path,
                null, null, Instant.EPOCH, null);
    }

    private Insight insight(long id, String title, Instant createdAt) {
        return Insight.builder()
                .id(new UUID(0, id))
                .analysis(Analysis.builder().id(new UUID(1, id)).build())
                .type(InsightType.ARCHITECTURAL)
                .severity(InsightSeverity.INFO)
                .status(InsightStatus.ACTIVE)
                .title(title)
                .content(title)
                .evidenceReferences(new ArrayList<>())
                .createdAt(createdAt)
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
