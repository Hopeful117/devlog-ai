package com.hopeful117.devlogai.projectcontextinput.service;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.projectcontextinput.dto.request.CreateProjectHumanContextInputRequest;
import com.hopeful117.devlogai.projectcontextinput.dto.response.ProjectHumanContextInputResponse;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInput;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.mapper.ProjectHumanContextInputMapper;
import com.hopeful117.devlogai.projectcontextinput.repository.ProjectHumanContextInputRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectHumanContextInputServiceImpl implements ProjectHumanContextInputService {

    private final ProjectRepository projectRepository;
    private final ProjectHumanContextInputRepository repository;
    private final ProjectHumanContextInputMapper mapper;

    @Override
    @Transactional
    public ProjectHumanContextInputResponse create(
            UUID projectId,
            CreateProjectHumanContextInputRequest request
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));

        ProjectHumanContextInput input = ProjectHumanContextInput.builder()
                .project(project)
                .title(request.title().trim())
                .contentMarkdown(request.contentMarkdown().trim())
                .type(request.type())
                .status(ProjectHumanContextInputStatus.ACTIVE)
                .build();

        return mapper.toResponse(repository.save(input));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectHumanContextInputResponse> getByProject(UUID projectId) {
        ensureProjectExists(projectId);
        return mapper.toResponse(repository.findByProject_IdOrderByUpdatedAtDescIdDesc(projectId));
    }

    @Override
    @Transactional
    public ProjectHumanContextInputResponse archive(UUID projectId, UUID inputId) {
        ensureProjectExists(projectId);
        ProjectHumanContextInput input = repository.findByIdAndProject_Id(inputId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("ProjectHumanContextInput", inputId));
        input.setStatus(ProjectHumanContextInputStatus.ARCHIVED);
        return mapper.toResponse(repository.save(input));
    }

    private void ensureProjectExists(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project", projectId);
        }
    }
}
