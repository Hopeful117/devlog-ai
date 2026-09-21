package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeterministicKnowledgeContextCollectorTest {

    @Test
    void keepsFactIdentitySeparateFromSupportingReferences() {
        UUID factId = UUID.randomUUID();
        String documentReference = "document:source:docs/decisions/ADR-063.md@abc123";
        AnalysisContext.FactSnapshot fact = new AnalysisContext.FactSnapshot(
                factId,
                FactType.OTHER,
                "The repository has source-aware evidence.",
                "docs/decisions/ADR-063.md",
                List.of(documentReference),
                Instant.parse("2026-09-21T10:00:00Z"));

        List<RepositoryEvidence> evidence = collect(List.of(fact), List.of());

        RepositoryEvidence item = evidence.getFirst();
        assertEquals(RepositoryContextLayer.ADR, item.layer());
        assertEquals("fact:" + factId, item.reference());
        assertEquals(List.of(documentReference), item.relatedReferences());
        assertEquals("docs/decisions/ADR-063.md", item.provenance().repositoryLocation());
        assertEquals(factId.toString(), item.provenance().identifier());
    }

    @Test
    void keepsObservationIdentitySeparateFromSupportingFactIds() {
        UUID observationId = UUID.randomUUID();
        UUID supportingFactId = UUID.randomUUID();
        AnalysisContext.ObservationSnapshot observation = new AnalysisContext.ObservationSnapshot(
                observationId,
                ObservationType.OTHER,
                "A repeated architecture pattern was observed.",
                "pattern-rule",
                "v1",
                List.of(supportingFactId),
                Instant.parse("2026-09-21T10:00:00Z"));

        List<RepositoryEvidence> evidence = collect(List.of(), List.of(observation));

        RepositoryEvidence item = evidence.getFirst();
        assertEquals("observation:" + observationId, item.reference());
        assertEquals(List.of("fact:" + supportingFactId), item.relatedReferences());
        assertEquals(observationId.toString(), item.provenance().identifier());
        assertTrue(item.provenance().repositoryLocation() == null);
    }

    private List<RepositoryEvidence> collect(
            List<AnalysisContext.FactSnapshot> facts,
            List<AnalysisContext.ObservationSnapshot> observations
    ) {
        AnalysisContext analysisContext = mock(AnalysisContext.class);
        when(analysisContext.facts()).thenReturn(facts);
        when(analysisContext.observations()).thenReturn(observations);

        ContextRequest request = mock(ContextRequest.class);
        when(request.analysisContext()).thenReturn(analysisContext);
        when(request.budget()).thenReturn(new RepositoryContext.ContextBudget(
                20, 1000, 10, 2000));

        return new DeterministicKnowledgeContextCollector(new EvidenceFactory())
                .collect(request);
    }
}
