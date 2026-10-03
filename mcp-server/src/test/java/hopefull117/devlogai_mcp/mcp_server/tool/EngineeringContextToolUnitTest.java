package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidenceContent;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidenceSymbols;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextMetadata;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringSymbolDeclaration;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringSymbolLocation;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringSymbolParameter;
import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.contracts.engineeringcontext.ContextSection;
import com.hopeful117.devlogai.contracts.engineeringcontext.ContextRequestEcho;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectNote;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineeringContextToolUnitTest {

    private DevlogProjectContextClient devlogProjectContextClient;
    private EngineeringContextTool engineeringContextTool;

    @BeforeEach
    void setUp() {
        devlogProjectContextClient = mock(DevlogProjectContextClient.class);
        engineeringContextTool = new EngineeringContextTool(
                devlogProjectContextClient,
                new tools.jackson.databind.ObjectMapper()
        );
    }

    @Test
    void shouldReturnEngineeringContextJson() throws Exception {
        var projectNote = new ProjectNote(
                UUID.fromString("c2cbc6d0-8c49-461a-9b8c-0d4f5b6e7a8f"),
                "CONSTRAINT",
                "Technical constraints",
                "- Java 17\n- PostgreSQL 15+\n- Kubernetes 1.27+\n- Git repository with adr/ directory",
                "ACTIVE",
                Instant.now()
        );

        var projectContext = new ProjectContext(
                UUID.fromString("c2cbc6d0-8c49-461a-9b8c-0d4f5b6e7a8f"),
                "devlog-ai",
                "devlog-ai",
                "DevLog AI - Engineering project tracking",
                "ACTIVE",
                List.of(projectNote)
        );

        var evidence = List.of(
                new EngineeringEvidence(
                        "CHANGED_FILE",
                        "COMMIT_DIFF",
                        "Modified project-context-inputs-section.html to use ngx-markdown renderer",
                        "GIT",
                        "project-context-inputs-section.html",
                        "a1b2c3d4e5f67890abcdef1234567890abcdef12",
                        "git:source:a1b2c3d4e5f67890abcdef1234567890abcdef12",
                        95,
                        "SELECTED_BY_RANK",
                        Instant.parse("2026-08-20T10:00:00Z"),
                        List.of("diff:a1b2c3d4e5f67890abcdef1234567890abcdef12:project-context-inputs-section.html"),
                        Map.of("collectorId", "commit-diff"),
                        null,
                        null,
                        "devlog://projects/devlog-ai/commits/a1b2c3d4e5f67890abcdef1234567890abcdef12",
                        TrustTier.TECHNICAL_EVIDENCE
                )
        );

        var metadata = new EngineeringContextMetadata(
                42,
                15,
                false,
                2100,
                "deadbeef1234567890deadbeef1234567890deadbeef",
                List.of("EVIDENCE_SUMMARY_TRUNCATED"),
                null
        );

        var engineeringContext = new EngineeringContext(
                projectContext,
                "Investigate why Project Notes Markdown is displayed incorrectly.",
                evidence,
                metadata,
                List.of(),
                null
        );

        when(devlogProjectContextClient.getEngineeringContext(
                        "devlog-ai",
                        "Investigate why Project Notes Markdown is displayed incorrectly.",
                        List.of(),
                        null
                ))
                .thenReturn(engineeringContext);

        String result = engineeringContextTool.getEngineeringContext(
                "devlog-ai",
                "Investigate why Project Notes Markdown is displayed incorrectly.",
                List.of(),
                null
        );

        // Verify JSON contains expected fields
        assert result.contains("devlog-ai") : "JSON should contain project slug";
        assert result.contains("Investigate why Project Notes Markdown is displayed incorrectly.")
                : "JSON should contain the intent";
        assert result.contains("CHANGED_FILE") : "JSON should contain evidence kind";
        assert result.contains("GIT") : "JSON should contain sourceType";
        assert result.contains("95") : "JSON should contain relevanceScore";
        assert result.contains("SELECTED_BY_RANK") : "JSON should contain selectionReason";
        assert result.contains("42") : "JSON should contain candidateCount";
        assert result.contains("15") : "JSON should contain selectedCount";
        assert result.contains("false") : "JSON should contain truncated=false";
        assert result.contains("deadbeef1234567890deadbeef1234567890deadbeef")
                : "JSON should contain contextDigest";
        assert result.contains("occurredAt") : "JSON should contain evidence timestamp";
        assert result.contains("2026-08-20T10:00:00Z") : "JSON should contain ISO-8601 timestamp";
        assert result.contains("relatedReferences") : "JSON should contain related references";
        assert result.contains("extractionMetadata") : "JSON should contain extraction metadata";
        assert result.contains("collectorId") : "JSON should contain collector provenance";
        assert result.contains("warnings") : "JSON should contain warnings";
        assert result.contains("EVIDENCE_SUMMARY_TRUNCATED") : "JSON should contain warning value";
        assert result.contains("\"resource\":\"devlog://projects/devlog-ai/commits/")
                : "JSON should contain the resource URI";

        // Verify the client was called with correct arguments
        Mockito.verify(devlogProjectContextClient).getEngineeringContext(
                "devlog-ai",
                "Investigate why Project Notes Markdown is displayed incorrectly.",
                List.of(),
                null
        );
    }

    @Test
    void shouldPreserveEnrichedEvidenceFieldsAtMcpBoundary() throws Exception {
        var evidence = new EngineeringEvidence(
                "SOURCE_FILE",
                "RELATED_SOURCE_CODE",
                "Engineering context controller",
                "REPOSITORY_STRUCTURE",
                "backend/src/main/java/EngineeringContextController.java",
                "source-id-1",
                "file:backend/src/main/java/EngineeringContextController.java",
                88,
                "SELECTED_BY_RANK",
                Instant.parse("2026-08-01T10:15:30Z"),
                List.of("diff:abc123:backend/src/main/java/EngineeringContextController.java"),
                Map.of("resolvedRevision", "revision-1"),
                new EngineeringEvidenceContent(
                        "TRUNCATED", "class Example {\n", "CONTENT_ENRICHMENT_TRUNCATED", "revision-1"),
                new EngineeringEvidenceSymbols(
                        "EXTRACTED", false, 1, 1, "java-declaration-extractor", "v1", "revision-1",
                        List.of(new EngineeringSymbolDeclaration(
                                "METHOD", "getEngineeringContext", "EngineeringContextController",
                                List.of("public"), "ResponseEntity<EngineeringContext>",
                                List.of(new EngineeringSymbolParameter("String", "projectSlug")),
                                List.of("@GetMapping"), new EngineeringSymbolLocation(15, 4, 19, 5)))),
                null,
                TrustTier.TECHNICAL_EVIDENCE
        );
        var context = new EngineeringContext(
                new ProjectContext(
                        UUID.randomUUID(), "devlog-ai", "devlog-ai", "DevLog AI", "ACTIVE", List.of()),
                "Inspect enriched context",
                List.of(evidence),
                new EngineeringContextMetadata(
                        1, 1, true, 4800, "context-digest", List.of("CONTENT_ENRICHMENT_TRUNCATED"), null),
                List.of(),
                null
        );

        when(devlogProjectContextClient.getEngineeringContext(
                "devlog-ai", "Inspect enriched context", List.of(), null)).thenReturn(context);

        var json = new tools.jackson.databind.ObjectMapper().readTree(
                engineeringContextTool.getEngineeringContext("devlog-ai", "Inspect enriched context", List.of(), null));

        assertThat(json.at("/evidence/0/content/text").asText()).isEqualTo("class Example {\n");
        assertThat(json.at("/evidence/0/content/revision").asText()).isEqualTo("revision-1");
        assertThat(json.at("/evidence/0/symbols/extractorId").asText())
                .isEqualTo("java-declaration-extractor");
        assertThat(json.at("/evidence/0/symbols/declarations/0/name").asText())
                .isEqualTo("getEngineeringContext");
        assertThat(json.at("/evidence/0/symbols/declarations/0/location/beginLine").asInt())
                .isEqualTo(15);
        assertThat(json.at("/evidence/0/occurredAt").asText())
                .isEqualTo("2026-08-01T10:15:30Z");
        assertThat(json.at("/metadata/warnings/0").asText())
                .isEqualTo("CONTENT_ENRICHMENT_TRUNCATED");
    }
}
