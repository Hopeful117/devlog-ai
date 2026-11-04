package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                                "TOKEN_BUDGET_EXCEEDED", 1, 8)), List.of(), "c".repeat(64));
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                org.mockito.Mockito.mock(EngineeringContext.class), context,
                "a".repeat(64), "engineering-context-v2", null, Map.of(),
                Map.of(), null, Map.of(),
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
