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
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceCriterion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectKnowledgeContextCollectorTest {

    @Mock private EvidenceFactory evidenceFactory;

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

        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidenceFactory.create(any(), eq(RepositoryContextLayer.ADR), eq("DECISION"),
                contains("decision:"), anyString(), any(), anyList(), isNull(), isNull(),
                eq(decisionId.toString()), anyInt()))
                .thenReturn(evidence);

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        verify(evidenceFactory).create(any(), eq(RepositoryContextLayer.ADR), eq("DECISION"),
                eq("decision:" + decisionId), contains("Use PostgreSQL"), any(), anyList(),
                isNull(), isNull(), eq(decisionId.toString()), anyInt());
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

        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidenceFactory.create(any(), eq(RepositoryContextLayer.ROADMAP), eq("MILESTONE"),
                contains("milestone:"), anyString(), any(), anyList(), isNull(), isNull(),
                eq(milestoneId.toString()), anyInt()))
                .thenReturn(evidence);

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        verify(evidenceFactory).create(any(), eq(RepositoryContextLayer.ROADMAP), eq("MILESTONE"),
                eq("milestone:" + milestoneId), contains("v2.0 Release"), any(), anyList(),
                isNull(), isNull(), eq(milestoneId.toString()), anyInt());
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

        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidenceFactory.create(any(), eq(RepositoryContextLayer.VALIDATED_INSIGHT), eq("INSIGHT"),
                contains("insight:"), anyString(), any(), anyList(), isNull(), isNull(),
                eq(insightId.toString()), anyInt()))
                .thenReturn(evidence);

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        verify(evidenceFactory).create(any(), eq(RepositoryContextLayer.VALIDATED_INSIGHT), eq("INSIGHT"),
                eq("insight:" + insightId), contains("Key Insight"), any(), anyList(),
                isNull(), isNull(), eq(insightId.toString()), anyInt());
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

        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidenceFactory.create(any(), eq(RepositoryContextLayer.PREVIOUS_ANALYSIS), eq("ANALYSIS"),
                contains("analysis:"), anyString(), any(), anyList(), isNull(), isNull(),
                eq(relatedId.toString()), anyInt()))
                .thenReturn(evidence);

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        verify(evidenceFactory).create(any(), eq(RepositoryContextLayer.PREVIOUS_ANALYSIS), eq("ANALYSIS"),
                eq("analysis:" + relatedId), contains("ARCHITECTURE_REVIEW"), any(), anyList(),
                isNull(), isNull(), eq(relatedId.toString()), anyInt());
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

        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidenceFactory.create(any(), eq(RepositoryContextLayer.PROJECT_DOCUMENTATION), eq("ARTIFACT"),
                contains("artifact:"), anyString(), any(), anyList(), isNull(),
                eq("docs/adr/001.md"), eq(artifactId.toString()), anyInt()))
                .thenReturn(evidence);

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        verify(evidenceFactory).create(any(), eq(RepositoryContextLayer.PROJECT_DOCUMENTATION), eq("ARTIFACT"),
                eq("artifact:" + artifactId), contains("ADR-001"), any(), anyList(),
                isNull(), eq("docs/adr/001.md"), eq(artifactId.toString()), anyInt());
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

        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        when(evidenceFactory.create(any(), eq(RepositoryContextLayer.PROJECT_DOCUMENTATION), eq("ARTIFACT"),
                contains("artifact:"), anyString(), any(), eq(List.of()), isNull(),
                isNull(), eq(artifactId.toString()), anyInt()))
                .thenReturn(evidence);

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        verify(evidenceFactory).create(any(), eq(RepositoryContextLayer.PROJECT_DOCUMENTATION), eq("ARTIFACT"),
                eq("artifact:" + artifactId), contains("ADR-002"), any(), eq(List.of()),
                isNull(), isNull(), eq(artifactId.toString()), anyInt());
    }

    @Test
    void shouldReturnEmptyListWhenNoKnowledgeItems() {
        var collector = createCollector();
        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of()));

        List<RepositoryEvidence> result = collector.collect(request);

        assertTrue(result.isEmpty());
        verifyNoInteractions(evidenceFactory);
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

        when(evidenceFactory.create(any(), any(), any(), anyString(), anyString(), any(),
                anyList(), isNull(), any(), anyString(), anyInt()))
                .thenReturn(mock(RepositoryEvidence.class));

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(4, result.size());
        verify(evidenceFactory, times(4)).create(any(), any(), any(), anyString(), anyString(), any(),
                anyList(), isNull(), any(), anyString(), anyInt());
    }
}
