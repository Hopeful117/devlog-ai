package com.hopeful117.devlogai.storycontextanalysis.investigation;

import com.hopeful117.devlogai.projectcontext.ProjectContextProvider;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.RepositoryContextAdapter;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigationCapabilityExecutionServiceTest {
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID STORY_ID = UUID.randomUUID();
    private static final String REVISION = "revision-1";
    private static final InvestigationScope SCOPE =
            new InvestigationScope("devlog", REVISION, List.of("backend", "docs"));
    private static final InvestigationBudget BUDGET =
            new InvestigationBudget(4, 3, 10, 100_000, 10_000, 60_000, 4_000);

    @Mock ProjectContextProvider projectContextProvider;
    @Mock RepositoryContextAdapter repositoryContextAdapter;
    @Mock ProjectContextSnapshot snapshot;

    private InvestigationCapabilityExecutionService service;

    @BeforeEach
    void setUp() {
        service = new InvestigationCapabilityExecutionService(
                projectContextProvider, repositoryContextAdapter);
    }

    @Test
    void searchReturnsBoundedCanonicalMetadata() {
        var action = search("action-1", 2);
        when(projectContextProvider.build(PROJECT_ID)).thenReturn(snapshot);
        when(repositoryContextAdapter.buildRepositoryContext(eq(PROJECT_ID), eq(action.purpose()),
                eq(snapshot), any(), eq(STORY_ID), eq(action.query())))
                .thenReturn(context(evidence("backend/Main.java", REVISION, "match")));

        var result = service.execute(PROJECT_ID, STORY_ID, action, SCOPE, BUDGET,
                InvestigationUsage.empty(), Set.of());

        assertThat(result.outcome()).isEqualTo(InvestigationCapabilityResult.Outcome.RESULTS);
        assertThat(result.evidence()).singleElement().satisfies(value -> {
            assertThat(value.path()).isEqualTo("backend/Main.java");
            assertThat(value.revision()).isEqualTo(REVISION);
            assertThat(value.content()).isNull();
            assertThat(value.citable()).isTrue();
        });
    }

    @Test
    void readReturnsOnlyRequestedLineRange() {
        var action = new ReadEvidenceRangeRequest("action-2", "devlog", REVISION,
                "inspect source", "backend/Main.java", 2, 3, 1, 10_000, 2_000);
        when(projectContextProvider.build(PROJECT_ID)).thenReturn(snapshot);
        when(repositoryContextAdapter.buildRepositoryContext(eq(PROJECT_ID), eq(action.purpose()),
                eq(snapshot), eq(List.of(action.path())), eq(STORY_ID), eq((String) null)))
                .thenReturn(context(evidence("backend/Main.java", REVISION, "one\ntwo\nthree\nfour")));

        var result = service.execute(PROJECT_ID, STORY_ID, action, SCOPE, BUDGET,
                InvestigationUsage.empty(), Set.of());

        assertThat(result.evidence()).singleElement().satisfies(value -> {
            assertThat(value.content()).isEqualTo("two\nthree");
            assertThat(value.startLine()).isEqualTo(2);
            assertThat(value.endLine()).isEqualTo(3);
        });
    }

    @Test
    void rejectsRevisionMismatchBeforeRepositoryAccess() {
        var action = search("action-3", 1);
        var result = service.execute(PROJECT_ID, STORY_ID, action,
                new InvestigationScope("devlog", "revision-2", List.of("backend")), BUDGET,
                InvestigationUsage.empty(), Set.of());

        assertThat(result.outcome()).isEqualTo(InvestigationCapabilityResult.Outcome.REJECTED);
        assertThat(result.rejectionCode()).isEqualTo("REVISION_MISMATCH");
        verifyNoInteractions(projectContextProvider, repositoryContextAdapter);
    }

    @Test
    void emptyResultsAreExplicitAndDoNotInventEvidence() {
        var action = search("action-4", 1);
        when(projectContextProvider.build(PROJECT_ID)).thenReturn(snapshot);
        when(repositoryContextAdapter.buildRepositoryContext(any(), any(), any(), any(), any(), any()))
                .thenReturn(context());

        var result = service.execute(PROJECT_ID, STORY_ID, action, SCOPE, BUDGET,
                InvestigationUsage.empty(), Set.of());

        assertThat(result.outcome()).isEqualTo(InvestigationCapabilityResult.Outcome.EMPTY);
        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void duplicateEvidenceIsDeduplicatedAndResultLimitIsApplied() {
        var action = search("action-5", 1);
        when(projectContextProvider.build(PROJECT_ID)).thenReturn(snapshot);
        var item = evidence("backend/Main.java", REVISION, "match");
        when(repositoryContextAdapter.buildRepositoryContext(any(), any(), any(), any(), any(), any()))
                .thenReturn(context(item, item, evidence("backend/Other.java", REVISION, "match")));

        var result = service.execute(PROJECT_ID, STORY_ID, action, SCOPE, BUDGET,
                InvestigationUsage.empty(), Set.of());

        assertThat(result.evidence()).hasSize(1);
        verify(repositoryContextAdapter).buildRepositoryContext(any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchBoundsTheAggregateEvidencePayloadNotEachItemIndependently() {
        var action = new SearchEvidenceRequest("action-6", "devlog", REVISION,
                "find source", "match", "backend", 10, 10, 10);
        when(projectContextProvider.build(PROJECT_ID)).thenReturn(snapshot);
        when(repositoryContextAdapter.buildRepositoryContext(any(), any(), any(), any(), any(), any()))
                .thenReturn(context(evidence("backend/One.java", REVISION, "match"),
                        evidence("backend/Two.java", REVISION, "match")));

        var result = service.execute(PROJECT_ID, STORY_ID, action, SCOPE, BUDGET,
                InvestigationUsage.empty(), Set.of());

        long bytes = result.evidence().stream()
                .mapToLong(value -> value.summary().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + (value.content() == null ? 0 : value.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length))
                .sum();
        assertThat(bytes).isLessThanOrEqualTo(10);
    }

    private static SearchEvidenceRequest search(String id, int resultLimit) {
        return new SearchEvidenceRequest(id, "devlog", REVISION, "find source", "match",
                "backend", resultLimit, 10_000, 2_000);
    }

    private static RepositoryContext context(RepositoryEvidence... evidence) {
        return new RepositoryContext("v1", null, List.of(), "v1", List.of(), List.of(evidence),
                Map.of(), new RepositoryContext.ContextBudget(10, 2_000, 10, 10_000),
                0, 0, 0, false, List.of(), List.of(), "digest");
    }

    private static RepositoryEvidence evidence(String path, String revision, String text) {
        return new RepositoryEvidence(RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                "file:" + path, "summary", Instant.EPOCH,
                EvidenceScore.unscored(), List.of(),
                new RepositoryEvidence.EvidenceProvenance("GIT", "source", path, path),
                Map.of("resolvedRevision", revision), 1, List.of(),
                new com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent(
                        com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent.Status.COMPLETE,
                        text, null, null, null, revision));
    }
}
