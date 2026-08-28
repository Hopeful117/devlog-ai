package com.hopeful117.devlogai.analysis.evidence.service;

import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse;
import com.hopeful117.devlogai.analysis.evidence.projection.HistoricalSelectedEvidenceSnapshotProjector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class AiTaskSelectedEvidencePersistenceIntegrationTest {
    private static final String VERSION = "knowledge-selection-v4";
    private static final String DIGEST = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AiTaskSelectedEvidenceService selectedEvidenceService;

    @Test
    void shouldReadPersistedSnapshotAfterCurrentKnowledgeChanges() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = insertProjectAndAnalysis(projectId);
        UUID factId = UUID.randomUUID();
        UUID insightId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        insertFact(factId, analysisId, "current fact before mutation");
        insertInsight(insightId, projectId, analysisId, "current insight before mutation");
        insertProfile(profileId, projectId, analysisId, "current profile before mutation");

        UUID taskId = UUID.randomUUID();
        Map<String, Object> snapshot = historicalSnapshot(
                projectId, analysisId, "historical fact", "historical insight",
                "historical profile");
        insertTask(taskId, analysisId, "COMPLETED", snapshot,
                VERSION, DIGEST, OffsetDateTime.now());

        AiTaskSelectedEvidenceResponse before =
                selectedEvidenceService.getSelectedEvidence(analysisId);

        jdbc.update("update facts set content = ? where id = ?",
                "mutated current fact", factId);
        jdbc.update("update insights set content = ?, status = 'ARCHIVED', updated_at = ? where id = ?",
                "mutated current insight", OffsetDateTime.now(), insightId);
        jdbc.update("""
                update project_profile_snapshots
                set deterministic_summary = ?, characteristic_count = ?
                where id = ?
                """, "mutated current profile", 99, profileId);

        AiTaskSelectedEvidenceResponse after =
                selectedEvidenceService.getSelectedEvidence(analysisId);

        assertEquals(AiTaskSelectedEvidenceResponse.State.AVAILABLE, after.state());
        assertEquals(taskId, after.task().id());
        assertEquals("historical fact", after.categories().facts().items().getFirst().content());
        assertEquals("historical insight",
                after.categories().priorInsights().items().getFirst().content());
        assertEquals("historical profile", after.snapshotMetadata()
                .projectProfile().deterministicSummary());
        assertEquals(before, after);
    }

    @Test
    void shouldSelectNewestTaskByTimestampThenId() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = insertProjectAndAnalysis(projectId);
        OffsetDateTime base = OffsetDateTime.now();
        UUID olderId = UUID.randomUUID();
        UUID newerId = UUID.randomUUID();
        insertTask(olderId, analysisId, "COMPLETED",
                historicalSnapshot(projectId, analysisId, "older", "older", "older"),
                VERSION, DIGEST, base);
        insertTask(newerId, analysisId, "COMPLETED",
                historicalSnapshot(projectId, analysisId, "newer", "newer", "newer"),
                VERSION, DIGEST, base.plusSeconds(10));

        AiTaskSelectedEvidenceResponse newest =
                selectedEvidenceService.getSelectedEvidence(analysisId);

        assertEquals(newerId, newest.task().id());
        assertEquals("newer", newest.categories().facts().items().getFirst().content());

        UUID tieProjectId = UUID.randomUUID();
        UUID tieAnalysisId = insertProjectAndAnalysis(tieProjectId);
        UUID lowerId = UUID.fromString("91000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("91000000-0000-0000-0000-000000000002");
        insertTask(lowerId, tieAnalysisId, "COMPLETED",
                historicalSnapshot(tieProjectId, tieAnalysisId, "lower", "lower", "lower"),
                VERSION, DIGEST, base);
        insertTask(higherId, tieAnalysisId, "COMPLETED",
                historicalSnapshot(tieProjectId, tieAnalysisId, "higher", "higher", "higher"),
                VERSION, DIGEST, base);

        AiTaskSelectedEvidenceResponse tieWinner =
                selectedEvidenceService.getSelectedEvidence(tieAnalysisId);

        assertEquals(higherId, tieWinner.task().id());
        assertEquals("higher", tieWinner.categories().facts().items().getFirst().content());
    }

    @Test
    void shouldFailClosedForCrossProjectSnapshotIdentity() {
        UUID projectA = UUID.randomUUID();
        UUID analysisA = insertProjectAndAnalysis(projectA);
        UUID projectB = UUID.randomUUID();
        UUID analysisB = insertProjectAndAnalysis(projectB);
        insertTask(UUID.randomUUID(), analysisB, "COMPLETED",
                historicalSnapshot(projectB, analysisB, "b", "b", "b"),
                VERSION, DIGEST, OffsetDateTime.now());

        UUID contradictoryTask = UUID.randomUUID();
        insertTask(contradictoryTask, analysisA, "COMPLETED",
                historicalSnapshot(projectB, analysisA, "a", "a", "a"),
                VERSION, DIGEST, OffsetDateTime.now());

        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException exception =
                assertThrows(
                        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException.class,
                        () -> selectedEvidenceService.getSelectedEvidence(analysisA));

        assertEquals("Selected evidence snapshot read failed task=%s version=%s path=project.id"
                .formatted(contradictoryTask, VERSION), exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void shouldClassifyLegacyNullSelectionTriadsByTaskLifecycle() {
        UUID pendingProject = UUID.randomUUID();
        UUID pendingAnalysis = insertProjectAndAnalysis(pendingProject);
        UUID pendingTask = UUID.randomUUID();
        insertTask(pendingTask, pendingAnalysis, "PROCESSING", null,
                null, null, OffsetDateTime.now());

        UUID terminalProject = UUID.randomUUID();
        UUID terminalAnalysis = insertProjectAndAnalysis(terminalProject);
        UUID terminalTask = UUID.randomUUID();
        insertTask(terminalTask, terminalAnalysis, "FAILED", null,
                null, null, OffsetDateTime.now());

        AiTaskSelectedEvidenceResponse pending =
                selectedEvidenceService.getSelectedEvidence(pendingAnalysis);
        AiTaskSelectedEvidenceResponse unavailable =
                selectedEvidenceService.getSelectedEvidence(terminalAnalysis);

        assertEquals(AiTaskSelectedEvidenceResponse.State.SNAPSHOT_PENDING, pending.state());
        assertEquals(pendingTask, pending.task().id());
        assertEquals(AiTaskSelectedEvidenceResponse.State.SNAPSHOT_UNAVAILABLE,
                unavailable.state());
        assertEquals(terminalTask, unavailable.task().id());
    }

    @Test
    void shouldFailSafelyForMalformedPersistedKnownShape() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = insertProjectAndAnalysis(projectId);
        UUID taskId = UUID.randomUUID();
        String sensitiveBody = "persisted-private-evidence-body";
        Map<String, Object> malformed = minimalSnapshot(projectId, analysisId);
        malformed.put("selectedFacts", sensitiveBody);
        insertTask(taskId, analysisId, "COMPLETED", malformed,
                VERSION, DIGEST, OffsetDateTime.now());

        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException exception =
                assertThrows(
                        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException.class,
                        () -> selectedEvidenceService.getSelectedEvidence(analysisId));

        assertEquals("Selected evidence snapshot read failed task=%s version=%s path=selectedFacts"
                .formatted(taskId, VERSION), exception.getMessage());
        assertFalse(exception.getMessage().contains(sensitiveBody));
        assertNull(exception.getCause());
    }

    private UUID insertProjectAndAnalysis(UUID projectId) {
        String projectName = "evidence-" + projectId;
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, projectId, projectName, projectName, now, now);

        UUID analysisId = UUID.randomUUID();
        jdbc.update("""
                insert into analyses
                    (id, project_id, type, intent_id, intent_version, status,
                     started_at, completed_at, created_at, updated_at)
                values (?, ?, 'ARCHITECTURE_REVIEW', 'architecture-overview', 'v1',
                        'COMPLETED', ?, ?, ?, ?)
                """, analysisId, projectId, now, now, now, now);
        return analysisId;
    }

    private void insertTask(
            UUID taskId,
            UUID analysisId,
            String status,
            Map<String, Object> snapshot,
            String selectionVersion,
            String selectionDigest,
            OffsetDateTime createdAt
    ) {
        String snapshotJson = snapshot == null ? null : objectMapper.writeValueAsString(snapshot);
        jdbc.update("""
                insert into ai_tasks
                    (id, analysis_id, correlation_id, task_type, status, context_snapshot,
                     selected_knowledge_snapshot, selection_version, selection_digest,
                     attempt_count, created_at)
                values (?, ?, ?, 'INSIGHT_GENERATION', ?, '{}'::jsonb,
                        cast(? as jsonb), ?, ?, 0, ?)
                """, taskId, analysisId, UUID.randomUUID(), status, snapshotJson,
                selectionVersion, selectionDigest, createdAt);
    }

    private void insertFact(UUID id, UUID analysisId, String content) {
        jdbc.update("""
                insert into facts (id, analysis_id, type, content, source, detected_at)
                values (?, ?, 'SOURCE_DIRECTORY_PRESENT', ?, 'src/App.java', ?)
                """, id, analysisId, content, OffsetDateTime.now());
    }

    private void insertInsight(UUID id, UUID projectId, UUID analysisId, String content) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into insights
                    (id, project_id, analysis_id, type, severity, status,
                     title, content, created_at, updated_at)
                values (?, ?, ?, 'ARCHITECTURAL', 'INFO', 'ACTIVE',
                        'Current insight', ?, ?, ?)
                """, id, projectId, analysisId, content, now, now);
    }

    private void insertProfile(UUID id, UUID projectId, UUID analysisId, String summary) {
        jdbc.update("""
                insert into project_profile_snapshots
                    (id, project_id, analysis_id, profile_version, renderer_version,
                     generated_at, requested_revision, resolved_revisions,
                     completeness_status, collection_complete, truncated,
                     warning_count, error_count, successful_collector_count,
                     collectors_with_warnings_count, failed_collector_count,
                     sections, deterministic_summary, source_observations, characteristic_count)
                values (?, ?, ?, 'v1', 'r1', ?, 'abc123', '{}'::jsonb,
                        'COMPLETE', true, false, 0, 0, 1, 0, 0,
                        '[]'::jsonb, ?, '[]'::jsonb, 1)
                """, id, projectId, analysisId, OffsetDateTime.now(), summary);
    }

    private Map<String, Object> historicalSnapshot(
            UUID projectId,
            UUID analysisId,
            String factContent,
            String insightContent,
            String profileSummary
    ) {
        Map<String, Object> snapshot = minimalSnapshot(projectId, analysisId);
        snapshot.put("projectProfile", map(
                "id", UUID.randomUUID().toString(),
                "projectId", projectId.toString(),
                "analysisId", analysisId.toString(),
                "deterministicSummary", profileSummary,
                "characteristicCount", 1));
        snapshot.put("selectedFacts", List.of(map(
                "id", UUID.randomUUID().toString(),
                "type", "SOURCE_DIRECTORY_PRESENT",
                "content", factContent,
                "source", "src/App.java",
                "evidenceReferences", List.of("fact:historical"),
                "detectedAt", "2026-08-27T10:00:00Z")));
        snapshot.put("selectedInsights", List.of(map(
                "type", "ARCHITECTURAL",
                "severity", "INFO",
                "title", "Historical insight",
                "content", insightContent)));
        return snapshot;
    }

    private Map<String, Object> minimalSnapshot(UUID projectId, UUID analysisId) {
        return map(
                "project", map("id", projectId.toString()),
                "analysis", map("id", analysisId.toString()),
                "selectedFacts", List.of(),
                "selectionMetadata", map("selectionVersion", VERSION),
                "selectionDigest", DIGEST
        );
    }

    private Map<String, Object> map(Object... entries) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }
}
