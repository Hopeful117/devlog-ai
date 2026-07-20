package com.hopeful117.devlogai.artifact.repository;

import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {
    List<Artifact> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Artifact> findByProjectIdAndTypeOrderByCreatedAtDesc(
            UUID projectId,
            ArtifactType type
    );
}
