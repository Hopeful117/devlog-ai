package com.hopeful117.devlogai.project.service;

import com.hopeful117.devlogai.project.dto.request.CreateProjectRequest;
import com.hopeful117.devlogai.project.dto.response.ProjectResponse;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.project.exception.ProjectSlugAlreadyExistsException;
import com.hopeful117.devlogai.project.mapper.ProjectMapper;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.service.SlugService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

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

}
