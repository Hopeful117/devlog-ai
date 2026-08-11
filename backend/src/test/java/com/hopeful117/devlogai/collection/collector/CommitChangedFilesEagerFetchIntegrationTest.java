package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards against the {@code LazyInitializationException} reported when the
 * "Refresh understanding" pipeline iterates {@code ProjectCommit.changedFiles}
 * outside the Hibernate session (the collectors run on a virtual thread).
 * The repository must eagerly fetch the collection so it is available
 * after the loading transaction has closed.
 */
@SpringBootTest
@Testcontainers
class CommitChangedFilesEagerFetchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ProjectCommitRepository projectCommitRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void changedFilesAreInitializedWhenAccessedOutsideTheLoadingTransaction() throws Exception {
        UUID project = UUID.randomUUID();
        insertProject(project, "Change files project", "change-files-project");
        UUID source = insertSource(project, "Change files source");
        UUID commit = insertCommit(project, source, "a".repeat(40));
        insertChangedFile(commit, "src/demo/Service.java");
        insertChangedFile(commit, "src/demo/Controller.java");

        Instant cutoff = Instant.now().minus(Duration.ofDays(1));

        List<ProjectCommit> commits = projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        project, cutoff);

        assertEquals(1, commits.size());

        // Repository transactions are closed by now; access the lazy collection
        // from a fresh virtual thread exactly like CollectorRunner does.
        FutureTask<Integer> task = new FutureTask<>(() ->
                commits.stream().mapToInt(c -> c.getChangedFiles().size()).sum());
        Thread worker = Thread.startVirtualThread(task);
        int total = task.get(5, TimeUnit.SECONDS);
        worker.join();

        assertEquals(2, total);
    }

    private void insertProject(UUID id, String name, String slug) {
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, name, slug, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private UUID insertSource(UUID projectId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into sources
                    (id, project_id, type, name, repository_url, default_branch, provider, active,
                     created_at, updated_at)
                values (?, ?, 'GIT', ?, 'https://example.test/repository.git', 'main', 'GITHUB',
                        true, ?, ?)
                """, id, projectId, name, OffsetDateTime.now(), OffsetDateTime.now());
        return id;
    }

    private UUID insertCommit(UUID projectId, UUID sourceId, String hash) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into project_commits
                    (id, project_id, source_id, commit_hash, committed_at, subject, full_message,
                     root_commit, merge_commit, files_changed, insertions, deletions, binary_files,
                     imported_at)
                values (?, ?, ?, ?, ?, 'Subject', 'Message', true, false, 2, 2, 0, 0, ?)
                """, id, projectId, sourceId, hash, OffsetDateTime.now(), OffsetDateTime.now());
        return id;
    }

    private void insertChangedFile(UUID commitId, String path) {
        jdbc.update("""
                insert into commit_changed_files
                    (id, project_commit_id, change_type, new_path, binary_file, insertions, deletions)
                values (?, ?, 'ADDED', ?, false, 1, 0)
                """, UUID.randomUUID(), commitId, path);
    }
}