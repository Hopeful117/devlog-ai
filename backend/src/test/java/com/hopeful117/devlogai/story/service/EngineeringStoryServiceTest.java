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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EngineeringStoryServiceTest {

    @Mock
    EngineeringStoryRepository storyRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    EngineeringStoryMapper storyMapper;

    @InjectMocks
    EngineeringStoryServiceImpl storyService;

    private EngineeringStoryResponse response(StoryStatus status, UUID projectId) {
        return new EngineeringStoryResponse(
                UUID.randomUUID(), projectId, 1, "Story 0001", "docs/stories/0001",
                null, null, status, Instant.now(), Instant.now(), null);
    }

    @Test
    void shouldRegisterStorySuccessfully() {
        UUID projectId = UUID.randomUUID();

        CreateEngineeringStoryRequest request = new CreateEngineeringStoryRequest();
        request.setProjectId(projectId);
        request.setTitle("Story 0001");
        request.setStoryNumber(1);
        request.setStoryPath("docs/stories/0001");

        Project project = new Project();
        EngineeringStory story = new EngineeringStory();
        EngineeringStoryResponse response = response(StoryStatus.REGISTERED, projectId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(storyMapper.toEntity(request)).thenReturn(story);
        when(storyRepository.save(story)).thenReturn(story);
        when(storyMapper.toResponse(story)).thenReturn(response);

        EngineeringStoryResponse result = storyService.register(request);

        assertNotNull(result);
        assertEquals(response, result);
        assertEquals(project, story.getProject());
        assertEquals(StoryStatus.REGISTERED, story.getStatus());

        verify(projectRepository).findById(projectId);
        verify(storyMapper).toEntity(request);
        verify(storyRepository).save(story);
        verify(storyMapper).toResponse(story);
    }

    @Test
    void shouldThrowWhenRegisteringForMissingProject() {
        UUID projectId = UUID.randomUUID();

        CreateEngineeringStoryRequest request = new CreateEngineeringStoryRequest();
        request.setProjectId(projectId);
        request.setTitle("Story 0001");

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> storyService.register(request));

        verify(projectRepository).findById(projectId);
        verify(storyRepository, never()).save(any());
    }

    @Test
    void shouldStartImplementationSuccessfully() {
        UUID storyId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();

        EngineeringStory story = new EngineeringStory();
        story.setId(storyId);
        story.setProject(project);
        story.setStatus(StoryStatus.REGISTERED);

        StartStoryRequest request = new StartStoryRequest();
        request.setBaseCommit("abc123");

        EngineeringStoryResponse response = response(StoryStatus.IN_PROGRESS, projectId);

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyRepository.save(story)).thenReturn(story);
        when(storyMapper.toResponse(story)).thenReturn(response);

        EngineeringStoryResponse result =
                storyService.startImplementation(storyId, projectId, request);

        assertEquals(response, result);
        assertEquals("abc123", story.getBaseCommit());
        assertEquals(StoryStatus.IN_PROGRESS, story.getStatus());

        verify(storyRepository).findById(storyId);
        verify(storyRepository).save(story);
    }

    @Test
    void shouldThrowWhenStartingAnAlreadyStartedStory() {
        UUID storyId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();

        EngineeringStory story = new EngineeringStory();
        story.setId(storyId);
        story.setProject(project);
        story.setStatus(StoryStatus.IN_PROGRESS);

        StartStoryRequest request = new StartStoryRequest();
        request.setBaseCommit("abc123");

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));

        assertThrows(ConflictException.class,
                () -> storyService.startImplementation(storyId, projectId, request));

        verify(storyRepository, never()).save(any());
    }

    @Test
    void shouldCompleteStorySuccessfully() {
        UUID storyId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();

        EngineeringStory story = new EngineeringStory();
        story.setId(storyId);
        story.setProject(project);
        story.setStatus(StoryStatus.IN_PROGRESS);

        CompleteStoryRequest request = new CompleteStoryRequest();
        request.setTargetCommit("def456");

        EngineeringStoryResponse response = response(StoryStatus.COMPLETED, projectId);

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyRepository.save(story)).thenReturn(story);
        when(storyMapper.toResponse(story)).thenReturn(response);

        EngineeringStoryResponse result = storyService.complete(storyId, projectId, request);

        assertEquals(response, result);
        assertEquals("def456", story.getTargetCommit());
        assertEquals(StoryStatus.COMPLETED, story.getStatus());
        assertNotNull(story.getCompletedAt());

        verify(storyRepository).findById(storyId);
        verify(storyRepository).save(story);
    }

    @Test
    void shouldThrowWhenCompletingWithoutStarting() {
        UUID storyId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();

        EngineeringStory story = new EngineeringStory();
        story.setId(storyId);
        story.setProject(project);
        story.setStatus(StoryStatus.REGISTERED);

        CompleteStoryRequest request = new CompleteStoryRequest();
        request.setTargetCommit("def456");

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));

        assertThrows(ConflictException.class,
                () -> storyService.complete(storyId, projectId, request));

        verify(storyRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenStoryBelongsToAnotherProject() {
        UUID storyId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        Project otherProject = Project.builder().id(otherProjectId).build();

        EngineeringStory story = new EngineeringStory();
        story.setId(storyId);
        story.setProject(otherProject);
        story.setStatus(StoryStatus.REGISTERED);

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));

        UUID differentProject = UUID.randomUUID();
        assertThrows(EntityNotFoundException.class,
                () -> storyService.getById(storyId, differentProject));

        verify(storyMapper, never()).toResponse(any());
    }

    @Test
    void shouldGetStoryById() {
        UUID storyId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();

        EngineeringStory story = new EngineeringStory();
        story.setId(storyId);
        story.setProject(project);

        EngineeringStoryResponse response = response(StoryStatus.IN_PROGRESS, projectId);

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyMapper.toResponse(story)).thenReturn(response);

        EngineeringStoryResponse result = storyService.getById(storyId, projectId);

        assertEquals(response, result);

        verify(storyRepository).findById(storyId);
        verify(storyMapper).toResponse(story);
    }

    @Test
    void shouldThrowWhenStoryNotFound() {
        UUID storyId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(storyRepository.findById(storyId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> storyService.getById(storyId, projectId));

        verify(storyMapper, never()).toResponse(any());
    }

    @Test
    void shouldGetStoriesByProject() {
        UUID projectId = UUID.randomUUID();

        EngineeringStory story = new EngineeringStory();
        EngineeringStoryResponse response = response(StoryStatus.IN_PROGRESS, projectId);

        when(storyRepository.findByProject_IdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(story));
        when(storyMapper.toResponse(story)).thenReturn(response);

        List<EngineeringStoryResponse> result = storyService.getByProject(projectId);

        assertEquals(1, result.size());
        assertEquals(response, result.get(0));

        verify(storyRepository).findByProject_IdOrderByCreatedAtDesc(projectId);
    }
}