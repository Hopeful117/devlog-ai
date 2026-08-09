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
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.service.SlugService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
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

        Project project = Project.builder().id(UUID.randomUUID()).slug(slug).build();
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

        when(projectRepository.saveAndFlush(project))
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
                .existsByNameAndIdNot("Trading OS Updated", project.getId());

        verify(projectRepository)
                .saveAndFlush(project);

        verify(projectMapper)
                .toResponse(project);
    }

    @Test
    void shouldPreserveDescriptionOnlyUpdateCompatibility() {
        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .description("")
                .build();
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        ProjectResponse response = new ProjectResponse();
        when(projectRepository.findBySlug("devlog-ai")).thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);
        when(projectMapper.toResponse(project)).thenReturn(response);

        assertSame(response, projectService.update("devlog-ai", request));

        verify(projectRepository, never()).existsByNameAndIdNot(anyString(), any());
        verify(projectMapper).updateProject(request, project);
    }

    @Test
    void shouldRejectDuplicateUpdatedName() {
        UUID projectId = UUID.randomUUID();
        UpdateProjectRequest request = UpdateProjectRequest.builder().name(" Existing ").build();
        Project project = Project.builder().id(projectId).slug("devlog-ai").build();
        when(projectRepository.findBySlug("devlog-ai")).thenReturn(Optional.of(project));
        when(projectRepository.existsByNameAndIdNot("Existing", projectId)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> projectService.update("devlog-ai", request));

        verify(projectMapper, never()).updateProject(any(), any());
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldNotMisreportUnrelatedIntegrityFailureAsDuplicateName() {
        UUID projectId = UUID.randomUUID();
        UpdateProjectRequest request = UpdateProjectRequest.builder().name("Updated").build();
        Project project = Project.builder().id(projectId).slug("devlog-ai").build();
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("fk_unrelated_constraint");
        when(projectRepository.findBySlug("devlog-ai")).thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(project)).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> projectService.update("devlog-ai", request)));
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
                .saveAndFlush(any(Project.class));
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

    @Test
    void shouldDeleteProjectAndFlush() {
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        when(projectRepository.findBySlug("devlog-ai")).thenReturn(Optional.of(project));

        projectService.delete("devlog-ai");

        verify(projectRepository).delete(project);
        verify(projectRepository).flush();
    }

    @Test
    void shouldRejectDeletingUnknownProject() {
        when(projectRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> projectService.delete("unknown"));

        verify(projectRepository, never()).delete(any());
        verify(projectRepository, never()).flush();
    }

}
