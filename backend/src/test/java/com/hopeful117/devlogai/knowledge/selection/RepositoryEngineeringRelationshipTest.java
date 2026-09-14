package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.projectcontext.EngineeringRelationship;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepositoryEngineeringRelationshipTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void admitsChangesUsingExistingBoundedCapacityAndProjectsExplicitEdge() {
        var relationship = changes("abc123", "src/App.java");
        var context = context(List.of(relationship));
        var diagnostics = mock(AnalysisExecutionDiagnosticRepository.class);
        var insights = mock(InsightRepository.class);
        var mapper = mock(ObjectMapper.class);
        var repositoryContexts = mock(RepositoryContextService.class);
        when(diagnostics.findById(context.analysis().id())).thenReturn(java.util.Optional.of(
                com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic.builder()
                        .analysisId(context.analysis().id()).collectionComplete(true).build()));
        when(insights.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                eq(PROJECT), eq(List.of(InsightStatus.ACTIVE)), any())).thenReturn(List.of());
        when(repositoryContexts.build(eq(context), any(), isNull(), anyList(), anyList(), any()))
                .thenReturn(emptyRepositoryContext());
        when(mapper.writeValueAsString(any())).thenReturn("digest");

        var selected = new KnowledgeSelectionServiceImpl(
                diagnostics, insights, mapper, repositoryContexts, 15, 1)
                .select(context, architectureIntent(), null);

        assertEquals(List.of(relationship), selected.engineeringRelationships());
        assertTrue(selected.knowledgeRelations().isEmpty());
    }

    @Test
    void sharedProjectionKeepsDurableAndRepositoryRelationsSeparateButCoexisting() {
        UUID insightA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID insightB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        var durable = new com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.KnowledgeRelationSnapshot(
                UUID.randomUUID(), EntityType.INSIGHT, insightA, EntityType.INSIGHT,
                insightB, KnowledgeRelationType.RELATES_TO, null, null);
        var repository = changes("abc123", "src/App.java");
        var selected = new SelectedKnowledge(
                new AnalysisContext.ProjectSnapshot(PROJECT, "Project", "project", null,
                        ProjectStatus.ACTIVE), null, profile(), List.of(), List.of(),
                new SelectedKnowledge.DiagnosticSnapshot(true, false, 0, 0),
                List.of(new SelectedKnowledge.InsightSnapshot(insightA, UUID.randomUUID(),
                                com.hopeful117.devlogai.insight.entity.InsightType.ARCHITECTURAL,
                                InsightSeverity.INFO, "A", "A"),
                        new SelectedKnowledge.InsightSnapshot(insightB, UUID.randomUUID(),
                                com.hopeful117.devlogai.insight.entity.InsightType.ARCHITECTURAL,
                                InsightSeverity.INFO, "B", "B")), List.of(), List.of(),
                List.of(), List.of(durable), List.of(), null, null,
                new SelectedKnowledge.SelectionMetadata("test", List.of(), 0, 0,
                        new SelectedKnowledge.KnowledgeBudget(40, 25, 10, 5, 60), "COMPLETE"),
                "digest", List.of(EngineeringRelationship.fromKnowledge(durable), repository));

        var projection = new SelectedKnowledgePromptProjectionService(
                new ObjectMapper(), new SemanticSectionComposer()).project(selected);

        assertTrue(projection.relationshipHighlights().stream()
                .anyMatch(value -> value.relationType().equals("CHANGES")
                        && value.source().entityType().equals("COMMIT")
                        && value.target().entityType().equals("FILE")));
        assertTrue(projection.relationshipHighlights().stream()
                .anyMatch(value -> value.relationType().equals("RELATES_TO")));
    }

    private static EngineeringRelationship changes(String revision, String path) {
        var commit = new EngineeringRelationship.RepositoryCommitEndpoint(PROJECT, SOURCE, revision);
        var file = new EngineeringRelationship.RepositoryFileEndpoint(PROJECT, SOURCE, revision, path);
        return new EngineeringRelationship("changes:" + revision + ":" + path, commit, "CHANGES", file,
                EngineeringRelationship.Origin.REPOSITORY_DERIVED, TrustTier.TECHNICAL_EVIDENCE,
                revision, List.of("diff:" + revision + ":" + path));
    }

    private static EngineeringRelationship repository() {
        return changes("abc123", "src/App.java");
    }

    private static AnalysisContext context(List<EngineeringRelationship> relationships) {
        UUID analysisId = UUID.randomUUID();
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(PROJECT, "Project", "project", null, ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(analysisId, AnalysisType.ARCHITECTURE_REVIEW,
                        "architecture-overview", "v1", AnalysisStatus.IN_PROGRESS,
                        java.time.Instant.EPOCH, null, java.time.Instant.EPOCH),
                profile(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(),
                relationships);
    }

    private static ProjectProfileResponse profile() {
        return new ProjectProfileResponse(UUID.randomUUID(), PROJECT, UUID.randomUUID(),
                "v1", "test", java.time.Instant.EPOCH, "abc123", Map.of(),
                new ProjectProfileResponse.Completeness(
                        com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus.COMPLETE,
                        true, false, 0, 0, 1, 0, 0), List.of(), "summary", List.of(), 0);
    }

    private static IntentDefinition architectureIntent() {
        return new IntentDefinition("architecture-overview", "v1", "Architecture",
                List.of(InsightType.ARCHITECTURE_DESCRIPTION), List.of("grounded"),
                Map.of("type", "object"), "architecture-overview-prompt-v1");
    }

    private static RepositoryContext emptyRepositoryContext() {
        return new RepositoryContext("test", com.hopeful117.devlogai.repositorycontext.ContextProfile.ARCHITECTURE_REVIEW,
                List.of(), "test", List.of(), List.of(), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                0, 0, 0, false, List.of(), List.of(), "digest");
    }
}
