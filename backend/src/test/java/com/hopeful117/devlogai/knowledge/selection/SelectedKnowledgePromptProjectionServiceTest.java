package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SelectedKnowledgePromptProjectionServiceTest {

    private final SelectedKnowledgePromptProjectionService service =
            new SelectedKnowledgePromptProjectionService(new ObjectMapper());

    @Test
    void shouldCompactRepositoryContextForPromptPayload() {
        RepositoryEvidence evidence = new RepositoryEvidence(
                RepositoryContextLayer.CURRENT_ANALYSIS,
                "FILE",
                "backend/src/main/java/App.java",
                "Application entry point",
                Instant.parse("2026-08-12T20:00:00Z"),
                EvidenceScore.unscored(),
                List.of("pom.xml"),
                new RepositoryEvidence.EvidenceProvenance(
                        "FILE", "repo", "backend/src/main/java/App.java", "app"),
                Map.of("extractor", "test"),
                123,
                List.of("high score"),
                new RepositoryEvidenceContent(
                        RepositoryEvidenceContent.Status.COMPLETE,
                        "class App {}",
                        "full content",
                        "content-policy",
                        "v1",
                        "r1",
                        "alloc-policy",
                        "v1",
                        1,
                        List.of("kept")
                ),
                new RepositoryEvidenceSymbols(
                        RepositoryEvidenceSymbols.Status.EXTRACTED,
                        "symbols available",
                        "symbols-policy",
                        "v1",
                        "javaparser",
                        "3.27.0",
                        "r1",
                        1,
                        List.of("top ranked"),
                        false,
                        1,
                        1,
                        List.of(new RepositoryEvidenceSymbols.JavaDeclaration(
                                RepositoryEvidenceSymbols.Kind.CLASS,
                                "App",
                                null,
                                List.of("public"),
                                null,
                                List.of(),
                                List.of(),
                                new RepositoryEvidenceSymbols.SourceLocation(1, 1, 1, 10)
                        ))
                )
        );
        RepositoryContext repositoryContext = new RepositoryContext(
                "repository-context-engine-v1",
                ContextProfile.PROJECT_STATE,
                List.of("project-state-v1"),
                "context-intelligence-v1",
                List.of("selected for understanding refresh"),
                List.of(evidence),
                Map.of(RepositoryContextLayer.CURRENT_ANALYSIS, 1),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                456,
                10,
                9,
                false,
                List.of(new RepositoryContext.SelectionDecision(
                        "backend/src/main/java/App.java", true, "top candidate", 100, 123
                )),
                List.of("warn"),
                "d".repeat(64)
        );
        SelectedKnowledge selectedKnowledge = new SelectedKnowledge(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "DevLog", "devlog-ai",
                        "desc", com.hopeful117.devlogai.project.entity.ProjectStatus.ACTIVE),
                null,
                new ProjectProfileResponse(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "v1", "r1", Instant.now(), null, Map.of(),
                        new ProjectProfileResponse.Completeness(
                                com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus.COMPLETE,
                                true, false, 0, 0, 1, 0, 0
                        ),
                        List.of(), "summary", List.of(), 0
                ),
                List.of(),
                List.of(),
                new SelectedKnowledge.DiagnosticSnapshot(true, false, 0, 0),
                List.of(),
                List.of(),
                List.of(),
                repositoryContext,
                null,
                new SelectedKnowledge.SelectionMetadata(
                        "knowledge-selection-v4",
                        List.of(),
                        0,
                        0,
                        new SelectedKnowledge.KnowledgeBudget(40, 25, 10, 5, 60),
                        "COMPLETE"
                ),
                "a".repeat(64)
        );

        Map<String, Object> projected = service.toMap(selectedKnowledge);

        @SuppressWarnings("unchecked")
        Map<String, Object> projectedRepositoryContext =
                (Map<String, Object>) projected.get("repositoryContext");
        assertNotNull(projectedRepositoryContext);
        assertEquals("repository-context-engine-v1",
                projectedRepositoryContext.get("contextVersion"));
        assertEquals("PROJECT_STATE", projectedRepositoryContext.get("profile"));
        assertEquals(List.of("warn"), projectedRepositoryContext.get("warnings"));
        assertFalse(projectedRepositoryContext.containsKey("selectionDecisions"));
        assertFalse(projectedRepositoryContext.containsKey("selectedByLayer"));
        assertFalse(projectedRepositoryContext.containsKey("diagnostics"));
        assertFalse(projectedRepositoryContext.containsKey("usedTokens"));

        @SuppressWarnings("unchecked")
        Map<String, Object> projectedEvidence =
                ((List<Map<String, Object>>) projectedRepositoryContext.get("evidence")).getFirst();
        assertEquals("backend/src/main/java/App.java", projectedEvidence.get("reference"));
        assertFalse(projectedEvidence.containsKey("score"));
        assertFalse(projectedEvidence.containsKey("provenance"));
        assertFalse(projectedEvidence.containsKey("rankingReasons"));
        assertFalse(projectedEvidence.containsKey("extractionMetadata"));
    }

    @Test
    void shouldOmitSelectedInsightIdentifiersFromPromptPayload() {
        UUID insightId = UUID.randomUUID();
        UUID sourceAnalysisId = UUID.randomUUID();
        SelectedKnowledge selectedKnowledge = new SelectedKnowledge(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "DevLog", "devlog-ai",
                        "desc", com.hopeful117.devlogai.project.entity.ProjectStatus.ACTIVE),
                null,
                new ProjectProfileResponse(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "v1", "r1", Instant.now(), null, Map.of(),
                        new ProjectProfileResponse.Completeness(
                                com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus.COMPLETE,
                                true, false, 0, 0, 1, 0, 0
                        ),
                        List.of(), "summary", List.of(), 0
                ),
                List.of(),
                List.of(),
                new SelectedKnowledge.DiagnosticSnapshot(true, false, 0, 0),
                List.of(new SelectedKnowledge.InsightSnapshot(
                        insightId,
                        sourceAnalysisId,
                        InsightType.ARCHITECTURAL,
                        InsightSeverity.INFO,
                        "Controllers",
                        "The project exposes REST controllers."
                )),
                List.of(),
                List.of(),
                null,
                null,
                new SelectedKnowledge.SelectionMetadata(
                        "knowledge-selection-v4",
                        List.of(),
                        1,
                        0,
                        new SelectedKnowledge.KnowledgeBudget(40, 25, 10, 5, 60),
                        "COMPLETE"
                ),
                "a".repeat(64)
        );

        Map<String, Object> projected = service.toMap(selectedKnowledge);

        @SuppressWarnings("unchecked")
        Map<String, Object> projectedInsight =
                ((List<Map<String, Object>>) projected.get("selectedInsights")).getFirst();
        assertEquals("ARCHITECTURAL", projectedInsight.get("type"));
        assertEquals("INFO", projectedInsight.get("severity"));
        assertEquals("Controllers", projectedInsight.get("title"));
        assertFalse(projectedInsight.containsKey("id"));
        assertFalse(projectedInsight.containsKey("analysisId"));
    }
}
