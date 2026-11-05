package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.ContextRequestEcho;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextDiagnostics;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextLossLedgerTest {
    @Test
    void capturesBoundedEmptyContextWithoutChangingProviderContract() {
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                org.mockito.Mockito.mock(EngineeringContext.class), null, "a".repeat(64),
                "engineering-context-v2", null,
                Map.of("sourceRevision", Map.of("revision", "revision-1")), Map.of(),
                null, Map.of(), Map.of(), Map.of(), List.of());

        ContextLossLedger ledger = ContextLossLedger.capture(
                canonical,
                Map.of("context", Map.of("repositoryEvidence", List.of())),
                null,
                new ObjectMapper());

        assertEquals(ContextLossLedger.VERSION, ledger.version());
        assertEquals(256, ledger.maximumDetailEntries());
        assertEquals(0, ledger.counts().candidateCount());
        assertEquals(0, ledger.counts().providerVisibleCount());
        assertEquals(64, ledger.digest().length());
        assertNotNull(ledger.budgets());
    }

    @Test
    void correlatesSelectionAndProjectionByCanonicalReference() {
        String selectedReference = "file:src/App.java@revision-1";
        RepositoryEvidence selected = new RepositoryEvidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE", selectedReference,
                "App source", null, EvidenceScore.unscored(), List.of(),
                new RepositoryEvidence.EvidenceProvenance("GIT", "source", "src/App.java", "App"),
                 Map.of("resolvedRevision", "revision-1"), 12, List.of())
                .withContent(new com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent(
                        com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent.Status.COMPLETE,
                        "answer-critical content", null, "policy", "v1", "revision-1"));
        RepositoryContext context = new RepositoryContext(
                "repository-context-engine-v1", ContextProfile.ARCHITECTURE_REVIEW,
                List.of("architecture-v1"), "context-intelligence-v2", List.of(),
                List.of(selected), Map.of(RepositoryContextLayer.RELATED_SOURCE_CODE, 1),
                RepositoryContextDiagnostics.empty(),
                new RepositoryContext.ContextBudget(2, 500, 20, 100), 12, 2, 1,
                 true, List.of(
                         new RepositoryContext.SelectionDecision(selectedReference, true,
                                 "SELECTED_BY_RANK", 10, 12),
                         new RepositoryContext.SelectionDecision("file:missing", false,
                                 "TOKEN_BUDGET_EXCEEDED", 1, 8)), List.of(), "c".repeat(64),
                        List.of(
                          new RepositoryContext.CandidateMetadata(selectedReference, "SOURCE_FILE",
                                  "src/App.java", 10, "GIT", List.of()),
                          new RepositoryContext.CandidateMetadata("file:missing", "SOURCE_FILE",
                                  "src/Missing.java", 1, "GIT", List.of())));
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                org.mockito.Mockito.mock(EngineeringContext.class), context,
                 "a".repeat(64), "engineering-context-v2",
                 new ContextRequestEcho("test", "inspect", List.of("src/App.java"), null),
                 Map.of(), Map.of(), null, Map.of(),
                 Map.of(selectedReference, selected.provenance()),
                Map.of(selectedReference, "TECHNICAL_EVIDENCE"),
                List.of(new EvidenceRef(selectedReference, "source")));

        Map<String, Object> projection = new java.util.LinkedHashMap<>();
        projection.put("context", Map.of("repositoryEvidence", List.of(
                Map.of("reference", selectedReference,
                        "content", java.util.Collections.singletonMap("text", null)))));
        ContextLossLedger ledger = ContextLossLedger.capture(
                canonical, projection, null, new ObjectMapper());

        assertEquals(2, ledger.counts().candidateCount());
        assertEquals(1, ledger.counts().projectedCount());
        assertEquals(1, ledger.lossReasons().get("SELECTION_TOKEN_BUDGET_EXCEEDED"));
        assertEquals("PROJECTION_CONTENT_COMPACTED", ledger.entries().stream()
                .filter(entry -> selectedReference.equals(entry.reference()))
                .findFirst().orElseThrow().lossReason());
        assertTrue(ledger.entries().stream().anyMatch(entry ->
                "file:missing".equals(entry.reference()) && !entry.selected()));
        assertEquals(1, ledger.evaluationTelemetry().stageCounts().get("SOURCE_FILE:SELECTED"));
        assertEquals(1, ledger.evaluationTelemetry().trustCounts().get("TECHNICAL_EVIDENCE:PROJECTED"));
        assertEquals(1, ledger.evaluationTelemetry().trustCounts().get("TECHNICAL_EVIDENCE:REJECTED"));
        assertEquals(2, ledger.evaluationTelemetry().candidateFixture().candidates().size());
        assertTrue(ledger.evaluationTelemetry().candidateFixture().candidates().stream()
                .allMatch(row -> "TECHNICAL_EVIDENCE".equals(row.trust())));
        assertEquals("TOKEN_BUDGET_EXCEEDED", ledger.evaluationTelemetry().candidateFixture()
                .candidates().stream().filter(row -> "file:missing".equals(row.reference()))
                .findFirst().orElseThrow().reason());
        ContextLossLedger.RequestedFileOutcome requested = ledger.evaluationTelemetry()
                .requestedFileOutcomes().getFirst();
        assertEquals("src/App.java", requested.path());
        assertTrue(requested.found());
        assertTrue(requested.selected());
        assertTrue(requested.projected());
        assertTrue(requested.grounded());

        RepositoryContext changedContext = new RepositoryContext(
                context.contextVersion(), context.profile(), context.activeProfileKeys(),
                context.contextPlanVersion(), context.contextIntelligenceExplanations(), context.evidence(),
                context.selectedByLayer(), context.diagnostics(), context.budget(), context.usedTokens(),
                context.candidateCount(), context.discardedCount(), context.truncated(),
                context.selectionDecisions(), context.warnings(), context.contextDigest(),
                List.of(new RepositoryContext.CandidateMetadata(selectedReference, "INSIGHT",
                                "src/App.java", 10, "CORE_KNOWLEDGE", List.of()),
                        new RepositoryContext.CandidateMetadata("file:missing", "SOURCE_FILE",
                                "src/Missing.java", 1, "GIT", List.of())));
        CanonicalEngineeringContext changedCanonical = new CanonicalEngineeringContext(
                canonical.projection(), changedContext, canonical.contextDigest(), canonical.contextVersion(),
                canonical.requestEcho(), canonical.freshness(), canonical.accounting(), canonical.diagnostics(),
                canonical.relationsByReference(), canonical.provenanceByReference(), canonical.trustByReference(),
                canonical.authorizedReferences());
        ContextLossLedger changedLedger = ContextLossLedger.capture(
                changedCanonical, projection, null, new ObjectMapper());
        assertNotEquals(ledger.evaluationTelemetry().candidateFixture().digest(),
                changedLedger.evaluationTelemetry().candidateFixture().digest());
    }

    @Test
    void classifiesEveryTrustStateForSelectedAndRejectedFixtureCandidates() {
        List<RepositoryContext.CandidateMetadata> candidates = List.of(
                candidate("trusted-selected", "INSIGHT", "CORE_KNOWLEDGE"),
                candidate("trusted-rejected", "INSIGHT", "CORE_KNOWLEDGE"),
                candidate("human-selected", "ENGINEERING_STORY", "DOCUMENTATION"),
                candidate("human-rejected", "ENGINEERING_STORY", "DOCUMENTATION"),
                candidate("system-selected", "ANALYSIS", "ANALYSIS"),
                candidate("system-rejected", "ANALYSIS", "ANALYSIS"),
                candidate("technical-selected", "SOURCE_FILE", "GIT"),
                candidate("technical-rejected", "SOURCE_FILE", "GIT"),
                candidate("unvalidated-selected", "VALIDATABLE_PROPOSAL", "CORE_ANALYSIS"),
                candidate("unvalidated-rejected", "VALIDATABLE_PROPOSAL", "CORE_ANALYSIS"),
                candidate("transient-selected", "DECISION", "AI_ENGINE"),
                candidate("transient-rejected", "DECISION", "AI_ENGINE"),
                candidate("unknown-selected", "MODULE", "OTHER"),
                candidate("unknown-rejected", "MODULE", "OTHER"));
        List<RepositoryContext.SelectionDecision> decisions = candidates.stream()
                .map(candidate -> new RepositoryContext.SelectionDecision(
                        candidate.reference(), candidate.reference().endsWith("selected"),
                        candidate.reference().endsWith("selected") ? "SELECTED_BY_RANK"
                                : "CATEGORY_CONCENTRATION_LIMIT", 1, 1))
                .toList();
        RepositoryContext context = new RepositoryContext(
                "repository-context-engine-v1", ContextProfile.ARCHITECTURE_REVIEW,
                List.of("architecture-v1"), "context-intelligence-v2", List.of(), List.of(),
                Map.of(), RepositoryContextDiagnostics.empty(),
                new RepositoryContext.ContextBudget(20, 500, 20, 100), 14, 14, 0, false,
                decisions, List.of(), "c".repeat(64), candidates);
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                org.mockito.Mockito.mock(EngineeringContext.class), context, "a".repeat(64),
                "engineering-context-v2", null,
                Map.of("sourceRevision", Map.of("revision", "revision-1")), Map.of(), null, Map.of(), Map.of(),
                Map.of(), List.of());

        ContextLossLedger ledger = ContextLossLedger.capture(
                canonical, Map.of("context", Map.of("repositoryEvidence", List.of())),
                null, new ObjectMapper());

        Map<String, Long> trustRows = ledger.evaluationTelemetry().candidateFixture().candidates()
                .stream().collect(java.util.stream.Collectors.groupingBy(
                        ContextLossLedger.CandidateRow::trust, java.util.stream.Collectors.counting()));
        Map<String, Integer> trustCounts = ledger.evaluationTelemetry().trustCounts();
        for (String trust : List.of("TRUSTED", "HUMAN_AUTHORED", "SYSTEM_METADATA",
                "TECHNICAL_EVIDENCE", "UNVALIDATED", "TRANSIENT_AI", "UNKNOWN")) {
            assertEquals(2L, trustRows.get(trust));
            assertEquals(2, trustCounts.get(trust + ":CANDIDATE"));
            assertEquals(1, trustCounts.get(trust + ":SELECTED"));
            assertEquals(1, trustCounts.get(trust + ":REJECTED"));
        }
        Map<String, Integer> stageCounts = ledger.evaluationTelemetry().stageCounts();
        for (String kind : List.of("INSIGHT", "ENGINEERING_STORY", "ANALYSIS", "SOURCE_FILE",
                "VALIDATABLE_PROPOSAL", "DECISION", "MODULE")) {
            assertEquals(2, stageCounts.get(kind + ":CANDIDATE"));
            assertEquals(1, stageCounts.get(kind + ":SELECTED"));
            assertEquals(1, stageCounts.get(kind + ":REJECTED"));
        }
        assertEquals(7, ledger.evaluationTelemetry().candidateFixture().candidates().stream()
                .filter(ContextLossLedger.CandidateRow::selected).count());
        assertEquals(7, ledger.evaluationTelemetry().candidateFixture().candidates().stream()
                .filter(row -> !row.selected()).count());
    }

    private static RepositoryContext.CandidateMetadata candidate(
            String reference, String kind, String sourceType) {
        return new RepositoryContext.CandidateMetadata(reference, kind, "src/" + reference,
                10, sourceType, List.of());
    }

    @Test
    void acceptsCoreCandidateKindsAndSelectionReasonsInFixtureRows() {
        List<String> coreKinds = List.of("ENGINEERING_EVENT", "ARTIFACT", "MILESTONE",
                "OBSERVATION", "STORY_DOCUMENT", "ADR_DOCUMENT", "ROADMAP_DOCUMENT",
                "PROJECT_NOTE", "FRESHNESS", "DIAGNOSTIC", "SELECTION_METADATA");
        List<String> reasons = List.of("DUPLICATE_REFERENCE", "NOT_SELECTED",
                "SELECTED_BY_STRONG_RELEVANCE");

        for (String kind : coreKinds) {
            for (String reason : reasons) {
                assertDoesNotThrow(() -> new ContextLossLedger.CandidateRow(
                        "candidate:" + kind + ":" + reason, kind, null, 1,
                        "UNKNOWN", false, !"NOT_SELECTED".equals(reason), false, reason));
            }
        }
    }

    @Test
    void capturesProjectionAccountingAndKeepsProviderVisibilityUnknownBeforeCallback() {
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                org.mockito.Mockito.mock(EngineeringContext.class), null, "a".repeat(64),
                "engineering-context-v2", null, Map.of(), Map.of(), null, Map.of(), Map.of(),
                Map.of(), List.of());

        ContextLossLedger ledger = ContextLossLedger.capture(canonical, Map.of(
                "projectionDigest", "b".repeat(64),
                "accounting", Map.of("canonicalBytes", 120, "estimatedTokens", 30,
                        "removedEvidenceItems", 2),
                "context", Map.of("repositoryEvidence", List.of(), "warnings", List.of("compact"))),
                null, new ObjectMapper());

        assertEquals("MEASURED", ledger.projection().get("status"));
        assertEquals(2, ledger.projection().get("removedEvidenceItems"));
        assertEquals("NOT_SUBMITTED", ledger.providerOutcome().get("status"));
        assertEquals(0, ledger.counts().providerVisibleCount());
    }

    @Test
    void updatesProviderOutcomeWithTraceLinkageAndNewDigest() {
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                org.mockito.Mockito.mock(EngineeringContext.class), null, "a".repeat(64),
                "engineering-context-v2", null, Map.of(), Map.of(), null, Map.of(), Map.of(),
                Map.of(), List.of());
        ObjectMapper mapper = new ObjectMapper();
        ContextLossLedger ledger = ContextLossLedger.capture(canonical,
                Map.of("context", Map.of("repositoryEvidence", List.of())), null, mapper);

        ContextLossLedger updated = ledger.withProviderOutcome("INCOMPLETE", 3,
                Map.of("traceCount", 1, "incompleteReasons", List.of("timeout")), mapper);

        assertEquals("INCOMPLETE", updated.providerOutcome().get("status"));
        assertEquals(1, updated.providerOutcome().get("traceCount"));
        assertEquals(3, updated.counts().providerVisibleCount());
        assertTrue(!ledger.digest().equals(updated.digest()));
    }
}
