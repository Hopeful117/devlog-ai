package com.hopeful117.devlogai.storybriefing;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hopeful117.devlogai.ai.reference.AiReference;
import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import com.hopeful117.devlogai.engineeringcontext.CanonicalContextDigest;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextDiagnostics;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryChangeBriefingTest {
    @Test
    void exposesTypedRepositoryEvidenceAndNonCausalStatus() {
        var reference = new AiReference(AiReferenceType.REPOSITORY_EVIDENCE,
                "git:source:0123456789012345678901234567890123456789",
                AiReferenceScope.REPOSITORY);
        var briefing = new StoryChangeBriefing("story-change-briefing-v1", UUID.randomUUID(),
                "context-digest", "projection-digest", "NOT_ESTABLISHED", "observed",
                List.of(new StoryChangeBriefing.Change(reference, "COMMIT", "summary", null, null)),
                List.of(), null);
        assertEquals(AiReferenceType.REPOSITORY_EVIDENCE, briefing.changes().getFirst().reference().type());
        assertEquals(AiReferenceScope.REPOSITORY, briefing.changes().getFirst().reference().scope());
    }

    @Test
    void rejectsCausalStatus() {
        assertThrows(IllegalArgumentException.class, () -> new StoryChangeBriefing(
                "story-change-briefing-v1", UUID.randomUUID(), "context", "projection",
                "ESTABLISHED", "causal", List.of(), List.of(), null));
    }

    @Test
    void preservesCanonicalAuditMetadataAndUsesFullProjectionDigest() {
        UUID storyId = UUID.randomUUID();
        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidence.reference()).thenReturn("git:source:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        when(evidence.kind()).thenReturn("COMMIT");
        when(evidence.summary()).thenReturn("observed change");
        when(evidence.relatedReferences()).thenReturn(List.of());
        when(evidence.extractionMetadata()).thenReturn(Map.of(
                "baseCommit", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "targetCommit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        RepositoryContext repository = mock(RepositoryContext.class);
        when(repository.evidence()).thenReturn(List.of(evidence));
        when(repository.warnings()).thenReturn(List.of());
        CanonicalEngineeringContext canonical = mock(CanonicalEngineeringContext.class);
        when(canonical.contextDigest()).thenReturn("context-digest");
        when(canonical.contextVersion()).thenReturn("canonical-v1");
        when(canonical.repositoryContext()).thenReturn(repository);
        when(canonical.freshness()).thenReturn(Map.of("state", "FRESH"));
        when(canonical.accounting()).thenReturn(Map.of("selectedCount", 1));
        when(canonical.diagnostics()).thenReturn(RepositoryContextDiagnostics.empty());
        when(canonical.provenanceByReference()).thenReturn(Map.of());
        when(canonical.trustByReference()).thenReturn(Map.of(
                "git:source:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "TECHNICAL_EVIDENCE"));
        when(canonical.authorizedReferences()).thenReturn(List.of());
        EngineeringContextFacade facade = mock(EngineeringContextFacade.class);
        when(facade.getCanonicalEngineeringContext("demo", "brief", List.of(), storyId))
                .thenReturn(canonical);

        var briefing = new StoryChangeBriefingServiceImpl(facade).build("demo", storyId, "brief");

        assertEquals("(base,target]", briefing.snapshot().get("changeWindow") instanceof Map<?, ?> window
                ? window.get("range") : null);
        assertEquals(Map.of("state", "FRESH"), briefing.snapshot().get("freshness"));
        assertTrue(briefing.snapshot().containsKey("diagnostics"));
        assertTrue(briefing.snapshot().containsKey("projectionPolicy"));
        assertEquals(CanonicalContextDigest.calculate(Map.of(
                "schema", "adr-069-story-change-briefing-projection-v1",
                "contractVersion", "story-change-briefing-v1", "storyId", storyId,
                "contextDigest", "context-digest", "groundingStatus", "NOT_ESTABLISHED",
                "description", briefing.description(), "changes", briefing.changes(),
                "warnings", briefing.warnings(), "snapshot", briefing.snapshot())),
                briefing.projectionDigest());
    }

    @Test
    void emitsExplicitFailClosedWarningCodesWhenScopeOrBoundsAreUnavailable() {
        CanonicalEngineeringContext canonical = mock(CanonicalEngineeringContext.class);
        when(canonical.contextDigest()).thenReturn("context-digest");
        when(canonical.contextVersion()).thenReturn("canonical-v1");
        when(canonical.repositoryContext()).thenReturn(null);
        when(canonical.freshness()).thenReturn(Map.of());
        when(canonical.accounting()).thenReturn(Map.of());
        when(canonical.diagnostics()).thenReturn(RepositoryContextDiagnostics.empty());
        when(canonical.provenanceByReference()).thenReturn(Map.of());
        when(canonical.trustByReference()).thenReturn(Map.of());
        when(canonical.authorizedReferences()).thenReturn(List.of());
        EngineeringContextFacade facade = mock(EngineeringContextFacade.class);
        UUID storyId = UUID.randomUUID();
        when(facade.getCanonicalEngineeringContext("demo", "brief", List.of(), storyId))
                .thenReturn(canonical);

        var briefing = new StoryChangeBriefingServiceImpl(facade).build("demo", storyId, "brief");

        assertTrue(briefing.warnings().contains("STORY_SCOPE_UNAVAILABLE"));
        assertTrue(briefing.changes().isEmpty());
    }
}
