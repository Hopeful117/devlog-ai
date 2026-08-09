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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService{

    private static final String PROJECT_ENTITY = "Project";

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
                        new EntityNotFoundException(PROJECT_ENTITY, slug)
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
    @Transactional
    public ProjectResponse update(String slug, UpdateProjectRequest request) {
        Project project = projectRepository.findBySlug(slug)
                .orElseThrow(() ->
                        new EntityNotFoundException(PROJECT_ENTITY, slug)
                );


        if (request.getName() != null) {
            String normalizedName = request.getName().trim();
            if (projectRepository.existsByNameAndIdNot(normalizedName, project.getId())) {
                throw duplicateName(normalizedName);
            }
            request.setName(normalizedName);
        }

        projectMapper.updateProject(request, project);

        try {
            project = projectRepository.saveAndFlush(project);
        } catch (DataIntegrityViolationException exception) {
            if (request.getName() != null && violatesProjectNameConstraint(exception)) {
                throw duplicateName(request.getName());
            }
            throw exception;
        }

        return projectMapper.toResponse(project);
    }

    @Override
    public void archive(String slug) {
        Project project = projectRepository.findBySlug(slug)
                .orElseThrow(() ->
                        new EntityNotFoundException(PROJECT_ENTITY, slug)
                );

        project.setStatus(ProjectStatus.ARCHIVED);

        projectRepository.save(project);

    }

    @Override
    @Transactional
    public void delete(String slug) {
        Project project = projectRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException(PROJECT_ENTITY, slug));

        projectRepository.delete(project);
        projectRepository.flush();
    }

    private ConflictException duplicateName(String name) {
        return new ConflictException("A project named '%s' already exists".formatted(name));
    }

    private boolean violatesProjectNameConstraint(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("projects_name_key")
                    || message.contains("uk_projects_name"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
