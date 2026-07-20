package com.hopeful117.devlogai.project.service;

import com.hopeful117.devlogai.project.dto.request.CreateProjectRequest;
import com.hopeful117.devlogai.project.dto.request.UpdateProjectRequest;
import com.hopeful117.devlogai.project.dto.response.ProjectResponse;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.project.exception.ProjectSlugAlreadyExistsException;
import com.hopeful117.devlogai.project.mapper.ProjectMapper;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.service.SlugService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectServiceTest {
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private SlugService slugService;

    @InjectMocks
    private ProjectServiceImpl projectService;



    @Test
    void shouldCreateProjectSuccessfully() {

        // Arrange
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("Trading OS");
        request.setDescription("AI assisted trading platform");


        Project project = new Project();
        ProjectResponse response = new ProjectResponse();

        when(slugService.generate(any(String.class)))
                .thenReturn("trading-os");

        when(projectRepository.existsBySlug(any(String.class)))
                .thenReturn(false);

        when(projectMapper.toEntity(any(CreateProjectRequest.class)))
                .thenReturn(project);

        when(projectRepository.save(any(Project.class)))
                .thenReturn(project);

        when(projectMapper.toResponse(project))
                .thenReturn(response);


        // Act
        ProjectResponse result = projectService.create(request);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);

        verify(slugService)
                .generate("Trading OS");

        verify(projectRepository)
                .existsBySlug("trading-os");

        verify(projectRepository)
                .save(project);

        verify(projectMapper)
                .toResponse(project);

        assertEquals("trading-os", project.getSlug());
        assertEquals(ProjectStatus.ACTIVE, project.getStatus());
    }


    @Test
    void shouldThrowExceptionWhenSlugAlreadyExists() {

        // Arrange
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("Trading OS");


        when(slugService.generate(any(String.class)))
                .thenReturn("trading-os");

        when(projectRepository.existsBySlug(any(String.class)))
                .thenReturn(true);


        // Act & Assert
        assertThrows(
                ProjectSlugAlreadyExistsException.class,
                () -> projectService.create(request)
        );


        verify(projectRepository, never())
                .save(any(Project.class));
    }

    @Test
    void shouldFindProjectBySlugSuccessfully() {

        // Arrange
        String slug = "trading-os";

        Project project = new Project();
        ProjectResponse response = new ProjectResponse();

        when(projectRepository.findBySlug(any(String.class)))
                .thenReturn(Optional.of(project));

        when(projectMapper.toResponse(project))
                .thenReturn(response);


        // Act
        ProjectResponse result = projectService.getBySlug(slug);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);

        verify(projectRepository)
                .findBySlug(slug);

        verify(projectMapper)
                .toResponse(project);
    }

    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {

        // Arrange
        String slug = "unknown-project";

        when(projectRepository.findBySlug(any(String.class)))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> projectService.getBySlug(slug)
        );


        verify(projectRepository)
                .findBySlug(slug);

        verify(projectMapper, never())
                .toResponse(any(Project.class));
    }
    @Test
    void shouldReturnAllProjects() {

        // Arrange
        Project project1 = new Project();
        Project project2 = new Project();

        ProjectResponse response1 = new ProjectResponse();
        ProjectResponse response2 = new ProjectResponse();

        when(projectRepository.findAll())
                .thenReturn(List.of(project1, project2));

        when(projectMapper.toResponse(project1))
                .thenReturn(response1);

        when(projectMapper.toResponse(project2))
                .thenReturn(response2);


        // Act
        List<ProjectResponse> result = projectService.getAll();


        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals(response1, result.get(0));
        assertEquals(response2, result.get(1));

        verify(projectRepository)
                .findAll();

        verify(projectMapper)
                .toResponse(project1);

        verify(projectMapper)
                .toResponse(project2);
    }
    @Test
    void shouldReturnEmptyListWhenNoProjectExists() {

        // Arrange
        when(projectRepository.findAll())
                .thenReturn(List.of());


        // Act
        List<ProjectResponse> result = projectService.getAll();


        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(projectRepository)
                .findAll();
    }
    @Test
    void shouldUpdateProjectSuccessfully() {

        // Arrange
        String slug = "trading-os";

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("Trading OS Updated");
        request.setDescription("Updated description");


        Project project = new Project();
        ProjectResponse response = new ProjectResponse();


        when(projectRepository.findBySlug(any(String.class)))
                .thenReturn(Optional.of(project));

        when(projectRepository.save(project))
                .thenReturn(project);

        when(projectMapper.toResponse(project))
                .thenReturn(response);


        // Act
        ProjectResponse result = projectService.update(slug, request);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);


        verify(projectRepository)
                .findBySlug(slug);

        verify(projectMapper)
                .updateProject(request, project);

        verify(projectRepository)
                .save(project);

        verify(projectMapper)
                .toResponse(project);
    }
    @Test
    void shouldThrowExceptionWhenUpdatingUnknownProject() {

        String slug = "unknown";

        UpdateProjectRequest request = new UpdateProjectRequest();


        when(projectRepository.findBySlug(any(String.class)))
                .thenReturn(Optional.empty());


        assertThrows(
                EntityNotFoundException.class,
                () -> projectService.update(slug, request)
        );


        verify(projectRepository, never())
                .save(any(Project.class));
    }
    @Test
    void shouldArchiveProjectSuccessfully() {

        // Arrange
        String slug = "trading-os";

        Project project = new Project();
        project.setStatus(ProjectStatus.ACTIVE);


        when(projectRepository.findBySlug(any(String.class)))
                .thenReturn(Optional.of(project));

        when(projectRepository.save(project))
                .thenReturn(project);


        // Act
        projectService.archive(slug);


        // Assert
        assertEquals(ProjectStatus.ARCHIVED, project.getStatus());


        verify(projectRepository)
                .findBySlug(slug);

        verify(projectRepository)
                .save(project);
    }
    @Test
    void shouldThrowExceptionWhenArchivingUnknownProject() {

        String slug = "unknown";


        when(projectRepository.findBySlug(any(String.class)))
                .thenReturn(Optional.empty());


        assertThrows(
                EntityNotFoundException.class,
                () -> projectService.archive(slug)
        );


        verify(projectRepository, never())
                .save(any(Project.class));
    }

}
