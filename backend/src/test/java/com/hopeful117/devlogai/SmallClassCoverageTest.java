package com.hopeful117.devlogai;

import com.hopeful117.devlogai.ai.engine.config.AIEngineProperties;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.collection.collector.CollectionWarning;
import com.hopeful117.devlogai.collection.collector.NonFatalCollectionException;
import com.hopeful117.devlogai.collection.service.CollectionDiagnostic;
import com.hopeful117.devlogai.collection.collector.CollectorType;
import com.hopeful117.devlogai.collection.workspace.GitCommandException;
import com.hopeful117.devlogai.fact.dto.response.FactResponse;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.history.dto.ProjectCommitResponse;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.history.provider.GitHistoryReadException;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.observation.dto.response.ObservationResponse;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.proposal.dto.response.InsightProposalPayloadResponse;
import com.hopeful117.devlogai.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted tests to cover small partially-covered or uncovered classes:
 * exceptions, DTOs, records, and config classes with 0-5 missed lines each.
 */
class SmallClassCoverageTest {

    // --- Exceptions ---

    @Test
    void gitCommandExceptionSingleArg() {
        GitCommandException ex = assertThrows(GitCommandException.class,
                () -> { throw new GitCommandException("git failed"); });
        assertEquals("git failed", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void gitCommandExceptionWithCause() {
        RuntimeException cause = new RuntimeException("root");
        GitCommandException ex = assertThrows(GitCommandException.class,
                () -> { throw new GitCommandException("git failed", cause); });
        assertEquals("git failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void gitHistoryReadExceptionSingleArg() {
        GitHistoryReadException ex = assertThrows(GitHistoryReadException.class,
                () -> { throw new GitHistoryReadException("read failed"); });
        assertEquals("read failed", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void gitHistoryReadExceptionWithCause() {
        RuntimeException cause = new RuntimeException("root");
        GitHistoryReadException ex = assertThrows(GitHistoryReadException.class,
                () -> { throw new GitHistoryReadException("read failed", cause); });
        assertEquals("read failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void businessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> { throw new BusinessException("business rule"); });
        assertEquals("business rule", ex.getMessage());
    }

    // --- Records with validation ---

    @Test
    void collectionWarningValid() {
        CollectionWarning w = new CollectionWarning("W001", "something happened");
        assertEquals("W001", w.code());
        assertEquals("something happened", w.message());
    }

    @Test
    void collectionWarningBlankCodeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CollectionWarning("", "msg"));
    }

    @Test
    void collectionWarningNullMessageThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CollectionWarning("CODE", null));
    }

    @Test
    void nonFatalCollectionException() {
        NonFatalCollectionException ex = assertThrows(NonFatalCollectionException.class,
                () -> { throw new NonFatalCollectionException("NF001", "non-fatal"); });
        assertEquals("NF001", ex.code());
        assertEquals("non-fatal", ex.getMessage());
    }

    // --- Simple DTOs / Records ---

    @Test
    void observationResponse() {
        ObservationResponse r = new ObservationResponse(
                UUID.randomUUID(), UUID.randomUUID(), ObservationType.ASYNCHRONOUS_COMMUNICATION,
                "content", "rule", "v1", Set.of(), Instant.now());
        assertNotNull(r.id());
        assertEquals(ObservationType.ASYNCHRONOUS_COMMUNICATION, r.type());
    }

    @Test
    void insightProposalPayloadResponse() {
        InsightProposalPayloadResponse r = new InsightProposalPayloadResponse(
                "type", "title", "summary", "rationale");
        assertEquals("title", r.title());
        assertEquals("summary", r.summary());
    }

    @Test
    void collectionDiagnostic() {
        CollectionDiagnostic d = new CollectionDiagnostic(
                UUID.randomUUID(), CollectorType.GIT, "1.0", "D001", "diagnostic msg");
        assertEquals("D001", d.code());
        assertEquals(CollectorType.GIT, d.collectorType());
    }

    @Test
    void aiEngineProperties() {
        AIEngineProperties p = new AIEngineProperties(
                "http://localhost:8080", Duration.ofSeconds(5), Duration.ofSeconds(30));
        assertEquals("http://localhost:8080", p.baseUrl());
        assertEquals(Duration.ofSeconds(5), p.connectTimeout());
    }

    @Test
    void factResponse() {
        FactResponse r = new FactResponse(
                UUID.randomUUID(), UUID.randomUUID(), FactType.COMMIT,
                "/api/test", "src", Set.of(), Instant.now());
        assertEquals(FactType.COMMIT, r.type());
        assertEquals("/api/test", r.content());
    }

    @Test
    void projectCommitResponse() {
        ProjectCommitResponse r = new ProjectCommitResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "abc123", List.of(), "Author", "a@b.com",
                Instant.now(), Instant.now(), "subject", "full msg",
                false, false, 1, 10, 5, 0, Instant.now(), List.of());
        assertEquals("abc123", r.commitHash());
        assertEquals("subject", r.subject());
    }

    @Test
    void projectCommitResponseChangedFile() {
        ProjectCommitResponse.ChangedFileResponse r =
                new ProjectCommitResponse.ChangedFileResponse(
                        FileChangeType.MODIFIED, "/old", "/new", false, 10, 5);
        assertEquals(FileChangeType.MODIFIED, r.changeType());
        assertEquals("/old", r.oldPath());
    }

    @Test
    void analysisResponse() {
        AnalysisResponse r = new AnalysisResponse(
                UUID.randomUUID(), UUID.randomUUID(), AnalysisType.ARCHITECTURE_REVIEW,
                "intent-v1", "1", AnalysisStatus.COMPLETED,
                Instant.now(), Instant.now(), Instant.now(), Instant.now());
        assertEquals(AnalysisType.ARCHITECTURE_REVIEW, r.type());
        assertEquals(AnalysisStatus.COMPLETED, r.status());
    }

    @Test
    void selectedKnowledgeInsightSnapshot() {
        SelectedKnowledge.InsightSnapshot s = new SelectedKnowledge.InsightSnapshot(
                UUID.randomUUID(), UUID.randomUUID(),
                InsightType.RECOMMENDATION, InsightSeverity.WARNING,
                "title", "content");
        assertEquals("title", s.title());
    }
}
