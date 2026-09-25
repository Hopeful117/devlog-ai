package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalEngineeringContextTest {
    @Test
    void authorizedReferencesMustBeRepositoryEvidence() {
        RepositoryContext repository = mock(RepositoryContext.class);
        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidence.reference()).thenReturn("document://allowed");
        when(repository.evidence()).thenReturn(List.of(evidence));
        EngineeringContext projection = new EngineeringContext(null, "intent", List.of(), null, List.of(), null);

        assertThrows(IllegalArgumentException.class, () -> new CanonicalEngineeringContext(
                projection, repository, "ignored", "v1", null, null, null, null,
                List.of(new EvidenceRef("document://not-allowed", "document://not-allowed"))));
    }

    @Test
    void projectionChangesDoNotChangeCanonicalDigest() {
        RepositoryContext repository = mock(RepositoryContext.class);
        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidence.reference()).thenReturn("document://allowed");
        when(repository.evidence()).thenReturn(List.of(evidence));
        when(repository.contextVersion()).thenReturn("repository-v1");
        when(repository.contextDigest()).thenReturn("repository-digest");
        EngineeringContext first = new EngineeringContext(null, "intent-a", List.of(), null, List.of(), null);
        EngineeringContext second = new EngineeringContext(null, "intent-b", List.of(), null, List.of(), null);
        List<EvidenceRef> authorized = List.of(new EvidenceRef("document://allowed", "document://allowed"));

        CanonicalEngineeringContext left = new CanonicalEngineeringContext(
                first, repository, "ignored", "v1", null, Map.of(), Map.of(), null,
                Map.of(), Map.of(), Map.of(), authorized);
        CanonicalEngineeringContext right = new CanonicalEngineeringContext(
                second, repository, "ignored", "v1", null, Map.of(), Map.of(), null,
                Map.of(), Map.of(), Map.of(), authorized);

        assertEquals(left.contextDigest(), right.contextDigest());
    }
}
