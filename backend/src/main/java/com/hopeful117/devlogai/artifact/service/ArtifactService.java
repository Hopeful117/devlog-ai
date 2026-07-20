package com.hopeful117.devlogai.artifact.service;

import com.hopeful117.devlogai.artifact.dto.request.CreateArtifactRequest;
import com.hopeful117.devlogai.artifact.dto.response.ArtifactResponse;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;

import java.util.List;
import java.util.UUID;

public interface ArtifactService {
    ArtifactResponse create(CreateArtifactRequest request);

    List<ArtifactResponse> getByProject(UUID projectId);

    List<ArtifactResponse> getByProjectAndType(
            UUID projectId,
            ArtifactType type
    );

    ArtifactResponse getById(UUID id);
}
