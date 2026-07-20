package com.hopeful117.devlogai.artifact.service;

import com.hopeful117.devlogai.artifact.dto.request.CreateArtifactRequest;
import com.hopeful117.devlogai.artifact.dto.response.ArtifactResponse;
import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.artifact.mapper.ArtifactMapper;
import com.hopeful117.devlogai.artifact.repository.ArtifactRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArtifactServiceImpl implements ArtifactService{
    private final ArtifactRepository artifactRepository;

    private final ProjectRepository projectRepository;

    private final ArtifactMapper artifactMapper;

    @Override
    public ArtifactResponse create(CreateArtifactRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Project",
                                request.getProjectId()
                        )
                );


        Artifact artifact = artifactMapper.toEntity(request);

        artifact.setProject(project);


        Artifact savedArtifact =
                artifactRepository.save(artifact);


        return artifactMapper.toResponse(savedArtifact);
    }

    @Override
    public List<ArtifactResponse> getByProject(UUID projectId) {

        return artifactRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(artifactMapper::toResponse)
                .toList();
    }

    @Override
    public List<ArtifactResponse> getByProjectAndType(UUID projectId, ArtifactType type) {
        return artifactRepository
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        type
                )
                .stream()
                .map(artifactMapper::toResponse)
                .toList();
    }

    @Override
    public ArtifactResponse getById(UUID id) {
        Artifact artifact = artifactRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Artifact",
                                id
                        )
                );


        return artifactMapper.toResponse(artifact);
    }
    }

