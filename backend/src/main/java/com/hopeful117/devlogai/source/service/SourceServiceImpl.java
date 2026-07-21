package com.hopeful117.devlogai.source.service;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.dto.request.CreateSourceRequest;
import com.hopeful117.devlogai.source.dto.response.SourceResponse;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.mapper.SourceMapper;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceServiceImpl implements SourceService {

    private final SourceRepository sourceRepository;
    private final ProjectRepository projectRepository;
    private final SourceMapper sourceMapper;

    @Override
    @Transactional
    public SourceResponse create(CreateSourceRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new EntityNotFoundException("Project", request.projectId()));

        Source source = sourceMapper.toEntity(request);
        source.setProject(project);
        source.setActive(true);

        return sourceMapper.toResponse(sourceRepository.save(source));
    }

    @Override
    public SourceResponse getById(UUID id) {
        return sourceMapper.toResponse(findSource(id));
    }

    @Override
    public List<SourceResponse> getByProject(UUID projectId) {
        requireProject(projectId);
        return sourceRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .stream()
                .map(sourceMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SourceResponse setActive(UUID id, boolean active) {
        Source source = findSource(id);
        source.setActive(active);
        return sourceMapper.toResponse(sourceRepository.save(source));
    }

    private Source findSource(UUID id) {
        return sourceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Source", id));
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project", projectId);
        }
    }
}
