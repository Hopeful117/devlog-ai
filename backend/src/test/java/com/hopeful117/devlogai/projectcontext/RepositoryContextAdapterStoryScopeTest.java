package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus;
import com.hopeful117.devlogai.projectfreshness.ProjectRefreshGuidance;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryContextAdapterStoryScopeTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final String SHA_A = "a".repeat(40);
    private static final String SHA_B = "b".repeat(40);
    private static final String SHA_C = "c".repeat(40);
    private static final String SHA_D = "d".repeat(40);

    @Mock ProjectContextProvider projectContextProvider;
    @Mock RepositoryContextService repositoryContextService;
    @Mock InsightRepository insightRepository;
    @Mock FactRepository factRepository;
    @Mock ObservationRepository observationRepository;
    @Mock ProjectCommitRepository commitRepository;
    @Mock ProjectFreshnessService freshnessService;

    private RepositoryContextAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RepositoryContextAdapter(projectContextProvider,
                repositoryContextService, insightRepository, factRepository,
                observationRepository, commitRepository, freshnessService);
    }

    @Test
    void storyNotFound_excludesAllTechnicalEvidence() {
        UUID unknownStoryId = UUID.randomUUID();
        var snapshot = snapshotWithStory(unknownStoryId, SHA_A, SHA_B);
        var context = contextWithEvidence(commitEvidence(SOURCE_ID, SHA_B));

        var result = adapter.filterByStoryScope(context, PROJECT_ID, snapshot, unknownStoryId);

        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void noCommits_excludesAllTechnicalEvidence() {
        var storyId = UUID.randomUUID();
        var snapshot = snapshotWithStory(storyId, null, null);
        var context = contextWithEvidence(commitEvidence(SOURCE_ID, SHA_B));

        var result = adapter.filterByStoryScope(context, PROJECT_ID, snapshot, storyId);

        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void targetOnly_excludesAllTechnicalEvidence() {
        var storyId = UUID.randomUUID();
        var snapshot = snapshotWithStory(storyId, null, SHA_B);
        var context = contextWithEvidence(commitEvidence(SOURCE_ID, SHA_B));

        when(freshnessService.summary(PROJECT_ID)).thenReturn(
                new com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary(
                        "v1", PROJECT_ID, List.of(), 0, false));

        var result = adapter.filterByStoryScope(context, PROJECT_ID, snapshot, storyId);

        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void baseOnlyWithoutSnapshot_excludesAllTechnicalEvidence() {
        var storyId = UUID.randomUUID();
        var snapshot = snapshotWithStory(storyId, SHA_A, null);
        var context = contextWithEvidence(commitEvidence(SOURCE_ID, SHA_B));

        when(freshnessService.summary(PROJECT_ID)).thenReturn(
                new com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary(
                        "v1", PROJECT_ID, List.of(), 0, false));

        var result = adapter.filterByStoryScope(context, PROJECT_ID, snapshot, storyId);

        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void baseAndTargetReachable_filtersToWindow() {
        var storyId = UUID.randomUUID();
        var snapshot = snapshotWithStory(storyId, SHA_A, SHA_C);
        var evidenceA = commitEvidence(SOURCE_ID, SHA_A);
        var evidenceB = commitEvidence(SOURCE_ID, SHA_B);
        var evidenceC = commitEvidence(SOURCE_ID, SHA_C);
        var evidenceD = commitEvidence(SOURCE_ID, SHA_D);
        var context = contextWithEvidence(evidenceA, evidenceB, evidenceC, evidenceD);

        var linearCommits = linearChain(PROJECT_ID, SHA_A, SHA_B, SHA_C, SHA_D);
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(linearCommits);

        var result = adapter.filterByStoryScope(context, PROJECT_ID, snapshot, storyId);

        assertThat(result.evidence()).containsExactlyInAnyOrder(evidenceB, evidenceC);
    }

    @Test
    void baseOnlyWithSnapshot_filtersToDeterministicWindow() {
        var storyId = UUID.randomUUID();
        var snapshot = snapshotWithStory(storyId, SHA_A, null);
        var evidenceA = commitEvidence(SOURCE_ID, SHA_A);
        var evidenceB = commitEvidence(SOURCE_ID, SHA_B);
        var evidenceC = commitEvidence(SOURCE_ID, SHA_C);
        var evidenceD = commitEvidence(SOURCE_ID, SHA_D);
        var context = contextWithEvidence(evidenceA, evidenceB, evidenceC, evidenceD);

        var linearCommits = linearChain(PROJECT_ID, SHA_A, SHA_B, SHA_C, SHA_D);
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(linearCommits);

        var baseline = new ProjectFreshnessResponse.Baseline(
                UUID.randomUUID(), Instant.now(), SHA_C);
        var source = new ProjectFreshnessResponse.Source(
                UUID.randomUUID(), "repo", "main", null, SHA_C, null);
        var freshnessRow = new ProjectFreshnessResponse(
                "v1", UUID.randomUUID(), PROJECT_ID, source,
                Instant.now(), ProjectFreshnessStatus.CURRENT,
                ProjectRefreshGuidance.REFRESH_NOT_NEEDED,
                baseline,
                new ProjectFreshnessResponse.ReviewCounts(0, 0, 0, 0));
        when(freshnessService.summary(PROJECT_ID)).thenReturn(
                new com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary(
                        "v1", PROJECT_ID, List.of(freshnessRow), 0, false));

        var result = adapter.filterByStoryScope(context, PROJECT_ID, snapshot, storyId);

        assertThat(result.evidence())
                .as("base A excluded per lower-bound semantics")
                .noneMatch(e -> e.reference().contains(SHA_A));
        assertThat(result.evidence())
                .as("intermediate commit B included")
                .anyMatch(e -> e.reference().contains(SHA_B));
        assertThat(result.evidence())
                .as("snapshot revision C included")
                .anyMatch(e -> e.reference().contains(SHA_C));
        assertThat(result.evidence())
                .as("post-snapshot commit D excluded")
                .noneMatch(e -> e.reference().contains(SHA_D));
        assertThat(result.evidence()).hasSize(2);
    }

    @Test
    void unreachableTarget_excludesAllTechnicalEvidence() {
        var storyId = UUID.randomUUID();
        var snapshot = snapshotWithStory(storyId, SHA_A, SHA_C);
        var evidence = commitEvidence(SOURCE_ID, SHA_C);
        var context = contextWithEvidence(evidence);

        var commitA = commitEntity(PROJECT_ID, SHA_A);
        var commitC = commitEntity(PROJECT_ID, SHA_C);
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(List.of(commitA, commitC));

        var result = adapter.filterByStoryScope(context, PROJECT_ID, snapshot, storyId);

        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void evidenceMentioningEndpointButOutsideWindow_excluded() {
        var storyId = UUID.randomUUID();
        var snapshot = snapshotWithStory(storyId, SHA_A, SHA_C);
        var evidence = commitEvidence(SOURCE_ID, SHA_D);
        var context = contextWithEvidence(evidence);

        var linearCommits = linearChain(PROJECT_ID, SHA_A, SHA_B, SHA_C, SHA_D);
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(linearCommits);

        var result = adapter.filterByStoryScope(context, PROJECT_ID, snapshot, storyId);

        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void filesAndStoryId_produceStrictIntersection() {
        var storyId = UUID.randomUUID();
        var snapshot = snapshotWithStory(storyId, SHA_A, SHA_C);
        var evidenceInWindow = changedFileEvidence(SOURCE_ID, SHA_B, "src/App.java");
        var evidenceOutsideFile = changedFileEvidence(SOURCE_ID, SHA_B, "src/Other.java");
        var context = contextWithEvidence(evidenceInWindow, evidenceOutsideFile);

        var linearCommits = linearChain(PROJECT_ID, SHA_A, SHA_B, SHA_C);
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(PROJECT_ID))
                .thenReturn(linearCommits);

        var storyResult = adapter.filterByStoryScope(context, PROJECT_ID, snapshot, storyId);
        assertThat(storyResult.evidence()).hasSize(2);
    }

    private ProjectContextSnapshot snapshotWithStory(
            UUID storyId, String baseCommit, String targetCommit) {
        var story = new ProjectContextSnapshot.EngineeringStorySnapshot(
                storyId, PROJECT_ID, 1, "Test Story", "IN_PROGRESS",
                "docs/stories/0001.md", baseCommit, targetCommit,
                Instant.now(), null);
        return new ProjectContextSnapshot(
                new com.hopeful117.devlogai.analysis.context.AnalysisContext.ProjectSnapshot(
                        PROJECT_ID, "test", "test", "desc",
                        com.hopeful117.devlogai.project.entity.ProjectStatus.ACTIVE),
                null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(story));
    }

    private RepositoryContext contextWithEvidence(RepositoryEvidence... evidence) {
        return new RepositoryContext(
                "v1", null, List.of(), "v1", List.of(),
                List.of(evidence), java.util.Map.of(),
                null, new RepositoryContext.ContextBudget(50, 5000, 20, 10000),
                100, evidence.length, 0, false, List.of(), List.of(), "digest");
    }

    private RepositoryEvidence commitEvidence(UUID sourceId, String sha) {
        return new RepositoryEvidence(
                RepositoryContextLayer.GIT_HISTORY, "COMMIT",
                "git:" + sourceId + ":" + sha,
                "test commit", Instant.now(), EvidenceScore.unscored(),
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                        "GIT", "src", null, null),
                java.util.Map.of(), 60, List.of());
    }

    private RepositoryEvidence changedFileEvidence(UUID sourceId, String commitSha, String filePath) {
        return new RepositoryEvidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:" + commitSha + ":" + filePath,
                "changed " + filePath, Instant.now(), EvidenceScore.unscored(),
                List.of("git:" + sourceId + ":" + commitSha),
                new RepositoryEvidence.EvidenceProvenance(
                        "GIT", "src", filePath, null),
                java.util.Map.of(), 60, List.of());
    }

    private List<ProjectCommit> linearChain(UUID projectId, String... shas) {
        var commits = new ArrayList<ProjectCommit>();
        for (int i = 0; i < shas.length; i++) {
            var commit = commitEntity(projectId, shas[i]);
            if (i > 0) {
                commit.addParent(0, shas[i - 1]);
            }
            commits.add(commit);
        }
        return commits;
    }

    private ProjectCommit commitEntity(UUID projectId, String sha) {
        var project = new Project();
        project.setId(projectId);
        var source = new com.hopeful117.devlogai.source.entity.Source();
        source.setId(UUID.randomUUID());
        return ProjectCommit.builder()
                .project(project)
                .source(source)
                .commitHash(sha)
                .committedAt(Instant.now())
                .subject("test")
                .fullMessage("test commit")
                .rootCommit(false)
                .mergeCommit(false)
                .filesChanged(1)
                .insertions(10)
                .deletions(5)
                .binaryFiles(0)
                .importedAt(Instant.now())
                .build();
    }
}
