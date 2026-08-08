package com.hopeful117.devlogai.history.service;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.history.context.CommitDiffAnalysisContext;
import com.hopeful117.devlogai.history.context.CommitDiffContextBuilder;
import com.hopeful117.devlogai.history.dto.HistoryImportResult;
import com.hopeful117.devlogai.history.dto.ProjectCommitResponse;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.DiffStatistics;
import com.hopeful117.devlogai.history.model.GitCommitData;
import com.hopeful117.devlogai.history.provider.GitHistoryProvider;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.project.entity.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectHistoryServiceAdditionalTest {

    @Mock private SourceRepository sourceRepository;
    @Mock private ProjectCommitRepository commitRepository;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private GitHistoryProvider historyProvider;
    @Mock private CommitDiffContextBuilder contextBuilder;

    private ProjectHistoryServiceImpl createService() {
        return new ProjectHistoryServiceImpl(sourceRepository, commitRepository,
                workspaceManager, historyProvider, contextBuilder);
    }

    private Source testSource() {
        return Source.builder().id(UUID.randomUUID())
                .project(Project.builder().id(UUID.randomUUID()).build())
                .type(SourceType.GIT_REPOSITORY).build();
    }

    private GitCommitData testCommitData(String hash) {
        return new GitCommitData(
                hash, List.of(), "Author", "author@test.com",
                Instant.now(), Instant.now(),
                "Subject", "Full message",
                List.of(), new DiffStatistics(1, 10, 5, 0));
    }

    @Test
    void shouldImportHistorySuccessfully() {
        Source source = testSource();
        SynchronizedWorkspace workspace = new SynchronizedWorkspace(
                source.getId(), Path.of("/tmp/repo"), "abc123");
        GitCommitData commitData = testCommitData("hash1");

        when(sourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, "main")).thenReturn(workspace);
        when(historyProvider.readHistory(workspace.path(), "abc123")).thenReturn(List.of(commitData));
        when(commitRepository.existsBySourceIdAndCommitHash(source.getId(), "hash1")).thenReturn(false);

        HistoryImportResult result = createService().importHistory(source.getId(), "main");

        assertNotNull(result);
        assertEquals(1, result.importedCommits());
        assertEquals(0, result.existingCommits());
        verify(commitRepository).save(any());
    }

    @Test
    void shouldSkipExistingCommits() {
        Source source = testSource();
        SynchronizedWorkspace workspace = new SynchronizedWorkspace(
                source.getId(), Path.of("/tmp/repo"), "abc123");
        GitCommitData commitData = testCommitData("hash1");

        when(sourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, "main")).thenReturn(workspace);
        when(historyProvider.readHistory(workspace.path(), "abc123")).thenReturn(List.of(commitData));
        when(commitRepository.existsBySourceIdAndCommitHash(source.getId(), "hash1")).thenReturn(true);

        HistoryImportResult result = createService().importHistory(source.getId(), "main");

        assertNotNull(result);
        assertEquals(0, result.importedCommits());
        assertEquals(1, result.existingCommits());
        verify(commitRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenSourceNotFound() {
        UUID sourceId = UUID.randomUUID();
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.empty());
        ProjectHistoryService service = createService();

        assertThrows(EntityNotFoundException.class,
                () -> service.importHistory(sourceId, "main"));
    }

    @Test
    void shouldGetProjectHistory() {
        UUID projectId = UUID.randomUUID();
        when(commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(projectId))
                .thenReturn(List.of());

        List<ProjectCommitResponse> result = createService().getProjectHistory(projectId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGetCommitContext() {
        UUID sourceId = UUID.randomUUID();
        String commitHash = "abc123";
        ProjectCommit commit = ProjectCommit.builder()
                .id(UUID.randomUUID())
                .project(Project.builder().id(UUID.randomUUID()).build())
                .source(Source.builder().id(sourceId).build())
                .commitHash(commitHash)
                .build();
        CommitDiffAnalysisContext expected = mock(CommitDiffAnalysisContext.class);

        when(commitRepository.findBySourceIdAndCommitHash(sourceId, commitHash))
                .thenReturn(Optional.of(commit));
        when(contextBuilder.build(commit)).thenReturn(expected);

        CommitDiffAnalysisContext result = createService().getCommitContext(sourceId, commitHash);

        assertSame(expected, result);
    }

    @Test
    void shouldThrowWhenCommitNotFound() {
        when(commitRepository.findBySourceIdAndCommitHash(any(), any()))
                .thenReturn(Optional.empty());
        UUID sourceId = UUID.randomUUID();
        ProjectHistoryService service = createService();

        assertThrows(EntityNotFoundException.class,
                () -> service.getCommitContext(sourceId, "nonexistent"));
    }

    @Test
    void shouldImportWithMergeCommits() {
        Source source = testSource();
        SynchronizedWorkspace workspace = new SynchronizedWorkspace(
                source.getId(), Path.of("/tmp/repo"), "abc123");
        GitCommitData mergeCommit = new GitCommitData(
                "merge1", List.of("parent1", "parent2"), "Merger", "merge@test.com",
                Instant.now(), Instant.now(),
                "Merge branch", "Merge message",
                List.of(), new DiffStatistics(5, 50, 20, 0));

        when(sourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, "main")).thenReturn(workspace);
        when(historyProvider.readHistory(workspace.path(), "abc123")).thenReturn(List.of(mergeCommit));
        when(commitRepository.existsBySourceIdAndCommitHash(source.getId(), "merge1")).thenReturn(false);

        HistoryImportResult result = createService().importHistory(source.getId(), "main");

        assertNotNull(result);
        assertEquals(1, result.importedCommits());
        verify(commitRepository).save(any());
    }
}
