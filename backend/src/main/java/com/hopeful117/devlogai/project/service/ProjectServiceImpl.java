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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService{

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final SlugService slugService;

    @Override
    public ProjectResponse create(CreateProjectRequest request) {
       String slug = slugService.generate(request.getName());
        if(projectRepository.existsBySlug(slug)){
            throw new ProjectSlugAlreadyExistsException(slug);
        }
        Project project = projectMapper.toEntity(request);
        project.setSlug(slug);
        project.setStatus(ProjectStatus.ACTIVE);
        project=projectRepository.save(project);
        return projectMapper.toResponse(project);
    }

    @Override
    public ProjectResponse getBySlug(String slug) {
        Project project = projectRepository.findBySlug(slug)
                .orElseThrow(() ->
                        new EntityNotFoundException("Project", slug)
                );

        return projectMapper.toResponse(project);
    }

    @Override
    public List<ProjectResponse> getAll() {

        return projectRepository.findAll()
                .stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    @Override
    public ProjectResponse update(String slug, UpdateProjectRequest request) {
        Project project = projectRepository.findBySlug(slug)
                .orElseThrow(() ->
                        new EntityNotFoundException("Project", slug)
                );


        projectMapper.updateProject(request, project);

        project = projectRepository.save(project);

        return projectMapper.toResponse(project);
    }

    @Override
    public void archive(String slug) {
        Project project = projectRepository.findBySlug(slug)
                .orElseThrow(() ->
                        new EntityNotFoundException("Project", slug)
                );

        project.setStatus(ProjectStatus.ARCHIVED);

        projectRepository.save(project);

    }
}
