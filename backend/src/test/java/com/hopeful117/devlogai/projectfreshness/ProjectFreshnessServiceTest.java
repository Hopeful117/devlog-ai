package com.hopeful117.devlogai.projectfreshness;

import com.hopeful117.devlogai.collection.workspace.ResolvedSourceRevision;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectFreshnessServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final SourceRepository sources = mock(SourceRepository.class);
    private final WorkspaceManager workspaces = mock(WorkspaceManager.class);
    private final ProjectFreshnessPersistenceService persistence =
            mock(ProjectFreshnessPersistenceService.class);
    private final ProjectFreshnessService service =
            new ProjectFreshnessService(projects, sources, workspaces, persistence);

    @Test
    void shouldResolveOutsidePersistenceAndSaveSuccessfulCheck() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Source source = Source.builder().id(sourceId).active(true)
                .type(SourceType.GIT_REPOSITORY).build();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(workspaces.resolveCurrentRevision(source)).thenReturn(
                new ResolvedSourceRevision(sourceId, "origin/main", "a".repeat(40)));
        when(persistence.save(eq(projectId), eq(sourceId), eq("origin/main"),
                eq("a".repeat(40)), any())).thenReturn(mock(ProjectFreshnessResponse.class));

        assertNotNull(service.check(projectId, sourceId));
        verify(workspaces).resolveCurrentRevision(source);
        verify(persistence).save(eq(projectId), eq(sourceId), eq("origin/main"),
                eq("a".repeat(40)), any());
    }

    @Test
    void shouldTranslateGitFailureAndNeverPersistIt() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Source source = Source.builder().id(sourceId).active(true)
                .type(SourceType.GIT_REPOSITORY).build();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(workspaces.resolveCurrentRevision(source)).thenThrow(new RuntimeException("secret"));

        var error = assertThrows(SourceRevisionUnavailableException.class,
                () -> service.check(projectId, sourceId));
        assertFalse(error.getMessage().contains("secret"));
        verifyNoInteractions(persistence);
    }

    @Test
    void shouldRecordExternallyObservedRevisionThroughExistingClassification() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Source source = Source.builder().id(sourceId).active(true)
                .type(SourceType.GIT_REPOSITORY).defaultBranch("main").build();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(persistence.save(eq(projectId), eq(sourceId), eq("origin/main"),
                eq("c".repeat(40)), any())).thenReturn(mock(ProjectFreshnessResponse.class));

        assertNotNull(service.recordObservedRevision(projectId, sourceId, "c".repeat(40)));
        verifyNoInteractions(workspaces);
    }

    @Test
    void shouldRejectObservationsForUnknownSources() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.recordObservedRevision(projectId, sourceId, "a".repeat(40)));
        verifyNoInteractions(persistence);
    }

    @Test
    void shouldSummarizeCheckedAndUncheckedActiveSourcesWithoutGit() {
        UUID projectId = UUID.randomUUID();
        Source first = Source.builder().id(UUID.randomUUID()).build();
        Source second = Source.builder().id(UUID.randomUUID()).build();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of(first, second));
        when(persistence.latest(projectId, first.getId()))
                .thenReturn(Optional.of(mock(ProjectFreshnessResponse.class)));
        when(persistence.latest(projectId, second.getId())).thenReturn(Optional.empty());

        ProjectFreshnessSummary summary = service.summary(projectId);
        assertEquals(1, summary.checkedSources().size());
        assertEquals(1, summary.uncheckedSourceCount());
        verifyNoInteractions(workspaces);
    }
}
