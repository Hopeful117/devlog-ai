package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedKnowledgePromptProjectionServiceTest {

    private static final SelectedKnowledge.DiagnosticSnapshot DIAGNOSTICS =
            new SelectedKnowledge.DiagnosticSnapshot(true, false, 0, 0);
    private static final SelectedKnowledge.SelectionMetadata METADATA =
            new SelectedKnowledge.SelectionMetadata(
                    "knowledge-selection-v4",
                    List.of(),
                    0,
                    0,
                    new SelectedKnowledge.KnowledgeBudget(40, 25, 10, 5, 60),
                    "COMPLETE"
            );

    private final SelectedKnowledgePromptProjectionService service =
            new SelectedKnowledgePromptProjectionService(new ObjectMapper());

    @Test
    void shouldCompactRepositoryContextForPromptPayload() {
        RepositoryContext repositoryContext = repositoryContext();
        SelectedKnowledge selectedKnowledge = selectedKnowledge(
                List.of(), List.of(), List.of(), List.of(), repositoryContext, profile());

        Map<String, Object> projected = service.toMap(selectedKnowledge);

        @SuppressWarnings("unchecked")
        Map<String, Object> projectedRepositoryContext =
                (Map<String, Object>) projected.get("repositoryContext");
        assertNotNull(projectedRepositoryContext);
        assertEquals("repository-context-engine-v1",
                projectedRepositoryContext.get("contextVersion"));
        assertEquals("PROJECT_STATE", projectedRepositoryContext.get("profile"));
        assertEquals(List.of("warn"), projectedRepositoryContext.get("warnings"));
        assertFalse(projectedRepositoryContext.containsKey("selectionDecisions"));
        assertFalse(projectedRepositoryContext.containsKey("selectedByLayer"));
        assertFalse(projectedRepositoryContext.containsKey("diagnostics"));
        assertFalse(projectedRepositoryContext.containsKey("usedTokens"));

        @SuppressWarnings("unchecked")
        Map<String, Object> projectedEvidence =
                ((List<Map<String, Object>>) projectedRepositoryContext.get("evidence")).getFirst();
        assertEquals("backend/src/main/java/App.java", projectedEvidence.get("reference"));
        assertFalse(projectedEvidence.containsKey("score"));
        assertFalse(projectedEvidence.containsKey("provenance"));
        assertFalse(projectedEvidence.containsKey("rankingReasons"));
        assertFalse(projectedEvidence.containsKey("extractionMetadata"));
    }

    @Test
    void shouldOmitSelectedInsightIdentifiersFromPromptPayload() {
        UUID insightId = UUID.randomUUID();
        UUID sourceAnalysisId = UUID.randomUUID();
        SelectedKnowledge selectedKnowledge = selectedKnowledge(
                List.of(new SelectedKnowledge.InsightSnapshot(
                        insightId,
                        sourceAnalysisId,
                        InsightType.ARCHITECTURAL,
                        InsightSeverity.INFO,
                        "Controllers",
                        "The project exposes REST controllers."
                )),
                List.of(),
                List.of(),
                List.of(),
                null,
                profile());

        Map<String, Object> projected = service.toMap(selectedKnowledge);

        @SuppressWarnings("unchecked")
        Map<String, Object> projectedInsight =
                ((List<Map<String, Object>>) projected.get("selectedInsights")).getFirst();
        assertEquals("ARCHITECTURAL", projectedInsight.get("type"));
        assertEquals("INFO", projectedInsight.get("severity"));
        assertEquals("Controllers", projectedInsight.get("title"));
        assertFalse(projectedInsight.containsKey("id"));
        assertFalse(projectedInsight.containsKey("analysisId"));
    }

    @Test
    void shouldExposeHumanContextInputsAsDistinctPromptSection() {
        SelectedKnowledge selectedKnowledge = selectedKnowledge(
                List.of(),
                List.of(),
                List.of(new ProjectContextSnapshot.HumanContextInputSnapshot(
                        UUID.randomUUID(),
                        ProjectHumanContextInputType.GOAL,
                        "Improve context quality",
                        "Raise the quality of information for humans and agents.",
                        "ACTIVE",
                        Instant.parse("2026-08-13T10:00:00Z"))),
                List.of(),
                null,
                null);

        Map<String, Object> projected = service.toMap(selectedKnowledge);

        @SuppressWarnings("unchecked")
        Map<String, Object> projectedInput =
                ((List<Map<String, Object>>) projected.get("selectedHumanContextInputs")).getFirst();
        assertEquals("GOAL", projectedInput.get("type"));
        assertEquals("Improve context quality", projectedInput.get("title"));
        assertEquals("ACTIVE", projectedInput.get("status"));
    }

    @Test
    void shouldProjectInsightToInsightRelationshipWhenBothInsightsAreSelected() {
        UUID insightA = uuid("00000000-0000-0000-0000-00000000000a");
        UUID insightB = uuid("00000000-0000-0000-0000-00000000000b");
        SelectedKnowledge selectedKnowledge = selectedKnowledge(
                List.of(insight(insightA, "A"), insight(insightB, "B")),
                List.of(),
                List.of(),
                List.of(relation("00000000-0000-0000-0000-000000000101",
                        EntityType.INSIGHT, insightA,
                        EntityType.INSIGHT, insightB,
                        KnowledgeRelationType.RELATES_TO)),
                null,
                profile());

        SelectedKnowledgePromptProjectionService.PromptProjection projected =
                service.project(selectedKnowledge);

        assertEquals(1, projected.relationshipHighlights().size());
        var highlight = projected.relationshipHighlights().getFirst();
        assertEquals("RELATES_TO", highlight.relationType());
        assertEquals("INSIGHT", highlight.source().entityType());
        assertEquals(insightA.toString(), highlight.source().entityId());
        assertEquals("INSIGHT", highlight.target().entityType());
        assertEquals(insightB.toString(), highlight.target().entityId());
    }

    @Test
    void shouldExcludeInsightToInsightRelationshipWhenTargetInsightIsNotSelected() {
        UUID insightA = uuid("00000000-0000-0000-0000-00000000000a");
        UUID insightB = uuid("00000000-0000-0000-0000-00000000000b");
        SelectedKnowledge selectedKnowledge = selectedKnowledge(
                List.of(insight(insightA, "A")),
                List.of(),
                List.of(),
                List.of(relation("00000000-0000-0000-0000-000000000102",
                        EntityType.INSIGHT, insightA,
                        EntityType.INSIGHT, insightB,
                        KnowledgeRelationType.RELATES_TO)),
                null,
                profile());

        SelectedKnowledgePromptProjectionService.PromptProjection projected =
                service.project(selectedKnowledge);

        assertTrue(projected.relationshipHighlights().isEmpty());
        assertEquals(1, projected.selectedInsights().size());
    }

    @Test
    void shouldProjectInsightEventAndEventInsightRelationshipsWhenEndpointsAreSelected() {
        UUID insightId = uuid("00000000-0000-0000-0000-00000000000a");
        UUID eventId = uuid("00000000-0000-0000-0000-00000000000e");
        SelectedKnowledge selectedKnowledge = selectedKnowledge(
                List.of(insight(insightId, "A")),
                List.of(engineeringEvent(eventId, "Event")),
                List.of(),
                List.of(
                        relation("00000000-0000-0000-0000-000000000103",
                                EntityType.INSIGHT, insightId,
                                EntityType.ENGINEERING_EVENT, eventId,
                                KnowledgeRelationType.INFORMED_BY),
                        relation("00000000-0000-0000-0000-000000000104",
                                EntityType.ENGINEERING_EVENT, eventId,
                                EntityType.INSIGHT, insightId,
                                KnowledgeRelationType.DERIVED_FROM)),
                null,
                profile());

        List<SelectedKnowledgePromptProjectionService.PromptRelationshipHighlight> highlights =
                service.project(selectedKnowledge).relationshipHighlights();

        assertEquals(2, highlights.size());
        assertTrue(highlights.stream().anyMatch(value ->
                value.relationType().equals("INFORMED_BY")
                        && value.source().entityType().equals("INSIGHT")
                        && value.target().entityType().equals("ENGINEERING_EVENT")));
        assertTrue(highlights.stream().anyMatch(value ->
                value.relationType().equals("DERIVED_FROM")
                        && value.source().entityType().equals("ENGINEERING_EVENT")
                        && value.target().entityType().equals("INSIGHT")));
    }

    @Test
    void shouldProjectEventToEventOnlyWhenBothEventsAreSelected() {
        UUID eventA = uuid("00000000-0000-0000-0000-00000000000e");
        UUID eventB = uuid("00000000-0000-0000-0000-00000000000f");
        UUID missingEvent = uuid("00000000-0000-0000-0000-000000000010");
        SelectedKnowledge selectedKnowledge = selectedKnowledge(
                List.of(),
                List.of(engineeringEvent(eventA, "A"), engineeringEvent(eventB, "B")),
                List.of(),
                List.of(
                        relation("00000000-0000-0000-0000-000000000105",
                                EntityType.ENGINEERING_EVENT, eventA,
                                EntityType.ENGINEERING_EVENT, eventB,
                                KnowledgeRelationType.RELATES_TO),
                        relation("00000000-0000-0000-0000-000000000106",
                                EntityType.ENGINEERING_EVENT, eventA,
                                EntityType.ENGINEERING_EVENT, missingEvent,
                                KnowledgeRelationType.RELATES_TO)),
                null,
                profile());

        List<SelectedKnowledgePromptProjectionService.PromptRelationshipHighlight> highlights =
                service.project(selectedKnowledge).relationshipHighlights();

        assertEquals(1, highlights.size());
        assertEquals(eventA.toString(), highlights.getFirst().source().entityId());
        assertEquals(eventB.toString(), highlights.getFirst().target().entityId());
    }

    @Test
    void shouldExcludeDecisionAndChallengeEndpointsFromRelationshipHighlights() {
        UUID insightId = uuid("00000000-0000-0000-0000-00000000000a");
        UUID eventId = uuid("00000000-0000-0000-0000-00000000000e");
        UUID decisionId = uuid("00000000-0000-0000-0000-00000000000d");
        UUID challengeId = uuid("00000000-0000-0000-0000-00000000000c");
        SelectedKnowledge selectedKnowledge = selectedKnowledge(
                List.of(insight(insightId, "A")),
                List.of(engineeringEvent(eventId, "Event")),
                List.of(),
                List.of(
                        relation("00000000-0000-0000-0000-000000000107",
                                EntityType.INSIGHT, insightId,
                                EntityType.DECISION, decisionId,
                                KnowledgeRelationType.ADDRESSES),
                        relation("00000000-0000-0000-0000-000000000108",
                                EntityType.CHALLENGE, challengeId,
                                EntityType.ENGINEERING_EVENT, eventId,
                                KnowledgeRelationType.CAUSED_BY)),
                null,
                profile());

        assertTrue(service.project(selectedKnowledge).relationshipHighlights().isEmpty());
    }

    @Test
    void shouldProjectBoundedDeterministicRelationshipHighlightsWithoutSelectionExpansionOrInvention() {
        UUID insightA = uuid("00000000-0000-0000-0000-00000000000a");
        UUID insightB = uuid("00000000-0000-0000-0000-00000000000b");
        UUID eventA = uuid("00000000-0000-0000-0000-00000000000e");
        UUID eventB = uuid("00000000-0000-0000-0000-00000000000f");
        List<ProjectContextSnapshot.KnowledgeRelationSnapshot> relations = List.of(
                relation("00000000-0000-0000-0000-000000000120", EntityType.INSIGHT, insightA, EntityType.INSIGHT, insightB, KnowledgeRelationType.RELATES_TO),
                relation("00000000-0000-0000-0000-000000000121", EntityType.INSIGHT, insightA, EntityType.ENGINEERING_EVENT, eventA, KnowledgeRelationType.ADDRESSES),
                relation("00000000-0000-0000-0000-000000000122", EntityType.ENGINEERING_EVENT, eventA, EntityType.INSIGHT, insightA, KnowledgeRelationType.CAUSED_BY),
                relation("00000000-0000-0000-0000-000000000123", EntityType.ENGINEERING_EVENT, eventA, EntityType.ENGINEERING_EVENT, eventB, KnowledgeRelationType.DERIVED_FROM),
                relation("00000000-0000-0000-0000-000000000124", EntityType.INSIGHT, insightB, EntityType.ENGINEERING_EVENT, eventB, KnowledgeRelationType.INFORMED_BY),
                relation("00000000-0000-0000-0000-000000000125", EntityType.INSIGHT, insightA, EntityType.INSIGHT, insightB, KnowledgeRelationType.RESOLVES),
                relation("00000000-0000-0000-0000-000000000126", EntityType.INSIGHT, insightB, EntityType.INSIGHT, insightA, KnowledgeRelationType.ADDRESSES),
                relation("00000000-0000-0000-0000-000000000127", EntityType.ENGINEERING_EVENT, eventB, EntityType.ENGINEERING_EVENT, eventA, KnowledgeRelationType.RELATES_TO),
                relation("00000000-0000-0000-0000-000000000128", EntityType.ENGINEERING_EVENT, eventB, EntityType.INSIGHT, insightB, KnowledgeRelationType.CAUSED_BY),
                relation("00000000-0000-0000-0000-000000000129", EntityType.INSIGHT, insightA, EntityType.ENGINEERING_EVENT, eventB, KnowledgeRelationType.DERIVED_FROM),
                relation("00000000-0000-0000-0000-00000000012a", EntityType.INSIGHT, insightB, EntityType.ENGINEERING_EVENT, eventA, KnowledgeRelationType.RELATES_TO),
                relation("00000000-0000-0000-0000-00000000012b", EntityType.ENGINEERING_EVENT, eventA, EntityType.INSIGHT, insightB, KnowledgeRelationType.ADDRESSES),
                relation("00000000-0000-0000-0000-00000000012c", EntityType.ENGINEERING_EVENT, eventB, EntityType.INSIGHT, insightA, KnowledgeRelationType.INFORMED_BY),
                relation("00000000-0000-0000-0000-00000000012d", EntityType.INSIGHT, insightB, EntityType.ENGINEERING_EVENT, eventA, KnowledgeRelationType.RESOLVES),
                relation("00000000-0000-0000-0000-00000000012e", EntityType.ENGINEERING_EVENT, eventA, EntityType.ENGINEERING_EVENT, eventB, KnowledgeRelationType.ADDRESSES),
                relation("00000000-0000-0000-0000-00000000012f", EntityType.INSIGHT, insightA, EntityType.INSIGHT, insightB, KnowledgeRelationType.INFORMED_BY),
                relation("00000000-0000-0000-0000-000000000130", EntityType.ENGINEERING_EVENT, eventB, EntityType.ENGINEERING_EVENT, eventA, KnowledgeRelationType.RESOLVES),
                relation("00000000-0000-0000-0000-000000000131", EntityType.INSIGHT, insightA, EntityType.ENGINEERING_EVENT, eventA, KnowledgeRelationType.RELATES_TO),
                relation("00000000-0000-0000-0000-000000000132", EntityType.ENGINEERING_EVENT, eventA, EntityType.INSIGHT, insightA, KnowledgeRelationType.RELATES_TO),
                relation("00000000-0000-0000-0000-000000000133", EntityType.INSIGHT, insightB, EntityType.INSIGHT, insightA, KnowledgeRelationType.DERIVED_FROM),
                relation("00000000-0000-0000-0000-000000000134", EntityType.ENGINEERING_EVENT, eventA, EntityType.ENGINEERING_EVENT, eventB, KnowledgeRelationType.INFORMED_BY),
                relation("00000000-0000-0000-0000-000000000135", EntityType.INSIGHT, insightA, EntityType.INSIGHT, insightB, KnowledgeRelationType.CAUSED_BY)
        );
        SelectedKnowledge selectedKnowledge = selectedKnowledge(
                List.of(insight(insightA, "A"), insight(insightB, "B")),
                List.of(engineeringEvent(eventA, "Event A"), engineeringEvent(eventB, "Event B")),
                List.of(),
                relations,
                null,
                profile());

        var first = service.project(selectedKnowledge);
        var second = service.project(selectedKnowledge);
        List<SelectedKnowledgePromptProjectionService.PromptRelationshipHighlight> expectedOrder =
                first.relationshipHighlights().stream()
                        .sorted(Comparator.comparing(SelectedKnowledgePromptProjectionService.PromptRelationshipHighlight::relationType)
                                .thenComparing(value -> value.source().entityType())
                                .thenComparing(value -> value.source().entityId())
                                .thenComparing(value -> value.target().entityType())
                                .thenComparing(value -> value.target().entityId()))
                        .toList();
        Set<String> canonicalKeys = relations.stream()
                .map(this::canonicalKey)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(SelectedKnowledgePromptProjectionService.MAX_RELATIONSHIP_HIGHLIGHTS,
                first.relationshipHighlights().size());
        assertEquals(first.relationshipHighlights(), second.relationshipHighlights());
        assertEquals(expectedOrder, first.relationshipHighlights());
        assertEquals(2, first.selectedInsights().size());
        assertEquals(2, first.selectedEngineeringEvents().size());
        assertTrue(first.relationshipHighlights().stream().allMatch(value ->
                canonicalKeys.contains(canonicalKey(value))));
    }

    private SelectedKnowledge selectedKnowledge(
            List<SelectedKnowledge.InsightSnapshot> insights,
            List<ProjectContextSnapshot.EngineeringEventSnapshot> engineeringEvents,
            List<ProjectContextSnapshot.HumanContextInputSnapshot> humanContextInputs,
            List<ProjectContextSnapshot.KnowledgeRelationSnapshot> knowledgeRelations,
            RepositoryContext repositoryContext,
            ProjectProfileResponse profile
    ) {
        return new SelectedKnowledge(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "DevLog", "devlog-ai",
                        "desc", ProjectStatus.ACTIVE),
                null,
                profile,
                List.of(),
                List.of(),
                DIAGNOSTICS,
                insights,
                List.of(),
                engineeringEvents,
                humanContextInputs,
                knowledgeRelations,
                repositoryContext,
                null,
                METADATA,
                "a".repeat(64)
        );
    }

    private SelectedKnowledge.InsightSnapshot insight(UUID id, String title) {
        return new SelectedKnowledge.InsightSnapshot(id, UUID.randomUUID(),
                InsightType.ARCHITECTURAL, InsightSeverity.INFO, title, title + " content");
    }

    private ProjectContextSnapshot.EngineeringEventSnapshot engineeringEvent(UUID id, String title) {
        return new ProjectContextSnapshot.EngineeringEventSnapshot(id, "CATEGORY", title,
                title + " summary", UUID.randomUUID(), "base", "target", Instant.EPOCH,
                UUID.randomUUID());
    }

    private ProjectContextSnapshot.KnowledgeRelationSnapshot relation(
            String id,
            EntityType sourceType,
            UUID sourceId,
            EntityType targetType,
            UUID targetId,
            KnowledgeRelationType relationType
    ) {
        return new ProjectContextSnapshot.KnowledgeRelationSnapshot(
                uuid(id), sourceType, sourceId, targetType, targetId, relationType,
                relationType.name(), Instant.EPOCH);
    }

    private String canonicalKey(ProjectContextSnapshot.KnowledgeRelationSnapshot relation) {
        return relation.relationType().name() + "|" + relation.sourceEntityType().name() + "|"
                + relation.sourceEntityId() + "|" + relation.targetEntityType().name() + "|"
                + relation.targetEntityId();
    }

    private String canonicalKey(SelectedKnowledgePromptProjectionService.PromptRelationshipHighlight relation) {
        return relation.relationType() + "|" + relation.source().entityType() + "|"
                + relation.source().entityId() + "|" + relation.target().entityType() + "|"
                + relation.target().entityId();
    }

    private ProjectProfileResponse profile() {
        return new ProjectProfileResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "v1", "r1", Instant.now(), null, Map.of(),
                new ProjectProfileResponse.Completeness(
                        com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus.COMPLETE,
                        true, false, 0, 0, 1, 0, 0),
                List.of(), "summary", List.of(), 0);
    }

    private RepositoryContext repositoryContext() {
        RepositoryEvidence evidence = new RepositoryEvidence(
                RepositoryContextLayer.CURRENT_ANALYSIS,
                "FILE",
                "backend/src/main/java/App.java",
                "Application entry point",
                Instant.parse("2026-08-12T20:00:00Z"),
                EvidenceScore.unscored(),
                List.of("pom.xml"),
                new RepositoryEvidence.EvidenceProvenance(
                        "FILE", "repo", "backend/src/main/java/App.java", "app"),
                Map.of("extractor", "test"),
                123,
                List.of("high score"),
                new RepositoryEvidenceContent(
                        RepositoryEvidenceContent.Status.COMPLETE,
                        "class App {}",
                        "full content",
                        "content-policy",
                        "v1",
                        "r1",
                        "alloc-policy",
                        "v1",
                        1,
                        List.of("kept")
                ),
                new RepositoryEvidenceSymbols(
                        RepositoryEvidenceSymbols.Status.EXTRACTED,
                        "symbols available",
                        "symbols-policy",
                        "v1",
                        "javaparser",
                        "3.27.0",
                        "r1",
                        1,
                        List.of("top ranked"),
                        false,
                        1,
                        1,
                        List.of(new RepositoryEvidenceSymbols.JavaDeclaration(
                                RepositoryEvidenceSymbols.Kind.CLASS,
                                "App",
                                null,
                                List.of("public"),
                                null,
                                List.of(),
                                List.of(),
                                new RepositoryEvidenceSymbols.SourceLocation(1, 1, 1, 10)
                        ))
                )
        );
        return new RepositoryContext(
                "repository-context-engine-v1",
                ContextProfile.PROJECT_STATE,
                List.of("project-state-v1"),
                "context-intelligence-v1",
                List.of("selected for understanding refresh"),
                List.of(evidence),
                Map.of(RepositoryContextLayer.CURRENT_ANALYSIS, 1),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                456,
                10,
                9,
                false,
                List.of(new RepositoryContext.SelectionDecision(
                        "backend/src/main/java/App.java", true, "top candidate", 100, 123
                )),
                List.of("warn"),
                "d".repeat(64)
        );
    }

    private UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
