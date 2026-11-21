package com.hopeful117.devlogai.story.service;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StoryCatalogSynchronizationServiceImpl implements StoryCatalogSynchronizationService {

    private static final Pattern STORY_DIRECTORY = Pattern.compile(
            "^docs/stories/(\\d+)-[^/]+/story\\.md$");
    private static final Pattern STORY_TITLE = Pattern.compile(
            "(?m)^#\\s+(?:Story|Engineering Story)(?:\\s+(\\d+))?\\s*(?:-|—|:)\\s*(.+?)\\s*$");

    private final ProjectRepository projectRepository;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private final EngineeringStoryRepository storyRepository;

    public StoryCatalogSynchronizationServiceImpl(
            ProjectRepository projectRepository,
            SourceRepository sourceRepository,
            WorkspaceManager workspaceManager,
            EngineeringStoryRepository storyRepository
    ) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.workspaceManager = workspaceManager;
        this.storyRepository = storyRepository;
    }

    @Override
    @Transactional
    public StoryCatalogSynchronizationResult synchronize(UUID projectId, String targetRevision) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));
        List<Source> sources = sourceRepository
                .findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId);
        if (sources.isEmpty()) {
            throw new EntityNotFoundException("ActiveSource", projectId);
        }
        if (sources.size() > 1) {
            throw new ConflictException("Story synchronization requires exactly one active source");
        }
        SynchronizedWorkspace workspace = workspaceManager.synchronize(sources.getFirst(), targetRevision);
        return synchronize(project, workspace);
    }

    @Override
    @Transactional
    public StoryCatalogSynchronizationResult synchronize(Project project, SynchronizedWorkspace workspace) {
        if (workspace == null || workspace.path() == null || workspace.resolvedRevision() == null) {
            throw new IllegalArgumentException("A synchronized workspace with a resolved revision is required");
        }
        if (project == null || project.getId() == null) {
            throw new IllegalArgumentException("A project with an id is required");
        }

        List<DiscoveredStory> discovered = discover(workspace.path());
        Map<Integer, EngineeringStory> existing = new HashMap<>();
        for (EngineeringStory story : storyRepository.findByProject_Id(project.getId())) {
            existing.put(story.getStoryNumber(), story);
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (DiscoveredStory candidate : discovered) {
            EngineeringStory story = existing.get(candidate.storyNumber());
            if (story == null) {
                storyRepository.save(EngineeringStory.builder()
                        .project(project)
                        .storyNumber(candidate.storyNumber())
                        .title(candidate.title())
                        .storyPath(candidate.storyPath())
                        .status(StoryStatus.REGISTERED)
                        .build());
                created++;
                continue;
            }
            boolean changed = false;
            if (!candidate.title().equals(story.getTitle())) {
                story.setTitle(candidate.title());
                changed = true;
            }
            if (!candidate.storyPath().equals(story.getStoryPath())) {
                story.setStoryPath(candidate.storyPath());
                changed = true;
            }
            if (changed) {
                storyRepository.save(story);
                updated++;
            } else {
                unchanged++;
            }
        }

        List<String> warnings = discoverWarnings(workspace.path());
        return new StoryCatalogSynchronizationResult(
                project.getId(), workspace.sourceId(), workspace.resolvedRevision(),
                created, updated, unchanged, warnings.size(), warnings);
    }

    private List<DiscoveredStory> discover(Path workspace) {
        Map<Integer, DiscoveredStory> discovered = new LinkedHashMap<>();
        try (var paths = Files.walk(workspace)) {
            paths.filter(Files::isRegularFile)
                    .map(workspace::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> STORY_DIRECTORY.matcher(path).matches())
                    .sorted()
                    .forEach(path -> {
                        DiscoveredStory story = parse(path, workspace.resolve(path));
                        if (discovered.put(story.storyNumber(), story) != null) {
                            throw new IllegalArgumentException(
                                    "Duplicate repository Story number: " + story.storyNumber());
                        }
                    });
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to scan repository Story catalog", exception);
        }
        return discovered.values().stream()
                .sorted(Comparator.comparingInt(DiscoveredStory::storyNumber))
                .toList();
    }

    private List<String> discoverWarnings(Path workspace) {
        List<String> warnings = new ArrayList<>();
        try (var paths = Files.walk(workspace)) {
            paths.filter(Files::isRegularFile)
                    .map(workspace::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> path.startsWith("docs/stories/") && path.endsWith("/story.md"))
                    .filter(path -> !STORY_DIRECTORY.matcher(path).matches())
                    .sorted()
                    .forEach(path -> warnings.add("Skipped non-numbered Story path: " + path));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect repository Story warnings", exception);
        }
        return warnings;
    }

    private DiscoveredStory parse(String relativePath, Path path) {
        Matcher directory = STORY_DIRECTORY.matcher(relativePath);
        if (!directory.matches()) {
            throw new IllegalArgumentException("Invalid repository Story path: " + relativePath);
        }
        int pathNumber = Integer.parseInt(directory.group(1));
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read repository Story: " + relativePath, exception);
        }
        Matcher title = STORY_TITLE.matcher(content);
        if (!title.find()) {
            throw new IllegalArgumentException("Story has no canonical title heading: " + relativePath);
        }
        String titleNumber = title.group(1);
        if (titleNumber != null && pathNumber != Integer.parseInt(titleNumber)) {
            throw new IllegalArgumentException(
                    "Story number mismatch in " + relativePath + ": path=" + pathNumber
                            + ", heading=" + titleNumber);
        }
        return new DiscoveredStory(pathNumber, title.group(0).substring(2).trim(), relativePath);
    }

    private record DiscoveredStory(int storyNumber, String title, String storyPath) {}
}
