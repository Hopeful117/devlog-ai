package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class GitCommitEvidenceResolverPostgresIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired ProjectCommitRepository commits;
    @Autowired SourceRepository sources;
    @Autowired GitCommitEvidenceResolver resolver;

    @Test
    void resolvesParentsAndChangedFilesUsingSeparateEntityGraphs() {
        UUID project = UUID.randomUUID(), source = UUID.randomUUID(), commit = UUID.randomUUID();
        String hash = "a".repeat(40), parent = "b".repeat(40);
        jdbc.update("insert into projects (id,name,slug,description,status,created_at,updated_at) values (?,?,?,'','ACTIVE',?,?)",
                project, "Resolver project", "resolver-" + project, OffsetDateTime.now(), OffsetDateTime.now());
        jdbc.update("insert into sources (id,project_id,type,name,repository_url,default_branch,provider,active,created_at,updated_at) values (?,?, 'GIT_REPOSITORY',?,'https://example.test/r.git','main','GITHUB',true,?,?)",
                source, project, "Resolver source", OffsetDateTime.now(), OffsetDateTime.now());
        jdbc.update("insert into project_commits (id,project_id,source_id,commit_hash,committed_at,subject,full_message,root_commit,merge_commit,files_changed,insertions,deletions,binary_files,imported_at) values (?,?,?,?,?,'Subject','Message',false,false,1,1,0,0,?)",
                commit, project, source, hash, OffsetDateTime.now(), OffsetDateTime.now());
        jdbc.update("insert into commit_parents (id,project_commit_id,parent_index,parent_hash) values (?,?,0,?)",
                UUID.randomUUID(), commit, parent);
        jdbc.update("insert into commit_changed_files (id,project_commit_id,change_type,new_path,binary_file,insertions,deletions) values (?,?,'ADDED','src/App.java',false,1,0)",
                UUID.randomUUID(), commit);

        var ref = new ParsedEvidenceReference("git:" + source + ":" + hash,
                EvidenceResolutionFamily.GIT_COMMIT, source, hash, hash);
        var result = resolver.resolve(ref, new EvidenceResolutionRequest(ref.canonicalReference(), EvidenceResolutionMode.CURRENT));
        var payload = (GitCommitResolutionPayload) result.payload();
        assertEquals(List.of(parent), payload.parentHashes());
        assertEquals("src/App.java", payload.changedFiles().getFirst().newPath());
    }
}
