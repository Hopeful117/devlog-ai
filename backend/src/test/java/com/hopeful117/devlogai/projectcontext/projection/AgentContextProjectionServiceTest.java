package com.hopeful117.devlogai.projectcontext.projection;

import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextDiagnostics;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentContextProjectionServiceTest {
    private static final UUID PROJECT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000019");
    private static final Instant GENERATED_AT = Instant.parse("2026-08-09T12:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateCompactTraceableDeterministicProjection() {
        AgentContextProjectionService service = service(100_000, 25_000);
        RepositoryContext context = context(List.of(
                evidence(RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                        "file:src/App.java", "class App"),
                evidence(RepositoryContextLayer.GIT_HISTORY, "COMMIT",
                        "git:abc", null),
                evidence(RepositoryContextLayer.ADR, "DECISION",
                        "decision:adr-46", null),
                evidence(RepositoryContextLayer.PROJECT_DOCUMENTATION, "DOCUMENTATION",
                        "doc:architecture", null),
                evidence(RepositoryContextLayer.VALIDATED_INSIGHT, "INSIGHT",
                        "insight:compact", null)));

        AgentEngineeringStoryContext first = service.project(
                PROJECT_ID, projectContext(), context, GENERATED_AT);
        AgentEngineeringStoryContext second = service.project(
                PROJECT_ID, projectContext(), context, GENERATED_AT.plusSeconds(30));
        AgentRepositoryContext projected = first.repositoryContext();

        assertEquals(AgentRepositoryContext.VERSION, projected.projectionVersion());
        assertEquals("repository-digest", projected.repositoryContextDigest());
        assertEquals(projected.projectionDigest(),
                second.repositoryContext().projectionDigest());
        assertEquals(64, projected.projectionDigest().length());
        assertEquals(5, projected.evidence().size());
        assertEquals(List.of("resolved-abc"), projected.resolvedRevisions());
        assertEquals(Map.of("TOKEN_BUDGET_EXCEEDED", 1),
                projected.rejectedByReason());
        assertEquals(List.of("SELECTED_BY_STRONG_RELEVANCE", "SEMANTIC_TERMS:app"),
                projected.evidence().getFirst().reasons());
        assertEquals(49, projected.evidence().getFirst().relevanceScore());
        assertEquals("src/App.java",
                projected.evidence().getFirst().provenance().originatingFile());
        assertEquals("class App", projected.evidence().getFirst().content().text());
        assertEquals(1,
                projected.evidence().getFirst().symbols().declarations().size());
        assertEquals((projected.accounting().canonicalBytes() + 3) / 4,
                projected.accounting().estimatedTokens());
        assertEquals(0, projected.accounting().removedEvidenceItems());

        String json = objectMapper.writeValueAsString(first);
        assertFalse(json.contains("rejected:file"));
        assertFalse(json.contains("criteria"));
        assertFalse(json.contains("allocationReasons"));
        assertTrue(json.contains("repositoryContextDigest"));
        assertTrue(json.contains("projectionDigest"));
    }

    @Test
    void shouldApplyMechanicalDegradationAndPreserveOutcomeMetadata() {
        RepositoryEvidence large = evidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                "file:src/Large.java", "x".repeat(12_000));
        AgentEngineeringStoryContext result = service(2_500, 625).project(
                PROJECT_ID, projectContext(), context(List.of(large)), GENERATED_AT);
        AgentRepositoryContext projected = result.repositoryContext();

        assertTrue(projected.accounting().canonicalBytes() <= 2_500);
        assertTrue(projected.accounting().estimatedTokens() <= 625);
        assertTrue(projected.accounting().removedRelatedReferences() > 0);
        assertTrue(projected.accounting().removedReasons() > 0);
        assertTrue(projected.accounting().removedDeclarationPayloads() > 0);
        assertTrue(projected.accounting().removedContentPayloads() > 0);
        assertNull(projected.evidence().getFirst().content().text());
        assertEquals("TRUNCATED", projected.evidence().getFirst().content().status());
        assertTrue(projected.evidence().getFirst().symbols().declarations().isEmpty());
        assertTrue(projected.warnings().contains(
                "AGENT_PROJECTION_CONTENT_REMOVED"));
    }

    @Test
    void shouldCompactLongSummaryWhenProjectionNeedsAdditionalReduction() {
        RepositoryEvidence oversizedSummary = oversizedSummaryEvidence("git:oversized");

        AgentRepositoryContext projected = service(1_600, 400).project(
                PROJECT_ID, projectContext(), context(List.of(oversizedSummary)), GENERATED_AT)
                .repositoryContext();

        assertEquals(1, projected.evidence().size());
        assertTrue(projected.evidence().getFirst().summary().length() <= 160);
        assertTrue(projected.warnings().contains("AGENT_PROJECTION_SUMMARY_COMPACTED"));
    }

    @Test
    void shouldFallbackToMinimalOrEmptyEvidenceWhenCompactionGetsTight() {
        RepositoryEvidence first = evidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                "file:src/HugeOne.java", "x".repeat(12_000));
        RepositoryEvidence second = evidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                "file:src/HugeTwo.java", "y".repeat(12_000));

        AgentRepositoryContext projected = service(1_600, 400).project(
                PROJECT_ID, projectContext(), context(List.of(first, second)), GENERATED_AT)
                .repositoryContext();

        assertTrue(projected.warnings().contains("AGENT_PROJECTION_MINIMAL_EVIDENCE_COMPACTED"));
        assertTrue(projected.accounting().canonicalBytes() <= 1_600);
        assertTrue(projected.accounting().estimatedTokens() <= 400);
        assertTrue(projected.evidence().size() <= 1);
    }

    @Test
    void shouldCompactProjectContextWhenEmptyEvidenceStillDoesNotFit() {
        AgentRepositoryContext projected = service(32_768, 8_192).project(
                PROJECT_ID, oversizedProjectContext(),
                context(List.of(evidence(RepositoryContextLayer.GIT_HISTORY,
                        "COMMIT", "git:abc", null))), GENERATED_AT)
                .repositoryContext();

        assertTrue(projected.accounting().canonicalBytes() <= 32_768);
        assertTrue(projected.accounting().estimatedTokens() <= 8_192);
        assertTrue(projected.warnings().contains("AGENT_PROJECTION_PROFILE_DETAILS_REMOVED"));
        assertTrue(projected.warnings().contains("AGENT_PROJECTION_HUMAN_CONTEXT_INPUTS_COMPACTED")
                || projected.warnings().contains("AGENT_PROJECTION_PROJECT_CONTEXT_LISTS_REMOVED")
                || projected.warnings().contains("AGENT_PROJECTION_PROJECT_CONTEXT_MINIMAL"));
    }

    @Test
    void shouldRemoveOnlyTheExistingTailAsLastResort() {
        List<RepositoryEvidence> evidence = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> evidence(RepositoryContextLayer.COMMIT_DIFF,
                        "CHANGED_FILE", "diff:file-" + index,
                        "content-" + index + "-" + "x".repeat(500)))
                .toList();

        AgentRepositoryContext projected = service(2_600, 650).project(
                PROJECT_ID, projectContext(), context(evidence), GENERATED_AT)
                .repositoryContext();

        assertTrue(projected.accounting().removedEvidenceItems() > 0);
        assertEquals(projected.evidence().size(), projected.selectedCount());
        assertEquals(projected.selectedCount(), projected.selectedByLayer().values().stream()
                .mapToInt(Integer::intValue).sum());
        assertEquals(projected.selectedCount(), projected.selectedByKind().values().stream()
                .mapToInt(Integer::intValue).sum());
        assertEquals("diff:file-0", projected.evidence().getFirst().reference());
        assertFalse(projected.evidence().stream()
                .anyMatch(value -> value.reference().equals("diff:file-7")));
        assertTrue(projected.warnings().contains("AGENT_PROJECTION_EVIDENCE_REMOVED"));
    }

    @Test
    void shouldFailWhenOneUsableEvidenceCannotFit() {
        AgentContextProjectionService projectionService = service(10, 3);
        ProjectContextSnapshot projectContext = projectContext();
        RepositoryContext repositoryContext = context(List.of(evidence(
                RepositoryContextLayer.GIT_HISTORY, "COMMIT", "git:abc", null)));

        assertThrows(AgentContextProjectionException.class,
                () -> projectionService.project(
                        PROJECT_ID, projectContext, repositoryContext, GENERATED_AT));
    }

    @Test
    void shouldChangeProjectionDigestWhenSemanticEvidenceChanges() {
        AgentContextProjectionService service = service(100_000, 25_000);
        String first = service.project(PROJECT_ID, projectContext(),
                context(List.of(evidence(RepositoryContextLayer.GIT_HISTORY,
                        "COMMIT", "git:first", null))), GENERATED_AT)
                .repositoryContext().projectionDigest();
        String second = service.project(PROJECT_ID, projectContext(),
                context(List.of(evidence(RepositoryContextLayer.GIT_HISTORY,
                        "COMMIT", "git:second", null))), GENERATED_AT)
                .repositoryContext().projectionDigest();

        assertNotEquals(first, second);
    }

    @Test
    void shouldPreserveEvidenceWhenProjectContextIsOversized() {
        List<RepositoryEvidence> evidenceItems = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> evidence(RepositoryContextLayer.COMMIT_DIFF,
                        "CHANGED_FILE", "diff:file-" + index,
                        "content-" + index + "-" + "x".repeat(200)))
                .toList();

        AgentRepositoryContext projected = service(32_768, 8_192).project(
                PROJECT_ID, oversizedProjectContext(),
                context(evidenceItems), GENERATED_AT)
                .repositoryContext();

        assertTrue(projected.accounting().canonicalBytes() <= 32_768);
        assertTrue(projected.accounting().estimatedTokens() <= 8_192);
        assertTrue(projected.evidence().size() > 0,
                "At least one evidence item must survive when ProjectContext is reduced first");
        assertFalse(projected.warnings().contains("AGENT_PROJECTION_ALL_EVIDENCE_REMOVED"),
                "ALL_EVIDENCE_REMOVED must not be emitted when evidence survives");
        assertTrue(
                projected.warnings().contains("AGENT_PROJECTION_PROFILE_DETAILS_REMOVED")
                        || projected.warnings().contains("AGENT_PROJECTION_HUMAN_CONTEXT_INPUTS_COMPACTED")
                        || projected.warnings().contains("AGENT_PROJECTION_PROJECT_CONTEXT_LISTS_REMOVED")
                        || projected.warnings().contains("AGENT_PROJECTION_PROJECT_CONTEXT_MINIMAL"),
                "ProjectContext reduction warnings must be present");
    }

    @Test
    void shouldNotReduceContextThatAlreadyFits() {
        AgentContextProjectionService smallBudgetService = service(32_768, 8_192);
        RepositoryContext smallContext = context(List.of(
                evidence(RepositoryContextLayer.GIT_HISTORY, "COMMIT",
                        "git:small", null)));

        AgentEngineeringStoryContext result = smallBudgetService.project(
                PROJECT_ID, projectContext(), smallContext, GENERATED_AT);
        AgentRepositoryContext projected = result.repositoryContext();

        assertEquals(1, projected.evidence().size());
        assertFalse(projected.warnings().contains("AGENT_PROJECTION_EVIDENCE_REMOVED"));
        assertFalse(projected.warnings().contains("AGENT_PROJECTION_ALL_EVIDENCE_REMOVED"));
        assertFalse(projected.warnings().contains("AGENT_PROJECTION_PROFILE_DETAILS_REMOVED"));
        assertFalse(projected.warnings().contains("AGENT_PROJECTION_PROJECT_CONTEXT_LISTS_REMOVED"));
    }

    @Test
    void shouldRemoveEvidenceWhenPhysicallyImpossible() {
        List<RepositoryEvidence> hugeEvidence = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> evidence(RepositoryContextLayer.COMMIT_DIFF,
                        "CHANGED_FILE", "diff:huge-" + index,
                        "x".repeat(8_000)))
                .toList();

        assertThrows(AgentContextProjectionException.class,
                () -> service(800, 200).project(
                        PROJECT_ID, oversizedProjectContext(),
                        context(hugeEvidence), GENERATED_AT));
    }

    @Test
    void shouldProduceDeterministicOutputForIdenticalInput() {
        AgentContextProjectionService svc = service(32_768, 8_192);
        ProjectContextSnapshot snapshot = oversizedProjectContext();
        List<RepositoryEvidence> evidenceItems = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> evidence(RepositoryContextLayer.COMMIT_DIFF,
                        "CHANGED_FILE", "diff:det-" + index,
                        "content-" + "y".repeat(300)))
                .toList();
        RepositoryContext ctx = context(evidenceItems);

        AgentEngineeringStoryContext first = svc.project(
                PROJECT_ID, snapshot, ctx, GENERATED_AT);
        AgentEngineeringStoryContext second = svc.project(
                PROJECT_ID, snapshot, ctx, GENERATED_AT.plusSeconds(60));

        assertEquals(first.repositoryContext().projectionDigest(),
                second.repositoryContext().projectionDigest());
        assertEquals(first.repositoryContext().evidence().size(),
                second.repositoryContext().evidence().size());
        assertEquals(first.repositoryContext().warnings(),
                second.repositoryContext().warnings());
    }

    private AgentContextProjectionService service(int bytes, int tokens) {
        return new AgentContextProjectionService(objectMapper,
                new AgentContextProjectionPolicy(bytes, tokens, 3, 3));
    }

    private ProjectContextSnapshot projectContext() {
        return new ProjectContextSnapshot(null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private ProjectContextSnapshot oversizedProjectContext() {
        return new ProjectContextSnapshot(
                new com.hopeful117.devlogai.analysis.context.AnalysisContext.ProjectSnapshot(
                        PROJECT_ID, "Devlog AI", "devlog-ai", "d".repeat(8_000), null),
                new com.hopeful117.devlogai.profile.dto.ProjectProfileResponse(
                        UUID.randomUUID(), PROJECT_ID, UUID.randomUUID(), "v1", "v1",
                        GENERATED_AT, "main",
                        Map.of("repository", "r".repeat(12_000)),
                        new com.hopeful117.devlogai.profile.dto.ProjectProfileResponse.Completeness(
                                null, true, false, 0, 0, 1, 0, 0),
                        List.of(Map.of("summary", "s".repeat(20_000))),
                        "p".repeat(20_000),
                        List.of(Map.of("observation", "o".repeat(20_000))),
                        1),
                List.of(),
                List.of(new com.hopeful117.devlogai.analysis.context.AnalysisContext
                        .ValidatedProposalSnapshot(
                        UUID.randomUUID(), null, Map.of("payload", "x".repeat(12_000)),
                        GENERATED_AT, GENERATED_AT)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProjectContextSnapshot.HumanContextInputSnapshot(
                        UUID.randomUUID(), null, "Title " + "t".repeat(3_000),
                        "h".repeat(24_000), "ACTIVE", GENERATED_AT))
        );
    }

    private RepositoryContext context(List<RepositoryEvidence> evidence) {
        Map<RepositoryContextLayer, Integer> layers = new java.util.EnumMap<>(
                RepositoryContextLayer.class);
        evidence.forEach(value -> layers.merge(value.layer(), 1, Integer::sum));
        Map<String, Integer> kinds = new java.util.TreeMap<>();
        evidence.forEach(value -> kinds.merge(value.kind(), 1, Integer::sum));
        RepositoryContextDiagnostics diagnostics = new RepositoryContextDiagnostics(
                layers, kinds, kinds, List.of(), evidence.size() + 1, 1);
        List<RepositoryContext.SelectionDecision> decisions = new java.util.ArrayList<>();
        evidence.forEach(value -> decisions.add(new RepositoryContext.SelectionDecision(
                value.reference(), true, "SELECTED_BY_STRONG_RELEVANCE", 49, 20)));
        decisions.add(new RepositoryContext.SelectionDecision(
                "rejected:file", false, "TOKEN_BUDGET_EXCEEDED", 10, 20));
        return new RepositoryContext("repository-v1", ContextProfile.ENGINEERING_STORY,
                List.of("engineering-story-v1"), "plan-v1", List.of("verbose"),
                evidence, layers, diagnostics,
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                evidence.size() * 20, evidence.size() + 1, 1, true,
                decisions, List.of("REPOSITORY_CONTEXT_BUDGET_APPLIED"),
                "repository-digest");
    }

    private RepositoryEvidence evidence(
            RepositoryContextLayer layer,
            String kind,
            String reference,
            String text
    ) {
        RepositoryEvidenceContent content = text == null ? null
                : new RepositoryEvidenceContent(
                        RepositoryEvidenceContent.Status.TRUNCATED, text,
                        "CONTENT_TRUNCATED", "selected-file-content", "v1",
                        "resolved-abc", "selected-content-allocation", "v1", 1,
                        List.of("FINAL_SCORE=49", "SEMANTIC_MATCH_STRENGTH=7"));
        RepositoryEvidenceSymbols symbols = text == null ? null
                : new RepositoryEvidenceSymbols(
                        RepositoryEvidenceSymbols.Status.EXTRACTED, null,
                        "selected-java-symbols", "v1", "java-declarations", "v1",
                        "resolved-abc", 1, List.of("FINAL_SCORE=49"), false, 1, 1,
                        List.of(new RepositoryEvidenceSymbols.JavaDeclaration(
                                RepositoryEvidenceSymbols.Kind.CLASS, "App", "App",
                                List.of("public"), null, List.of(), List.of("Service"),
                                new RepositoryEvidenceSymbols.SourceLocation(1, 1, 3, 1))));
        return new RepositoryEvidence(layer, kind, reference,
                "Summary for " + reference, Instant.EPOCH,
                new EvidenceScore("multi-criteria-v2", Map.of(), Map.of(), 49,
                        List.of("SEMANTIC_RELEVANCE=70@15", "SEMANTIC_TERMS:app"),
                        new EvidenceScore.MatchStrength(7, 3)),
                List.of("related:one", "related:two", "related:three", "related:four"),
                new RepositoryEvidence.EvidenceProvenance(
                        "REPOSITORY_STRUCTURE", "source-id", "src/App.java", reference),
                Map.of("collectorId", "repository-structure", "collectorVersion", "v2",
                        "resolvedRevision", "resolved-abc"), 20,
                List.of("SEMANTIC_RELEVANCE=70@15", "SEMANTIC_TERMS:app",
                        "SEMANTIC_TERMS:app"), content, symbols);
    }

    private RepositoryEvidence oversizedSummaryEvidence(String reference) {
        return new RepositoryEvidence(
                RepositoryContextLayer.GIT_HISTORY,
                "COMMIT",
                reference,
                "Summary " + "x".repeat(500),
                Instant.EPOCH,
                new EvidenceScore("multi-criteria-v2", Map.of(), Map.of(), 49,
                        List.of("SEMANTIC_TERMS:app"),
                        new EvidenceScore.MatchStrength(7, 3)),
                List.of(),
                new RepositoryEvidence.EvidenceProvenance(
                        "GIT_HISTORY", "source-id", null, reference),
                Map.of("collectorId", "git-history", "collectorVersion", "v1",
                        "resolvedRevision", "resolved-abc"),
                20,
                List.of("SEMANTIC_TERMS:app"),
                null,
                null
        );
    }
}
