package com.hopeful117.devlogai.lineage.service;

import com.hopeful117.devlogai.lineage.dto.KnowledgeLifecycleDiagnosticResponse;
import com.hopeful117.devlogai.lineage.dto.KnowledgeLifecycleStatus;
import com.hopeful117.devlogai.lineage.dto.LineageStageStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.validation.dto.request.CreateValidationRequest;
import com.hopeful117.devlogai.validation.entity.ValidationDecision;
import com.hopeful117.devlogai.validation.service.ValidationService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class KnowledgeLifecycleDiagnosticPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ValidatableProposalRepository proposals;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private KnowledgeLifecycleDiagnosticService diagnostic;

    @Test
    void promotedEngineeringDecisionLifecycleIsComplete() {
        UUID project = UUID.randomUUID();
        insertProject(project, "Diagnostic project", "diagnostic-project");
        UUID analysis = insertAnalysis(project, "ARCHITECTURE_REVIEW");
        UUID proposalId = insertProposal(project, analysis, "ENGINEERING_DECISION",
                ProposalStatus.PROPOSED, "{\"title\":\"T\",\"context\":\"C\",\"choice\":\"X\",\"rationale\":\"R\"}");

        validationService.validate(new CreateValidationRequest(
                proposalId, ValidationDecision.ACCEPTED, "approved", UUID.randomUUID()));

        KnowledgeLifecycleDiagnosticResponse response = diagnostic.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.COMPLETE, response.lifecycleStatus());
        assertEquals(ProposalType.ENGINEERING_DECISION, response.type());
        assertEquals(ProposalStatus.ACCEPTED, response.proposalStatus());
        assertEquals(LineageStageStatus.PRESENT, stage(response, "Validation").status());
        assertEquals(LineageStageStatus.PRESENT, stage(response, "Promoted Knowledge").status());
        assertTrue(response.findings().isEmpty(), "no findings for a complete lifecycle: " + response.findings());
    }

    @Test
    void acceptedProposalWithoutPromotedDecisionIsBrokenWithInvariantFinding() {
        UUID project = UUID.randomUUID();
        insertProject(project, "Diagnostic gap project", "diagnostic-gap-project");
        UUID analysis = insertAnalysis(project, "ARCHITECTURE_REVIEW");
        UUID proposalId = insertProposal(project, analysis, "ENGINEERING_DECISION",
                ProposalStatus.ACCEPTED, "{}");

        KnowledgeLifecycleDiagnosticResponse response = diagnostic.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.BROKEN, response.lifecycleStatus());
        assertEquals(LineageStageStatus.MISSING, stage(response, "Promoted Knowledge").status());
        assertTrue(response.findings().stream()
                        .anyMatch(f -> f.startsWith("An ACCEPTED") && f.contains("MUST produce exactly one")),
                "expected invariant-violation finding, got " + response.findings());
    }

    @Test
    void proposedProposalWithoutValidationIsPendingAndComplete() {
        UUID project = UUID.randomUUID();
        insertProject(project, "Diagnostic proposed project", "diagnostic-proposed-project");
        UUID analysis = insertAnalysis(project, "ARCHITECTURE_REVIEW");
        UUID proposalId = insertProposal(project, analysis, "ENGINEERING_DECISION",
                ProposalStatus.PROPOSED, "{}");

        KnowledgeLifecycleDiagnosticResponse response = diagnostic.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.COMPLETE, response.lifecycleStatus());
        assertEquals(LineageStageStatus.PENDING, stage(response, "Validation").status());
        assertTrue(response.findings().isEmpty());
    }

    @Test
    void rejectedProposalWithRejectedValidationIsComplete() {
        UUID project = UUID.randomUUID();
        insertProject(project, "Diagnostic rejected project", "diagnostic-rejected-project");
        UUID analysis = insertAnalysis(project, "ARCHITECTURE_REVIEW");
        UUID proposalId = insertProposal(project, analysis, "ENGINEERING_DECISION",
                ProposalStatus.REJECTED, "{}");
        insertValidation(proposalId, "REJECTED", UUID.randomUUID());

        KnowledgeLifecycleDiagnosticResponse response = diagnostic.diagnose(proposalId);

        assertEquals(KnowledgeLifecycleStatus.COMPLETE, response.lifecycleStatus());
        assertEquals(LineageStageStatus.PRESENT, stage(response, "Validation").status());
        assertEquals(LineageStageStatus.NOT_APPLICABLE, stage(response, "Promoted Knowledge").status());
        assertTrue(response.findings().isEmpty());
    }

    private com.hopeful117.devlogai.lineage.dto.KnowledgeLifecycleStageResponse stage(
            KnowledgeLifecycleDiagnosticResponse r, String name) {
        return r.stages().stream().filter(s -> s.stage().equals(name)).findFirst().orElseThrow();
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

    private UUID insertProposal(UUID projectId, UUID analysisId, String type,
                                ProposalStatus status, String payload) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into validatable_proposals
                    (id, project_id, analysis_id, type, status, payload, created_at)
                values (?, ?, ?, ?, ?, cast(? as jsonb), ?)
                """, id, projectId, analysisId, type, status.name(), payload, OffsetDateTime.now());
        return id;
    }

    private void insertValidation(UUID proposalId, String decision, UUID validatedBy) {
        jdbc.update("""
                insert into validations (id, proposal_id, decision, validated_at, validated_by)
                values (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), proposalId, decision, OffsetDateTime.now(), validatedBy);
    }
}