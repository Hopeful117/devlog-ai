package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextMetadata;
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
}