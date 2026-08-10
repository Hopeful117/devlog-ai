package com.hopeful117.devlogai.story.service;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.story.dto.request.CompleteStoryRequest;
import com.hopeful117.devlogai.story.dto.request.CreateEngineeringStoryRequest;
import com.hopeful117.devlogai.story.dto.request.StartStoryRequest;
import com.hopeful117.devlogai.story.dto.response.EngineeringStoryResponse;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.mapper.EngineeringStoryMapper;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngineeringStoryServiceImpl implements EngineeringStoryService {

    private final EngineeringStoryRepository storyRepository;
    private final ProjectRepository projectRepository;
    private final EngineeringStoryMapper storyMapper;

    @Override
    public EngineeringStoryResponse register(CreateEngineeringStoryRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException("Project", request.getProjectId()));

        EngineeringStory story = storyMapper.toEntity(request);
        story.setProject(project);
        story.setStatus(StoryStatus.REGISTERED);

        EngineeringStory saved = storyRepository.save(story);
        return storyMapper.toResponse(saved);
    }

    @Override
    public EngineeringStoryResponse startImplementation(UUID storyId, UUID projectId, StartStoryRequest request) {
        EngineeringStory story = requireStoryInProject(storyId, projectId);
        requireStatus(story, StoryStatus.REGISTERED);

        story.setBaseCommit(request.getBaseCommit());
        story.setStatus(StoryStatus.IN_PROGRESS);

        EngineeringStory saved = storyRepository.save(story);
        return storyMapper.toResponse(saved);
    }

    @Override
    public EngineeringStoryResponse complete(UUID storyId, UUID projectId, CompleteStoryRequest request) {
        EngineeringStory story = requireStoryInProject(storyId, projectId);
        requireStatus(story, StoryStatus.IN_PROGRESS);

        story.setTargetCommit(request.getTargetCommit());
        story.setStatus(StoryStatus.COMPLETED);
        story.setCompletedAt(Instant.now());

        EngineeringStory saved = storyRepository.save(story);
        return storyMapper.toResponse(saved);
    }

    @Override
    public EngineeringStoryResponse getById(UUID storyId, UUID projectId) {
        return storyMapper.toResponse(requireStoryInProject(storyId, projectId));
    }

    @Override
    public List<EngineeringStoryResponse> getByProject(UUID projectId) {
        return storyRepository.findByProject_IdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(storyMapper::toResponse)
                .toList();
    }

    private EngineeringStory requireStoryInProject(UUID storyId, UUID projectId) {
        EngineeringStory story = storyRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("EngineeringStory", storyId));

        if (!story.getProject().getId().equals(projectId)) {
            throw new EntityNotFoundException("EngineeringStory", storyId);
        }

        return story;
    }

    private void requireStatus(EngineeringStory story, StoryStatus expected) {
        if (story.getStatus() != expected) {
            throw new ConflictException(
                    "EngineeringStory %s cannot transition from status %s (expected %s)"
                            .formatted(story.getId(), story.getStatus(), expected));
        }
    }
}