package com.hopeful117.devlogai.history.service;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.history.context.CommitDiffContextBuilder;
import com.hopeful117.devlogai.history.dto.HistoryImportResult;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.DiffStatistics;
import com.hopeful117.devlogai.history.model.GitCommitData;
import com.hopeful117.devlogai.history.provider.GitHistoryProvider;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectHistoryServiceTest {
    @Test
    void importsOnlyPreviouslyUnknownCommitsAndRetainsParentMetadata() {
        SourceRepository sources = mock(SourceRepository.class);
        ProjectCommitRepository commits = mock(ProjectCommitRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        GitHistoryProvider provider = mock(GitHistoryProvider.class);
        UUID sourceId = UUID.randomUUID();
        Source source = Source.builder().id(sourceId)
                .project(Project.builder().id(UUID.randomUUID()).build()).build();
        Path path = Path.of("/tmp/history-test");
        when(sources.findById(sourceId)).thenReturn(Optional.of(source));
        when(workspaces.synchronize(source, null))
                .thenReturn(new SynchronizedWorkspace(sourceId, path, "head"));
        GitCommitData existing = commit("old", List.of());
        GitCommitData fresh = commit("new", List.of("old"));
        when(provider.readHistory(path, "head")).thenReturn(List.of(existing, fresh));
        when(commits.existsBySourceIdAndCommitHash(sourceId, "old")).thenReturn(true);
        when(commits.existsBySourceIdAndCommitHash(sourceId, "new")).thenReturn(false);
        ProjectHistoryServiceImpl service = new ProjectHistoryServiceImpl(
                sources, commits, workspaces, provider, new CommitDiffContextBuilder(10, 100));

        HistoryImportResult result = service.importHistory(sourceId, null);

        assertEquals(2, result.discoveredCommits());
        assertEquals(1, result.importedCommits());
        assertEquals(1, result.existingCommits());
        ArgumentCaptor<ProjectCommit> saved = ArgumentCaptor.forClass(ProjectCommit.class);
        verify(commits).save(saved.capture());
        assertEquals("new", saved.getValue().getCommitHash());
        assertEquals("old", saved.getValue().getParents().getFirst().getParentHash());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getValue().getImportedAt());
    }

    @Test
    void reimportIsIdempotent() {
        SourceRepository sources = mock(SourceRepository.class);
        ProjectCommitRepository commits = mock(ProjectCommitRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        GitHistoryProvider provider = mock(GitHistoryProvider.class);
        UUID sourceId = UUID.randomUUID();
        Source source = Source.builder().id(sourceId)
                .project(Project.builder().id(UUID.randomUUID()).build()).build();
        when(sources.findById(sourceId)).thenReturn(Optional.of(source));
        when(workspaces.synchronize(source, null)).thenReturn(
                new SynchronizedWorkspace(sourceId, Path.of("/tmp/repository"), "same"));
        when(provider.readHistory(Path.of("/tmp/repository"), "same"))
                .thenReturn(List.of(commit("same", List.of())));
        when(commits.existsBySourceIdAndCommitHash(sourceId, "same")).thenReturn(true);
        ProjectHistoryServiceImpl service = new ProjectHistoryServiceImpl(
                sources, commits, workspaces, provider, new CommitDiffContextBuilder(10, 100));

        HistoryImportResult result = service.importHistory(sourceId, null);

        assertEquals(0, result.importedCommits());
        assertEquals(1, result.existingCommits());
        verify(commits, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private GitCommitData commit(String hash, List<String> parents) {
        return new GitCommitData(hash, parents, "Author", "author@example.test",
                Instant.parse("2026-07-23T10:00:00Z"),
                Instant.parse("2026-07-23T10:01:00Z"), hash, hash,
                List.of(), new DiffStatistics(0, 0, 0, 0));
    }
}
