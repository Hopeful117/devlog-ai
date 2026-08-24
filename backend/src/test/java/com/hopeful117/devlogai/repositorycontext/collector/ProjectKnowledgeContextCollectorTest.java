package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.repositorycontext.*;
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
        assertEquals("v2", collector.collectorVersion());
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
                "DECISION", "decision:" + decisionId);
    }

    @Test
    void shouldCollectDecisionsAsAdrEvidenceWithRationale() {
        var collector = createCollector();
        UUID decisionId = UUID.randomUUID();
        var decision = new AnalysisContext.DecisionSnapshot(
                decisionId, "Use PostgreSQL", "Need database", "PostgreSQL chosen", "ACID", "Migration needed", Instant.now());
        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(decision), List.of(), List.of()));


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        var evidence = result.getFirst();
        assertEquals(RepositoryContextLayer.ADR, evidence.layer());
        assertEquals("DECISION", evidence.kind());
        assertEquals("decision:" + decisionId, evidence.reference());
        assertTrue(evidence.summary().contains("Use PostgreSQL"));
        assertTrue(evidence.summary().contains("PostgreSQL chosen"));
        assertTrue(evidence.summary().contains("ACID"));
    }

    @Test
    void shouldCollectDecisionsAsAdrEvidenceWithoutRationale() {
        var collector = createCollector();
        UUID decisionId = UUID.randomUUID();
        var decision = new AnalysisContext.DecisionSnapshot(
                decisionId, "Use SQLite", "Need lightweight database", "SQLite chosen", null, null, Instant.now());
        var request = createRequest(new AnalysisContext(
                null, testAnalysis(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(decision), List.of(), List.of()));


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        var evidence = result.getFirst();
        assertEquals(RepositoryContextLayer.ADR, evidence.layer());
        assertEquals("DECISION", evidence.kind());
        assertEquals("decision:" + decisionId, evidence.reference());
        assertTrue(evidence.summary().contains("Use SQLite"));
        assertTrue(evidence.summary().contains("SQLite chosen"));
        assertFalse(evidence.summary().contains("— ACID"));
        assertFalse(evidence.summary().contains("— null"));
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
                "MILESTONE", "milestone:" + milestoneId);
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
                "INSIGHT", "insight:" + insightId);
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
                "ANALYSIS", "analysis:" + relatedId);
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
                "ARTIFACT", "artifact:" + artifactId);
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
                "ARTIFACT", "artifact:" + artifactId);
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

    @Test
    void shouldCollectEngineeringStoryEvidenceWithCommits() {
        var collector = createCollector();
        UUID storyId = UUID.randomUUID();
        var story = new ProjectContextSnapshot.EngineeringStorySnapshot(
                storyId, UUID.randomUUID(), 42, "Expose Engineering Context through MCP",
                "COMPLETED", "src/main/java/com/example/EngineContext.java",
                "base-abc123", "target-def456", Instant.now(), null);

        var analysisContext = new AnalysisContext(
                null, // project
                new AnalysisContext.AnalysisSnapshot(
                        UUID.randomUUID(), AnalysisType.ARCHITECTURE_REVIEW, "intent-v1", "v1",
                        AnalysisStatus.IN_PROGRESS, null, null, Instant.now()),
                null, // projectProfile
                List.of(), // facts
                List.of(), // observations
                List.of(), // recentKnowledgeEvents
                List.of(), // relatedAnalyses
                List.of(), // architectureArtifacts
                List.of(), // relatedDecisions
                List.of(), // recentMilestones
                List.of(), // validatedProposals
                null, // evolutionContext
                List.of(), // validatedEngineeringEvents
                List.of(), // openChallenges
                List.of(), // knowledgeRelations
                List.of(story)); // engineeringStories

        var request = createRequest(analysisContext);


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        var evidence = result.getFirst();
        assertEquals(RepositoryContextLayer.ROADMAP, evidence.layer());
        assertEquals("ENGINEERING_STORY", evidence.kind());
        assertEquals("story:" + storyId, evidence.reference());
        assertTrue(evidence.summary().contains("Expose Engineering Context"));
        assertEquals(story.createdAt(), evidence.occurredAt());
        assertEquals("CORE_KNOWLEDGE", evidence.provenance().sourceType());
        assertEquals("src/main/java/com/example/EngineContext.java", evidence.provenance().originatingFile());
        assertEquals(story.id().toString(), evidence.provenance().identifier());
        assertEquals("42", evidence.extractionMetadata().get("storyNumber"));
        assertEquals("COMPLETED", evidence.extractionMetadata().get("status"));
        assertEquals("base-abc123", evidence.extractionMetadata().get("baseCommit"));
        assertEquals("target-def456", evidence.extractionMetadata().get("targetCommit"));
    }

    @Test
    void shouldCollectEngineeringStoryEvidenceWithNullCommitValues() {
        var collector = createCollector();
        UUID storyId = UUID.randomUUID();
        var story = new ProjectContextSnapshot.EngineeringStorySnapshot(
                storyId, UUID.randomUUID(), 1, "Simple Story",
                "REGISTERED", "src/main/java/SomeFile.java", null, null, Instant.now(), null);

        var analysisContext = new AnalysisContext(
                null, // project
                new AnalysisContext.AnalysisSnapshot(
                        UUID.randomUUID(), AnalysisType.ARCHITECTURE_REVIEW, "intent-v1", "v1",
                        AnalysisStatus.IN_PROGRESS, null, null, Instant.now()),
                null, // projectProfile
                List.of(), // facts
                List.of(), // observations
                List.of(), // recentKnowledgeEvents
                List.of(), // relatedAnalyses
                List.of(), // architectureArtifacts
                List.of(), // relatedDecisions
                List.of(), // recentMilestones
                List.of(), // validatedProposals
                null, // evolutionContext
                List.of(), // validatedEngineeringEvents
                List.of(), // openChallenges
                List.of(), // knowledgeRelations
                List.of(story)); // engineeringStories

        var request = createRequest(analysisContext);


        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        var evidence = result.getFirst();
        assertEquals(RepositoryContextLayer.ROADMAP, evidence.layer());
        assertEquals("ENGINEERING_STORY", evidence.kind());
        assertEquals("story:" + storyId, evidence.reference());
        assertTrue(evidence.summary().contains("Simple Story"));
        assertEquals(story.createdAt(), evidence.occurredAt());
        assertEquals("CORE_KNOWLEDGE", evidence.provenance().sourceType());
        assertEquals("src/main/java/SomeFile.java", evidence.provenance().originatingFile());
        assertEquals(story.id().toString(), evidence.provenance().identifier());
        assertEquals("1", evidence.extractionMetadata().get("storyNumber"));
        assertEquals("REGISTERED", evidence.extractionMetadata().get("status"));
        assertNull(evidence.extractionMetadata().get("baseCommit"));
        assertNull(evidence.extractionMetadata().get("targetCommit"));
    }

    @Test
    void shouldCollectValidatedEngineeringEventsAsGitHistoryEvidence() {
        var collector = createCollector();
        UUID eventId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-01T12:00:00Z");
        var event = new ProjectContextSnapshot.EngineeringEventSnapshot(
                eventId, "FEATURE_INTRODUCTION", "Add markdown rendering",
                "Introduced ngx-markdown renderer for project notes", sourceId,
                "base-abc123", "target-def456", occurredAt, proposalId);

        var analysisContext = fullAnalysisContext(List.of(), List.of(), List.of(),
                List.of(), List.of(event), List.of(), List.of());
        var request = createRequest(analysisContext);

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        var evidence = result.getFirst();
        assertEquals(RepositoryContextLayer.GIT_HISTORY, evidence.layer());
        assertEquals("ENGINEERING_EVENT", evidence.kind());
        assertEquals("event:" + eventId, evidence.reference());
        assertTrue(evidence.summary().contains("Add markdown rendering"));
        assertTrue(evidence.summary().contains("ngx-markdown renderer"));
        assertEquals(occurredAt, evidence.occurredAt());
        assertEquals("CORE_KNOWLEDGE", evidence.provenance().sourceType());
        assertEquals(sourceId.toString(), evidence.provenance().repositoryLocation());
        assertEquals(eventId.toString(), evidence.provenance().identifier());
        assertEquals(List.of(
                        "git:" + sourceId + ":base-abc123",
                        "git:" + sourceId + ":target-def456"),
                evidence.relatedReferences());
        assertEquals("FEATURE_INTRODUCTION", evidence.extractionMetadata().get("category"));
        assertEquals("base-abc123", evidence.extractionMetadata().get("baseCommit"));
        assertEquals("target-def456", evidence.extractionMetadata().get("targetCommit"));
        assertEquals(proposalId.toString(), evidence.extractionMetadata().get("proposalId"));
    }

    @Test
    void shouldCollectEngineeringEventsWithNullCommitsAsCleanAbsence() {
        var collector = createCollector();
        UUID eventId = UUID.randomUUID();
        var event = new ProjectContextSnapshot.EngineeringEventSnapshot(
                eventId, "BUG_RESOLUTION", "Fix import flow", null,
                null, null, null, Instant.now(), null);

        var analysisContext = fullAnalysisContext(List.of(), List.of(), List.of(),
                List.of(), List.of(event), List.of(), List.of());
        var request = createRequest(analysisContext);

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        var evidence = result.getFirst();
        assertTrue(evidence.relatedReferences().isEmpty());
        assertNull(evidence.provenance().repositoryLocation());
        assertFalse(evidence.extractionMetadata().containsKey("baseCommit"));
        assertFalse(evidence.extractionMetadata().containsKey("targetCommit"));
        assertFalse(evidence.extractionMetadata().containsKey("proposalId"));
    }

    @Test
    void shouldCollectOpenChallengesAsRoadmapEvidence() {
        var collector = createCollector();
        UUID challengeId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-20T09:00:00Z");
        var challenge = new ProjectContextSnapshot.ChallengeSnapshot(
                challengeId, "Slow context pipeline",
                "Repository synchronization dominates latency",
                "HIGH", "OPEN", null, createdAt);

        var analysisContext = fullAnalysisContext(List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(challenge), List.of());
        var request = createRequest(analysisContext);

        List<RepositoryEvidence> result = collector.collect(request);

        assertEquals(1, result.size());
        var evidence = result.getFirst();
        assertEquals(RepositoryContextLayer.ROADMAP, evidence.layer());
        assertEquals("CHALLENGE", evidence.kind());
        assertEquals("challenge:" + challengeId, evidence.reference());
        assertTrue(evidence.summary().contains("Slow context pipeline"));
        assertTrue(evidence.summary().contains("Repository synchronization dominates latency"));
        assertEquals(createdAt, evidence.occurredAt());
        assertEquals(challengeId.toString(), evidence.provenance().identifier());
        assertEquals("CORE_KNOWLEDGE", evidence.provenance().sourceType());
        assertEquals("OPEN", evidence.extractionMetadata().get("status"));
        assertEquals("HIGH", evidence.extractionMetadata().get("impact"));
    }

    private AnalysisContext fullAnalysisContext(
            List<AnalysisContext.DecisionSnapshot> decisions,
            List<AnalysisContext.MilestoneSnapshot> milestones,
            List<AnalysisContext.AnalysisSnapshot> relatedAnalyses,
            List<AnalysisContext.ArtifactSnapshot> artifacts,
            List<ProjectContextSnapshot.EngineeringEventSnapshot> engineeringEvents,
            List<ProjectContextSnapshot.ChallengeSnapshot> openChallenges,
            List<ProjectContextSnapshot.EngineeringStorySnapshot> stories
    ) {
        return new AnalysisContext(
                null,
                testAnalysis(),
                null,
                List.of(),
                List.of(),
                List.of(),
                relatedAnalyses,
                artifacts,
                decisions,
                milestones,
                List.of(),
                null,
                engineeringEvents,
                openChallenges,
                List.of(),
                stories);
    }

    private void assertEvidence(RepositoryEvidence first, RepositoryContextLayer repositoryContextLayer, String decision, String s) {

    }
    }

