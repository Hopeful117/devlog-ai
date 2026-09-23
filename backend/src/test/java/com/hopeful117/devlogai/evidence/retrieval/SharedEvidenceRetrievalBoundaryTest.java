package com.hopeful117.devlogai.evidence.retrieval;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedEvidenceRetrievalBoundaryTest {
    private static final UUID PROJECT_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID SOURCE_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final String REFERENCE =
            "git:22222222-2222-2222-2222-222222222222:abcdef1234567";

    @Test
    void delegatesValidGitRetrievalWithoutResolution() {
        EvidencePage expected = page(candidate());
        AtomicBoolean called = new AtomicBoolean();
        GitCommitRetrievalPort port = query -> {
            called.set(true);
            assertThat(query.scope().projectId()).isEqualTo(PROJECT_ID);
            assertThat(query.scope().sourceId()).isEqualTo(SOURCE_ID);
            return expected;
        };

        EvidencePage result = boundary(port).retrieve(query(0, 20));

        assertThat(called).isTrue();
        assertThat(result).isSameAs(expected);
    }

    @Test
    void requiresProjectScope() {
        assertCode(new EvidenceRetrievalQuery(
                        null, EvidenceRetrievalFamily.GIT_COMMIT, "resolver", 0, 20),
                EvidenceRetrievalFailureCode.INVALID_SCOPE);
        assertCode(new EvidenceRetrievalQuery(
                        new EvidenceRetrievalScope(null, SOURCE_ID),
                        EvidenceRetrievalFamily.GIT_COMMIT, "resolver", 0, 20),
                EvidenceRetrievalFailureCode.INVALID_SCOPE);
    }

    @Test
    void allowsAnExplicitOptionalSourceScope() {
        EvidenceRetrievalQuery query = new EvidenceRetrievalQuery(
                new EvidenceRetrievalScope(PROJECT_ID, null),
                EvidenceRetrievalFamily.GIT_COMMIT, "resolver", 0, 20);

        assertThat(boundary(value -> {
            assertThat(value.scope().sourceId()).isNull();
            return page(candidate());
        }).retrieve(query))
                .isNotNull();
    }

    @Test
    void supportsOnlyGitCommitInSlice0() {
        EvidenceRetrievalQuery query = new EvidenceRetrievalQuery(
                new EvidenceRetrievalScope(PROJECT_ID, null),
                null, "resolver", 0, 20);

        assertCode(query, EvidenceRetrievalFailureCode.UNSUPPORTED_FAMILY);
    }

    @Test
    void rejectsInvalidQueries() {
        assertCode(new EvidenceRetrievalQuery(
                        new EvidenceRetrievalScope(PROJECT_ID, null),
                        EvidenceRetrievalFamily.GIT_COMMIT, "x", 0, 20),
                EvidenceRetrievalFailureCode.INVALID_QUERY);
        assertCode(new EvidenceRetrievalQuery(
                        new EvidenceRetrievalScope(PROJECT_ID, null),
                        EvidenceRetrievalFamily.GIT_COMMIT, "", 0, 20),
                EvidenceRetrievalFailureCode.INVALID_QUERY);
    }

    @Test
    void rejectsInvalidPagesAndAcceptsMaximumPageSize() {
        assertCode(query(-1, 20), EvidenceRetrievalFailureCode.INVALID_PAGE);
        assertCode(query(0, 0), EvidenceRetrievalFailureCode.INVALID_PAGE);
        assertCode(query(0, 101), EvidenceRetrievalFailureCode.INVALID_PAGE);

        assertThat(boundary(ignored -> page(candidate())).retrieve(query(0, 100)))
                .isNotNull();
    }

    @Test
    void preservesEmptySuccessfulPage() {
        EvidencePage empty = new EvidencePage(List.of(), 0, 20, 0, false);

        assertThat(boundary(ignored -> empty).retrieve(query(0, 20)))
                .isSameAs(empty);
    }

    @Test
    void preservesCandidateIdentityScopeAndTrustMetadata() {
        EvidenceCandidate candidate = candidate();

        assertThat(candidate.canonicalReference()).isEqualTo(REFERENCE);
        assertThat(candidate.family()).isEqualTo(EvidenceRetrievalFamily.GIT_COMMIT);
        assertThat(candidate.projectId()).isEqualTo(PROJECT_ID);
        assertThat(candidate.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(candidate.provenance().authority()).isEqualTo("PROJECT_HISTORY");
        assertThat(candidate.trustTier()).isEqualTo(TrustTier.TECHNICAL_EVIDENCE);
    }

    @Test
    void requiresSourceIdForGitCommitCandidate() {
        assertThatThrownBy(() -> new EvidenceCandidate(
                REFERENCE,
                EvidenceRetrievalFamily.GIT_COMMIT,
                PROJECT_ID,
                null,
                Instant.parse("2026-09-21T10:00:00Z"),
                "Add shared retrieval contract",
                new EvidenceCandidateProvenance("PROJECT_HISTORY"),
                TrustTier.TECHNICAL_EVIDENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Git commit candidates require sourceId");
    }

    @Test
    void candidateUsesValueSemantics() {
        assertThat(candidate()).isEqualTo(candidate());
    }

    @Test
    void pageCollectionCannotBeMutated() {
        List<EvidenceCandidate> candidates = new ArrayList<>(List.of(candidate()));
        EvidencePage page = new EvidencePage(candidates, 0, 20, 1, false);

        candidates.clear();

        assertThat(page.candidates()).containsExactly(candidate());
        assertThatThrownBy(() -> page.candidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void boundaryDoesNotDependOnEvidenceResolution() {
        assertThat(SharedEvidenceRetrievalBoundaryImpl.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().contains("EvidenceResolution"));
    }

    private SharedEvidenceRetrievalBoundary boundary(GitCommitRetrievalPort port) {
        return new SharedEvidenceRetrievalBoundaryImpl(port);
    }

    private EvidenceRetrievalQuery query(int page, int pageSize) {
        return new EvidenceRetrievalQuery(
                new EvidenceRetrievalScope(PROJECT_ID, SOURCE_ID),
                EvidenceRetrievalFamily.GIT_COMMIT, "resolver", page, pageSize);
    }

    private EvidencePage page(EvidenceCandidate candidate) {
        return new EvidencePage(List.of(candidate), 0, 20, 1, false);
    }

    private EvidenceCandidate candidate() {
        return new EvidenceCandidate(
                REFERENCE,
                EvidenceRetrievalFamily.GIT_COMMIT,
                PROJECT_ID,
                SOURCE_ID,
                Instant.parse("2026-09-21T10:00:00Z"),
                "Add shared retrieval contract",
                new EvidenceCandidateProvenance("PROJECT_HISTORY"),
                TrustTier.TECHNICAL_EVIDENCE);
    }

    private void assertCode(
            EvidenceRetrievalQuery query,
            EvidenceRetrievalFailureCode code
    ) {
        assertThatThrownBy(() -> boundary(ignored -> page(candidate())).retrieve(query))
                .isInstanceOf(EvidenceRetrievalException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
