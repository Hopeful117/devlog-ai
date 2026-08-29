package com.hopeful117.devlogai.decision;

import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class DecisionPromotionProvenancePostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DecisionRepository decisionRepository;

    @Test
    void migrationAppliesUniqueAndPreservesLegacyNullProvenance() {
        assertEquals(latestMigrationVersion(), jdbc.queryForObject(
                "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                String.class));

        List<String> proposalColumns = jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'decisions' and column_name = 'proposal_id'
                """, String.class);
        assertEquals(List.of("proposal_id"), proposalColumns);

        List<String> uniqueConstraint = jdbc.queryForList(
                "select conname from pg_constraint where conname = 'uk_decision_proposal_id' and contype = 'u'",
                String.class);
        assertEquals(List.of("uk_decision_proposal_id"), uniqueConstraint);

        UUID project = UUID.randomUUID();
        insertProject(project, "Decision provenance project", "decision-provenance-project");
        UUID analysis = insertAnalysis(project, "ARCHITECTURE_REVIEW");
        UUID proposal = insertProposal(project, analysis, "ENGINEERING_DECISION");

        insertDecision(project, (UUID) null, UUID.randomUUID());
        insertDecision(project, proposal, "linked-proposal");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertDecision(project, proposal, "duplicate-proposal"));
    }

    @Test
    void findByProposalIdReturnsExactlyOnePromotedDecision() {
        UUID project = UUID.randomUUID();
        insertProject(project, "Decision query project", "decision-query-project");
        UUID analysis = insertAnalysis(project, "ARCHITECTURE_REVIEW");
        UUID proposalA = insertProposal(project, analysis, "ENGINEERING_DECISION");
        UUID proposalB = insertProposal(project, analysis, "ENGINEERING_DECISION");

        UUID decisionA = UUID.randomUUID();
        UUID decisionB = UUID.randomUUID();
        insertDecision(project, proposalA, decisionA);
        insertDecision(project, proposalB, decisionB);
        insertDecision(project, null, UUID.randomUUID());

        java.util.Optional<Decision> found = decisionRepository.findByProposalId(proposalA);
        assertTrue(found.isPresent(), "findByProposalId should return the promoted Decision");
        assertEquals(decisionA, found.get().getId());

        assertFalse(decisionRepository.findByProposalId(UUID.randomUUID()).isPresent(),
                "unlinked proposal has no promoted Decision");
    }

    @Test
    void findByProposalIdInReturnsPromotedDecisionsInBatch() {
        UUID project = UUID.randomUUID();
        insertProject(project, "Decision batch query project", "decision-batch-query-project");
        UUID analysis = insertAnalysis(project, "ARCHITECTURE_REVIEW");
        UUID proposalA = insertProposal(project, analysis, "ENGINEERING_DECISION");
        UUID proposalB = insertProposal(project, analysis, "ENGINEERING_DECISION");

        UUID decisionA = UUID.randomUUID();
        UUID decisionB = UUID.randomUUID();
        insertDecision(project, proposalA, decisionA);
        insertDecision(project, proposalB, decisionB);

        List<Decision> found = decisionRepository.findByProposalIdIn(List.of(proposalA, proposalB));

        assertEquals(2, found.size());
        assertEquals(
                List.of(decisionA, decisionB).stream().sorted().toList(),
                found.stream().map(Decision::getId).sorted().toList()
        );
    }

    private void insertProject(UUID id, String name, String slug) {
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, name, slug, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private UUID insertAnalysis(UUID projectId, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into analyses
                    (id, project_id, type, status, started_at, created_at, updated_at)
                values (?, ?, ?, 'COMPLETED', ?, ?, ?)
                """, id, projectId, type, OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now());
        return id;
    }

    private UUID insertProposal(UUID projectId, UUID analysisId, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into validatable_proposals
                    (id, project_id, analysis_id, type, status, payload, created_at)
                values (?, ?, ?, ?, 'ACCEPTED', cast('{}' as jsonb), ?)
                """, id, projectId, analysisId, type, OffsetDateTime.now());
        return id;
    }

    private void insertDecision(UUID projectId, UUID proposalId, String title) {
        insertDecision(projectId, proposalId, UUID.randomUUID(), title);
    }

    private void insertDecision(UUID projectId, UUID proposalId, UUID id) {
        insertDecision(projectId, proposalId, id, "Promoted " + id);
    }

    private void insertDecision(UUID projectId, UUID proposalId, UUID id, String title) {
        jdbc.update("""
                insert into decisions
                    (id, project_id, proposal_id, title, context, choice, rationale,
                     created_at, updated_at)
                values (?, ?, ?, ?, 'context', 'choice', 'rationale', ?, ?)
                """, id, projectId, proposalId, title, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private String latestMigrationVersion() {
        try {
            return java.util.Arrays.stream(new PathMatchingResourcePatternResolver()
                            .getResources("classpath:db/migration/V*__*.sql"))
                    .map(resource -> resource.getFilename())
                    .filter(filename -> filename != null)
                    .map(filename -> filename.substring(1, filename.indexOf("__")))
                    .max(Comparator.comparingInt(Integer::parseInt))
                    .orElseThrow();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to determine latest migration version", exception);
        }
    }
}
