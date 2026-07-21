package com.hopeful117.devlogai.milestone.service;

import com.hopeful117.devlogai.milestone.dto.request.CreateMilestoneRequest;
import com.hopeful117.devlogai.milestone.dto.response.MilestoneResponse;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.milestone.mapper.MilestoneMapper;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
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
public class MilestoneServiceTest {

    @Mock
    MilestoneRepository milestoneRepository;

    @Mock
    MilestoneMapper milestoneMapper;

    @Mock
    ProjectRepository projectRepository;

    @InjectMocks
    MilestoneServiceImpl milestoneService;

    @Test
    void shouldCreateMilestoneSuccessfully() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        CreateMilestoneRequest request =
                new CreateMilestoneRequest();

        request.setProjectId(projectId);
        request.setName("MVP");
        request.setDescription("First version");
        request.setStartedAt(Instant.now());


        Project project = new Project();

        Milestone milestone = new Milestone();

        MilestoneResponse response =
                mock(MilestoneResponse.class);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(milestoneMapper.toEntity(request))
                .thenReturn(milestone);

        when(milestoneRepository.save(milestone))
                .thenReturn(milestone);

        when(milestoneMapper.toResponse(milestone))
                .thenReturn(response);


        // Act
        MilestoneResponse result =
                milestoneService.create(request);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);

        assertEquals(project, milestone.getProject());
        assertEquals(
                MilestoneStatus.PLANNED,
                milestone.getStatus()
        );


        verify(projectRepository)
                .findById(projectId);

        verify(milestoneMapper)
                .toEntity(request);

        verify(milestoneRepository)
                .save(milestone);

        verify(milestoneMapper)
                .toResponse(milestone);
    }
    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        CreateMilestoneRequest request =
                new CreateMilestoneRequest();

        request.setProjectId(projectId);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> milestoneService.create(request)
        );


        verify(milestoneRepository, never())
                .save(any(Milestone.class));
    }
    @Test
    void shouldFindMilestoneById() {

        // Arrange
        UUID id = UUID.randomUUID();

        Milestone milestone =
                new Milestone();

        MilestoneResponse response =
                mock(MilestoneResponse.class);


        when(milestoneRepository.findById(id))
                .thenReturn(Optional.of(milestone));

        when(milestoneMapper.toResponse(milestone))
                .thenReturn(response);


        // Act
        MilestoneResponse result =
                milestoneService.getById(id);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);

        verify(milestoneRepository)
                .findById(id);

        verify(milestoneMapper)
                .toResponse(milestone);
    }
    @Test
    void shouldThrowExceptionWhenMilestoneDoesNotExist() {

        // Arrange
        UUID id = UUID.randomUUID();

        when(milestoneRepository.findById(id))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> milestoneService.getById(id)
        );


        verify(milestoneMapper, never())
                .toResponse(any());
    }
    @Test
    void shouldReturnMilestonesByProject() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        Milestone milestone =
                new Milestone();

        MilestoneResponse response =
                mock(MilestoneResponse.class);


        when(milestoneRepository
                .findByProjectIdOrderByStartedAtDesc(projectId))
                .thenReturn(List.of(milestone));

        when(milestoneMapper.toResponse(milestone))
                .thenReturn(response);


        // Act
        List<MilestoneResponse> result =
                milestoneService.getByProject(projectId);


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(milestoneRepository)
                .findByProjectIdOrderByStartedAtDesc(projectId);

        verify(milestoneMapper)
                .toResponse(milestone);
    }
    @Test
    void shouldReturnMilestonesByProjectAndStatus() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        Milestone milestone =
                new Milestone();

        MilestoneResponse response =
                mock(MilestoneResponse.class);


        when(milestoneRepository
                .findByProjectIdAndStatusOrderByStartedAtDesc(
                        projectId,
                        MilestoneStatus.IN_PROGRESS
                ))
                .thenReturn(List.of(milestone));

        when(milestoneMapper.toResponse(milestone))
                .thenReturn(response);


        // Act
        List<MilestoneResponse> result =
                milestoneService.getByProjectAndStatus(
                        projectId,
                        MilestoneStatus.IN_PROGRESS
                );


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(milestoneRepository)
                .findByProjectIdAndStatusOrderByStartedAtDesc(
                        projectId,
                        MilestoneStatus.IN_PROGRESS
                );

        verify(milestoneMapper)
                .toResponse(milestone);
    }
}
