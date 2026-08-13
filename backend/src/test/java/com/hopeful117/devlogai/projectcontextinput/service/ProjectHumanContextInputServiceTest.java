package com.hopeful117.devlogai.projectcontextinput.service;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.projectcontextinput.dto.request.CreateProjectHumanContextInputRequest;
import com.hopeful117.devlogai.projectcontextinput.dto.response.ProjectHumanContextInputResponse;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInput;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import com.hopeful117.devlogai.projectcontextinput.mapper.ProjectHumanContextInputMapper;
import com.hopeful117.devlogai.projectcontextinput.repository.ProjectHumanContextInputRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectHumanContextInputServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock ProjectHumanContextInputRepository repository;
    @Mock ProjectHumanContextInputMapper mapper;

    @InjectMocks ProjectHumanContextInputServiceImpl service;

    @Test
    void shouldCreateActiveInputForProject() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        CreateProjectHumanContextInputRequest request = new CreateProjectHumanContextInputRequest(
                "  Improve knowledge quality  ",
                "  Better context for humans and agents.  ",
                ProjectHumanContextInputType.GOAL
        );
        ProjectHumanContextInput saved = ProjectHumanContextInput.builder()
                .id(UUID.randomUUID())
                .project(project)
                .title("Improve knowledge quality")
                .contentMarkdown("Better context for humans and agents.")
                .type(ProjectHumanContextInputType.GOAL)
                .status(ProjectHumanContextInputStatus.ACTIVE)
                .build();
        ProjectHumanContextInputResponse response = new ProjectHumanContextInputResponse(
                saved.getId(), projectId, saved.getTitle(), saved.getContentMarkdown(),
                saved.getType(), saved.getStatus(), null, null
        );

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(repository.save(any(ProjectHumanContextInput.class))).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        ProjectHumanContextInputResponse result = service.create(projectId, request);

        assertEquals(response, result);
        verify(repository).save(argThat(input ->
                input.getProject().equals(project)
                        && input.getStatus() == ProjectHumanContextInputStatus.ACTIVE
                        && input.getType() == ProjectHumanContextInputType.GOAL
                        && "Improve knowledge quality".equals(input.getTitle())
                        && "Better context for humans and agents.".equals(input.getContentMarkdown())
        ));
    }

    @Test
    void shouldArchiveInputWithinProjectScope() {
        UUID projectId = UUID.randomUUID();
        UUID inputId = UUID.randomUUID();
        ProjectHumanContextInput input = ProjectHumanContextInput.builder()
                .id(inputId)
                .status(ProjectHumanContextInputStatus.ACTIVE)
                .build();
        ProjectHumanContextInputResponse response = new ProjectHumanContextInputResponse(
                inputId, projectId, "Goal", "Body", ProjectHumanContextInputType.GOAL,
                ProjectHumanContextInputStatus.ARCHIVED, null, null
        );

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(repository.findByIdAndProject_Id(inputId, projectId)).thenReturn(Optional.of(input));
        when(repository.save(input)).thenReturn(input);
        when(mapper.toResponse(input)).thenReturn(response);

        ProjectHumanContextInputResponse result = service.archive(projectId, inputId);

        assertEquals(ProjectHumanContextInputStatus.ARCHIVED, input.getStatus());
        assertEquals(response, result);
    }

    @Test
    void shouldFailWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> service.getByProject(projectId));
    }

    @Test
    void shouldReturnProjectInputsInRepositoryOrder() {
        UUID projectId = UUID.randomUUID();
        ProjectHumanContextInput input = ProjectHumanContextInput.builder()
                .id(UUID.randomUUID())
                .title("Goal")
                .build();
        ProjectHumanContextInputResponse response = new ProjectHumanContextInputResponse(
                input.getId(), projectId, "Goal", "Body", ProjectHumanContextInputType.GOAL,
                ProjectHumanContextInputStatus.ACTIVE, null, null
        );

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(repository.findByProject_IdOrderByUpdatedAtDescIdDesc(projectId))
                .thenReturn(List.of(input));
        when(mapper.toResponse(List.of(input))).thenReturn(List.of(response));

        List<ProjectHumanContextInputResponse> results = service.getByProject(projectId);

        assertEquals(List.of(response), results);
    }
}
