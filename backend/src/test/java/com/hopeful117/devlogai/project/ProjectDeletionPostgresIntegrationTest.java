package com.hopeful117.devlogai.project;

import com.hopeful117.devlogai.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Testcontainers
class ProjectDeletionPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProjectService projects;

    @Test
    void migrationDefinesCompleteProjectOwnershipAndDeletionIsIsolated() {
        assertDeleteRule("fk_knowledge_event_project", "CASCADE");
        assertDeleteRule("fk_decision_project", "CASCADE");
        assertDeleteRule("fk_artifact_project", "CASCADE");
        assertDeleteRule("fk_documentation_project", "CASCADE");
        assertDeleteRule("fk_milestones_project", "CASCADE");
        assertDeleteRule("fk_analyses_project", "CASCADE");
        assertDeleteRule("fk_insights_project", "CASCADE");
        assertDeleteRule("fk_validatable_proposal_project", "CASCADE");
        assertDeleteRule("fk_source_project", "CASCADE");
        assertDeleteRule("fk_profile_project", "CASCADE");
        assertDeleteRule("generated_deliverables_project_id_fkey", "CASCADE");
        assertDeleteRule("project_commits_project_id_fkey", "CASCADE");
        assertDeleteRule("fk_validation_proposal", "CASCADE");
        assertDeleteRule("fk_insight_proposal", "SET NULL");
        assertDeleteRule("fk_insight_validation", "SET NULL");
        assertDeleteRule("generated_deliverable_insights_insight_id_fkey", "CASCADE");

        UUID deletedProject = UUID.randomUUID();
        UUID retainedProject = UUID.randomUUID();
        insertProject(deletedProject, "Deleted project", "deleted-project");
        insertProject(retainedProject, "Retained project", "retained-project");

        UUID deletedSource = insertSource(deletedProject, "Deleted source");
        UUID retainedSource = insertSource(retainedProject, "Retained source");
        UUID deletedCommit = insertCommit(deletedProject, deletedSource, "a".repeat(40));
        UUID retainedCommit = insertCommit(retainedProject, retainedSource, "b".repeat(40));
        insertChangedFile(deletedCommit, "src/deleted.java");
        insertChangedFile(retainedCommit, "src/retained.java");

        projects.delete("deleted-project");

        assertEquals(0, count("projects", deletedProject));
        assertEquals(0, count("sources", deletedSource));
        assertEquals(0, count("project_commits", deletedCommit));
        assertEquals(0, count("commit_changed_files", deletedCommit, "project_commit_id"));
        assertEquals(1, count("projects", retainedProject));
        assertEquals(1, count("sources", retainedSource));
        assertEquals(1, count("project_commits", retainedCommit));
        assertEquals(1, count("commit_changed_files", retainedCommit, "project_commit_id"));
        assertEquals("34", jdbc.queryForObject(
                "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                String.class));
    }

    @Test
    void activeUnderstandingExecutionIsUniqueAndTerminalExecutionReleasesTheKey() throws Exception {
        UUID project = UUID.randomUUID();
        insertProject(project, "Understanding project", "understanding-project");
        UUID source = insertSource(project, "Understanding source");
        String key = "c".repeat(64);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> concurrentInsert(project, source, key, ready, start));
            var second = executor.submit(() -> concurrentInsert(project, source, key, ready, start));
            ready.await();
            start.countDown();
            int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, successes);
        }

        UUID active = jdbc.queryForObject(
                "select id from analyses where understanding_execution_key = ?", UUID.class, key);
        jdbc.update("update analyses set status = 'COMPLETED', completed_at = ? where id = ?",
                OffsetDateTime.now(), active);
        UUID refresh = insertUnderstanding(project, source, key);
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from analyses where understanding_execution_key = ?",
                Integer.class, key));

        jdbc.update("delete from sources where id = ?", source);
        assertNull(jdbc.queryForObject(
                "select selected_source_id from analyses where id = ?", UUID.class, refresh));
        assertEquals(source.toString(), jdbc.queryForObject(
                "select selected_source_snapshot ->> 'id' from analyses where id = ?",
                String.class, refresh));
    }

    private boolean concurrentInsert(UUID project, UUID source, String key,
                                     CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            insertUnderstanding(project, source, key);
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException expected) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private UUID insertUnderstanding(UUID project, UUID source, String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into analyses
                    (id, project_id, selected_source_id, selected_source_snapshot,
                     understanding_execution_key, type, status, intent_id, intent_version,
                     created_at, updated_at)
                values (?, ?, ?, cast(? as jsonb), ?, 'ARCHITECTURE_REVIEW', 'PENDING',
                        'describe-project', 'v1', ?, ?)
                """, id, project, source, "{\"id\":\"" + source + "\"}", key,
                OffsetDateTime.now(), OffsetDateTime.now());
        return id;
    }

    private void assertDeleteRule(String constraint, String expected) {
        List<String> rules = jdbc.queryForList("""
                select delete_rule
                from information_schema.referential_constraints
                where constraint_schema = current_schema() and constraint_name = ?
                """, String.class, constraint);
        assertEquals(List.of(expected), rules, constraint);
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
                values (?, ?, ?, ?, ?, 'Subject', 'Message', true, false, 1, 1, 0, 0, ?)
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

    private int count(String table, UUID id) {
        return count(table, id, "id");
    }

    private int count(String table, UUID id, String column) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Integer.class, id);
    }
}
