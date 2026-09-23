package com.hopeful117.devlogai.evidence.retrieval;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistoryMatch;
import com.hopeful117.devlogai.history.service.ProjectHistoryQueryMatch;
import com.hopeful117.devlogai.history.service.ProjectHistoryQueryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitCommitRetrievalAdapterTest {
    private static final UUID PROJECT_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID SOURCE_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");

    private final ProjectHistoryQueryService history = mock(ProjectHistoryQueryService.class);
    private final GitCommitRetrievalAdapter adapter = new GitCommitRetrievalAdapter(history);

    @Test
    void mapsDeterministicHistoryMatchesAndPaginatesWithoutResolution() {
        ProjectHistoryQueryMatch first = match("a".repeat(40), "Add retrieval", 1);
        ProjectHistoryQueryMatch second = match("b".repeat(40), "Add adapter", 2);
        ProjectHistoryQueryMatch third = match("c".repeat(40), "Add tests", 3);
        when(history.findMatches(PROJECT_ID, SOURCE_ID, "retrieval"))
                .thenReturn(List.of(first, second, third));

        EvidencePage firstPage = adapter.retrieve(query(0, 2));
        assertThat(firstPage.candidates()).extracting(EvidenceCandidate::canonicalReference)
                .containsExactly(
                        "git:" + SOURCE_ID + ":" + first.commitSha(),
                        "git:" + SOURCE_ID + ":" + second.commitSha());
        assertThat(firstPage.hasNext()).isTrue();

        EvidencePage page = adapter.retrieve(query(1, 2));

        assertThat(page.candidates()).extracting(EvidenceCandidate::canonicalReference)
                .containsExactly("git:" + SOURCE_ID + ":" + third.commitSha());
        assertThat(page.totalMatches()).isEqualTo(3);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.candidates().getFirst().projectId()).isEqualTo(PROJECT_ID);
        assertThat(page.candidates().getFirst().sourceId()).isEqualTo(SOURCE_ID);
        assertThat(page.candidates().getFirst().summary()).isEqualTo("Add tests");
        assertThat(page.candidates().getFirst().provenance().authority())
                .isEqualTo("PROJECT_HISTORY");
        assertThat(page.candidates().getFirst().trustTier())
                .isEqualTo(TrustTier.TECHNICAL_EVIDENCE);
        verify(history, times(2)).findMatches(PROJECT_ID, SOURCE_ID, "retrieval");
    }

    @Test
    void returnsEmptyPageAfterTheLastMatch() {
        when(history.findMatches(PROJECT_ID, null, "missing"))
                .thenReturn(List.of(match("a".repeat(40), "Only result", 1)));

        EvidencePage page = adapter.retrieve(new EvidenceRetrievalQuery(
                new EvidenceRetrievalScope(PROJECT_ID, null),
                EvidenceRetrievalFamily.GIT_COMMIT, "missing", 1, 1));

        assertThat(page.candidates()).isEmpty();
        assertThat(page.totalMatches()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
    }

    private EvidenceRetrievalQuery query(int page, int pageSize) {
        return new EvidenceRetrievalQuery(
                new EvidenceRetrievalScope(PROJECT_ID, SOURCE_ID),
                EvidenceRetrievalFamily.GIT_COMMIT, "retrieval", page, pageSize);
    }

    private ProjectHistoryQueryMatch match(String sha, String subject, long second) {
        return new ProjectHistoryQueryMatch(
                sha, subject, "author", Instant.ofEpochSecond(second), SOURCE_ID, 10,
                List.<ProjectHistoryMatch>of());
    }
}
