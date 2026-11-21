package com.hopeful117.devlogai.story.service;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryCatalogSynchronizationServiceTest {

    @TempDir
    Path workspace;

    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final SourceRepository sources = mock(SourceRepository.class);
    private final WorkspaceManager workspaces = mock(WorkspaceManager.class);
    private final EngineeringStoryRepository stories = mock(EngineeringStoryRepository.class);
    private final StoryCatalogSynchronizationServiceImpl service =
            new StoryCatalogSynchronizationServiceImpl(projects, sources, workspaces, stories);

    @Test
    void createsNumberedStoriesAndSkipsLegacyDirectories() throws Exception {
        write("docs/stories/0223-agentic-quality-real-story-pilot/story.md",
                "# Story 0223 - Agentic Quality Evaluation and Real Engineering Story Pilot\n");
        write("docs/stories/bugfix-llm-provider-timeout/story.md", "# Legacy\n");
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        when(stories.findByProject_Id(projectId)).thenReturn(List.of());

        StoryCatalogSynchronizationResult result = service.synchronize(
                project, new SynchronizedWorkspace(UUID.randomUUID(), workspace, "abc123"));

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.warnings()).contains("Skipped non-numbered Story path: "
                + "docs/stories/bugfix-llm-provider-timeout/story.md");
        var saved = org.mockito.ArgumentCaptor.forClass(EngineeringStory.class);
        verify(stories).save(saved.capture());
        assertThat(saved.getValue().getStoryNumber()).isEqualTo(223);
        assertThat(saved.getValue().getTitle()).startsWith("Story 0223 -");
        assertThat(saved.getValue().getStatus()).isEqualTo(StoryStatus.REGISTERED);
    }

    @Test
    void updatesOnlyRepositoryMetadataAndPreservesWorkflowState() throws Exception {
        write("docs/stories/0223-agentic-quality-real-story-pilot/story.md",
                "# Story 0223 - Renamed title\n");
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        EngineeringStory existing = new EngineeringStory();
        existing.setId(UUID.randomUUID());
        existing.setProject(project);
        existing.setStoryNumber(223);
        existing.setTitle("Old title");
        existing.setStoryPath("docs/stories/old/story.md");
        existing.setStatus(StoryStatus.COMPLETED);
        existing.setBaseCommit("base");
        existing.setTargetCommit("target");
        when(stories.findByProject_Id(projectId)).thenReturn(List.of(existing));

        StoryCatalogSynchronizationResult result = service.synchronize(
                project, new SynchronizedWorkspace(UUID.randomUUID(), workspace, "def456"));

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(StoryStatus.COMPLETED);
        assertThat(existing.getBaseCommit()).isEqualTo("base");
        assertThat(existing.getTargetCommit()).isEqualTo("target");
        verify(stories).save(existing);
    }

    @Test
    void rejectsAPathAndHeadingNumberMismatch() throws Exception {
        write("docs/stories/0223-agentic-quality-real-story-pilot/story.md",
                "# Story 0222 - Wrong number\n");
        Project project = Project.builder().id(UUID.randomUUID()).build();
        when(stories.findByProject_Id(project.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.synchronize(
                project, new SynchronizedWorkspace(UUID.randomUUID(), workspace, "abc123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Story number mismatch");
    }

    @Test
    void resolvesAndSynchronizesTheOnlyActiveSource() throws Exception {
        write("docs/stories/0223-agentic-quality-real-story-pilot/story.md",
                "# Story 0223 - Pilot\n");
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        var source = com.hopeful117.devlogai.source.entity.Source.builder()
                .id(sourceId).project(project).active(true).build();
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(sources.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of(source));
        when(workspaces.synchronize(source, "main"))
                .thenReturn(new SynchronizedWorkspace(sourceId, workspace, "abc123"));
        when(stories.findByProject_Id(projectId)).thenReturn(List.of());

        StoryCatalogSynchronizationResult result = service.synchronize(projectId, "main");

        assertThat(result.sourceId()).isEqualTo(sourceId);
        assertThat(result.createdCount()).isEqualTo(1);
        verify(workspaces).synchronize(source, "main");
    }

    private void write(String relativePath, String content) throws Exception {
        Path path = workspace.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
