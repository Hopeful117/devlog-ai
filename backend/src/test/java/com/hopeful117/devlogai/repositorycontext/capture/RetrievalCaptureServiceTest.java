package com.hopeful117.devlogai.repositorycontext.capture;

import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalCaptureServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private RetrievalCaptureService buildService(boolean enabled, Path outputDir) {
        RetrievalCaptureService service = new RetrievalCaptureService(objectMapper);
        ReflectionTestUtils.setField(service, "enabled", enabled);
        ReflectionTestUtils.setField(service, "outputDir", outputDir.toString());
        return service;
    }

    // --- disabled flag ---

    @Test
    void disabled_flag_no_file_written() throws IOException {
        RetrievalCaptureService service = buildService(false, tempDir);

        service.capture("What is the architecture?", "devlog-ai", "rev-001",
                minimalContext(List.of()), List.of(), 100L);

        assertThat(Files.list(tempDir)).isEmpty();
    }

    // --- enabled writes valid json ---

    @Test
    void enabled_writes_json_with_required_fields() throws IOException {
        RetrievalCaptureService service = buildService(true, tempDir);
        RepositoryEvidence candidate = candidateEvidence("doc:ADR-067@rev-001", "STRUCTURAL");

        service.capture("What is the architecture?", "devlog-ai", "rev-001",
                minimalContext(List.of()), List.of(candidate), 250L);

        List<Path> files = Files.list(tempDir).toList();
        assertThat(files).hasSize(1);

        String json = Files.readString(files.getFirst());
        assertThat(json).contains(RetrievalCaptureService.PROTOCOL_VERSION);
        assertThat(json).contains("devlog-ai");
        assertThat(json).contains("rev-001");
        assertThat(json).contains("What is the architecture?");
    }

    @Test
    void enabled_candidate_count_matches_ranked_list_size() throws IOException {
        RetrievalCaptureService service = buildService(true, tempDir);
        List<RepositoryEvidence> ranked = List.of(
                candidateEvidence("ref:a@rev", "STRUCTURAL"),
                candidateEvidence("ref:b@rev", "SEMANTIC_RETRIEVAL")
        );

        service.capture("query", "proj", "rev", minimalContext(List.of()), ranked, 50L);

        String json = Files.readString(Files.list(tempDir).findFirst().orElseThrow());
        assertThat(json).contains("\"candidateCount\":2");
    }

    @Test
    void capture_failure_does_not_propagate() {
        RetrievalCaptureService service = buildService(true,
                Path.of("/dev/null/nonexistent/dir"));

        assertThatCode(() ->
                service.capture("q", "p", "r", minimalContext(List.of()), List.of(), 10L)
        ).doesNotThrowAnyException();
    }

    // --- helpers ---

    private RepositoryContext minimalContext(List<RepositoryEvidence> selected) {
        RepositoryContext ctx = mock(RepositoryContext.class);
        when(ctx.evidence()).thenReturn(selected);
        when(ctx.selectionDecisions()).thenReturn(List.of());
        when(ctx.candidateCount()).thenReturn(selected.size());
        when(ctx.usedTokens()).thenReturn(0);
        when(ctx.truncated()).thenReturn(false);
        when(ctx.contextDigest()).thenReturn("test-digest");
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private RepositoryEvidence candidateEvidence(String reference, String sourceType) {
        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidence.reference()).thenReturn(reference);
        when(evidence.kind()).thenReturn("ADR_DOCUMENT");
        when(evidence.relevanceScore()).thenReturn(80);
        when(evidence.estimatedTokens()).thenReturn(100);
        RepositoryEvidence.EvidenceProvenance provenance =
                mock(RepositoryEvidence.EvidenceProvenance.class);
        when(provenance.sourceType()).thenReturn(sourceType);
        when(evidence.provenance()).thenReturn(provenance);
        when(evidence.extractionMetadata()).thenReturn(Map.of("trustTier", "HUMAN_AUTHORED"));
        return evidence;
    }
}
