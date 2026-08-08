package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextProfileDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectKnowledgeContextCollectorTest {

    private final EvidenceFactory evidenceFactory = new EvidenceFactory();

    private ProjectKnowledgeContextCollector createCollector() {
        return new ProjectKnowledgeContextCollector(evidenceFactory);
    }

    private ContextRequest createRequest(AnalysisContext ctx) {
        return createRequest(ctx, List.of());
    }

    private ContextRequest createRequest(AnalysisContext ctx, List<Insight> insights) {
        var plan = new ContextPlan("v1",
                List.of(new ContextProfileDefinition("full", ContextProfile.PROJECT_STATE, "v1",
                        Map.of(), List.of(), 0, 1)),
                Map.of(), List.of(), 0, List.of());
        return new ContextRequest(ctx, null, null, insights, plan,
                new RepositoryContext.ContextBudget(50, 200, 10, 10000));
    }

    private AnalysisContext.AnalysisSnapshot testAnalysis() {
        return new AnalysisContext.AnalysisSnapshot(
                UUID.randomUUID(), AnalysisType.ARCHITECTURE_REVIEW, "intent-v1", "v1",
                AnalysisStatus.IN_PROGRESS, null, null, Instant.now());
    }

    @Test
    void shouldReturnCorrectMetadata() {
        var collector = createCollector();
        assertEquals("project-knowledge", collector.collectorId());
        assertEquals("v1", collector.collectorVersion());
    }

    @Test
    void shouldCollectDecisionsAsAdrEvidence() {
        var collector = createCollector();
        UUID decisionId = UUID.randomUUID();
        var decision = new AnalysisContext.DecisionSnapshot(
                decisionId, "Use PostgreSQL", "Need database", "PostgreSQL chosen", "ACID", "Migration needed", Instant.now());
        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(decision), List.of(), List.of()));


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        assertEvidence(result.getFirst(), RepositoryContextLayer.ADR,
                "DECISION", "decision:" + decisionId, "Use PostgreSQL");
    }

    @Test
    void shouldCollectMilestonesAsRoadmapEvidence() {
        var collector = createCollector();
        UUID milestoneId = UUID.randomUUID();
        var milestone = new AnalysisContext.MilestoneSnapshot(
                milestoneId, "v2.0 Release", "Major release", MilestoneStatus.IN_PROGRESS,
                Instant.now(), null);
        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(milestone), List.of()));


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        assertEvidence(result.getFirst(), RepositoryContextLayer.ROADMAP,
                "MILESTONE", "milestone:" + milestoneId, "v2.0 Release");
    }

    @Test
    void shouldCollectInsightsAsValidatedInsightEvidence() {
        var collector = createCollector();
        UUID insightId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();

        var analysis = com.hopeful117.devlogai.analysis.entity.Analysis.builder()
                .id(analysisId).build();

        Insight insight = mock(Insight.class);
        when(insight.getId()).thenReturn(insightId);
        when(insight.getTitle()).thenReturn("Key Insight");
        when(insight.getContent()).thenReturn("Important finding");
        when(insight.getCreatedAt()).thenReturn(Instant.now());
        when(insight.getAnalysis()).thenReturn(analysis);

        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of()), List.of(insight));


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        assertEvidence(result.getFirst(), RepositoryContextLayer.VALIDATED_INSIGHT,
                "INSIGHT", "insight:" + insightId, "Key Insight");
        assertEquals(List.of("analysis:" + analysisId), result.getFirst().relatedReferences());
    }

    @Test
    void shouldCollectRelatedAnalysesAsPreviousAnalysisEvidence() {
        var collector = createCollector();
        UUID relatedId = UUID.randomUUID();
        var related = new AnalysisContext.AnalysisSnapshot(
                relatedId, AnalysisType.ARCHITECTURE_REVIEW, "intent-v1", "v1",
                AnalysisStatus.COMPLETED, null, null, Instant.now());
        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(related), List.of(), List.of(), List.of(), List.of()));


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        assertEvidence(result.getFirst(), RepositoryContextLayer.PREVIOUS_ANALYSIS,
                "ANALYSIS", "analysis:" + relatedId, "ARCHITECTURE_REVIEW");
    }

    @Test
    void shouldCollectArchitectureArtifactsWithFileLocation() {
        var collector = createCollector();
        UUID artifactId = UUID.randomUUID();
        var artifact = new AnalysisContext.ArtifactSnapshot(
                artifactId, ArtifactType.DOCUMENTATION, "ADR-001", "docs/adr/001.md", "Architecture Decision", Instant.now());
        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(artifact), List.of(), List.of(), List.of()));


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        assertEvidence(result.getFirst(), RepositoryContextLayer.PROJECT_DOCUMENTATION,
                "ARTIFACT", "artifact:" + artifactId, "ADR-001");
        assertEquals("docs/adr/001.md", result.getFirst().provenance().originatingFile());
    }

    @Test
    void shouldCollectArtifactWithNullPathAsEmptyList() {
        var collector = createCollector();
        UUID artifactId = UUID.randomUUID();
        var artifact = new AnalysisContext.ArtifactSnapshot(
                artifactId, ArtifactType.DOCUMENTATION, "ADR-002", null, "No path artifact", Instant.now());
        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(artifact), List.of(), List.of(), List.of()));


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        assertEvidence(result.getFirst(), RepositoryContextLayer.PROJECT_DOCUMENTATION,
                "ARTIFACT", "artifact:" + artifactId, "ADR-002");
        assertTrue(result.getFirst().relatedReferences().isEmpty());
        assertNull(result.getFirst().provenance().originatingFile());
    }

    @Test
    void shouldReturnEmptyListWhenNoKnowledgeItems() {
        var collector = createCollector();
        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of()));

        List<RepositoryEvidence> result = collector.collect(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldCollectAllKnowledgeItemsTogether() {
        var collector = createCollector();
        UUID decisionId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();

        var decision = new AnalysisContext.DecisionSnapshot(
                decisionId, "Decision", "ctx", "choice", "rationale", "consequences", Instant.now());
        var milestone = new AnalysisContext.MilestoneSnapshot(
                milestoneId, "Milestone", "desc", MilestoneStatus.PLANNED, null, null);
        var related = new AnalysisContext.AnalysisSnapshot(
                relatedId, AnalysisType.ARCHITECTURE_REVIEW, "i", "v", AnalysisStatus.COMPLETED, null, null, Instant.now());
        var artifact = new AnalysisContext.ArtifactSnapshot(
                artifactId, ArtifactType.DOCUMENTATION, "ADR", "path", "desc", Instant.now());

        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(related), List.of(artifact), List.of(decision), List.of(milestone), List.of()));


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(4, result.size());
        assertEquals(List.of(
                        RepositoryContextLayer.ADR,
                        RepositoryContextLayer.ROADMAP,
                        RepositoryContextLayer.PREVIOUS_ANALYSIS,
                        RepositoryContextLayer.PROJECT_DOCUMENTATION),
                result.stream().map(RepositoryEvidence::layer).toList());
    }

    private void assertEvidence(RepositoryEvidence evidence,
            RepositoryContextLayer layer, String kind, String reference, String summaryFragment) {
        assertEquals(layer, evidence.layer());
        assertEquals(kind, evidence.kind());
        assertEquals(reference, evidence.reference());
        assertTrue(evidence.summary().contains(summaryFragment));
        assertEquals("project-knowledge", evidence.extractionMetadata().get("collectorId"));
    }
}
