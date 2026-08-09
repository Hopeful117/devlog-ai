package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.history.context.CommitDiffAnalysisContext;
import com.hopeful117.devlogai.history.service.ProjectHistoryService;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EngineeringEventExecutionPreparationServiceTest {
    private static final String TARGET = "b".repeat(40);

    @Mock ProjectRepository projects;
    @Mock SourceRepository sources;
    @Mock WorkspaceManager workspaces;
    @Mock ProjectHistoryService history;
    @Mock IntentCatalog intents;
    @InjectMocks EngineeringEventExecutionPreparationService service;

    @Test
    void rejectsUnknownProjectBeforeResolvingTheSource() {
        UUID projectId = UUID.randomUUID();
        EngineeringEventExecutionRequest request = request();
        when(projects.existsById(projectId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.prepare(projectId, request));

        verifyNoInteractions(sources, workspaces, history, intents);
    }

    @Test
    void rejectsSourceThatIsNotAnActiveGitRepositoryOfTheProject() {
        UUID projectId = UUID.randomUUID();
        EngineeringEventExecutionRequest request = request();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(any(), eq(projectId)))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.prepare(projectId, request));
    }

    @Test
    void rejectsIncompleteTargetCommit() {
        UUID projectId = UUID.randomUUID();
        Source source = gitSource();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(any(), eq(projectId)))
                .thenReturn(Optional.of(source));

        var invalidRequest = new EngineeringEventExecutionRequest(source.getId(), "abc123", null);
        assertThrows(IllegalArgumentException.class, () -> service.prepare(projectId, invalidRequest));
        verifyNoInteractions(workspaces, history, intents);
    }

    @Test
    void rejectsWorkspaceResolvingToAnotherRevision() {
        UUID projectId = UUID.randomUUID();
        Source source = gitSource();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(any(), eq(projectId)))
                .thenReturn(Optional.of(source));
        when(workspaces.synchronize(source, TARGET)).thenReturn(
                new SynchronizedWorkspace(source.getId(), Path.of("workspace"), "c".repeat(40)));
        EngineeringEventExecutionRequest request = request();

        assertThrows(IllegalArgumentException.class, () -> service.prepare(projectId, request));
        verifyNoInteractions(history, intents);
    }

    @Test
    void rejectsRootCommitAfterImportingItsHistory() {
        UUID projectId = UUID.randomUUID();
        Source source = gitSource();
        CommitDiffAnalysisContext context = mock(CommitDiffAnalysisContext.class);
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(any(), eq(projectId)))
                .thenReturn(Optional.of(source));
        when(workspaces.synchronize(source, TARGET)).thenReturn(
                new SynchronizedWorkspace(source.getId(), Path.of("workspace"), TARGET));
        when(history.getCommitContext(source.getId(), TARGET)).thenReturn(context);
        when(context.rootCommit()).thenReturn(true);
        EngineeringEventExecutionRequest request = request();

        assertThrows(IllegalArgumentException.class, () -> service.prepare(projectId, request));

        verify(history).importHistory(eq(source), any());
        verifyNoInteractions(intents);
    }

    @Test
    void preparesAnImmutableFirstParentBoundaryAndSourceSnapshot() {
        UUID projectId = UUID.randomUUID();
        Source source = gitSource();
        source.setName("Repository");
        source.setDefaultBranch("main");
        CommitDiffAnalysisContext context = mock(CommitDiffAnalysisContext.class);
        IntentDefinition intent = mock(IntentDefinition.class);
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(source.getId(), projectId))
                .thenReturn(Optional.of(source));
        when(workspaces.synchronize(source, TARGET)).thenReturn(
                new SynchronizedWorkspace(source.getId(), Path.of("workspace"), TARGET));
        when(history.getCommitContext(source.getId(), TARGET)).thenReturn(context);
        when(context.firstParentHash()).thenReturn("a".repeat(40));
        when(intents.resolve(EngineeringEventExecutionPreparationService.INTENT_KEY)).thenReturn(intent);

        PreparedEngineeringEventExecution prepared = service.prepare(projectId, request());

        assertEquals("a".repeat(40), prepared.baseCommit());
        assertEquals(TARGET, prepared.targetCommit());
        assertEquals("Repository", prepared.sourceSnapshot().get("name"));
        Map<String, Object> snapshot = prepared.sourceSnapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("name", "changed"));
    }

    private EngineeringEventExecutionRequest request() {
        return new EngineeringEventExecutionRequest(UUID.fromString(
                "11111111-1111-1111-1111-111111111111"), TARGET, null);
    }

    private Source gitSource() {
        return Source.builder().id(request().sourceId()).type(SourceType.GIT_REPOSITORY).build();
    }
}
