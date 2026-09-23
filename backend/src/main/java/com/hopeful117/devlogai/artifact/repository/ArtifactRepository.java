package com.hopeful117.devlogai.artifact.repository;

import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {
    @EntityGraph(attributePaths = "project")
    java.util.Optional<Artifact> findDetailedById(UUID id);

    List<Artifact> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Artifact> findByProjectIdAndTypeOrderByCreatedAtDesc(
            UUID projectId,
            ArtifactType type
    );

    List<Artifact> findByProjectIdAndTypeInOrderByCreatedAtDescIdDesc(
            UUID projectId,
            List<ArtifactType> types,
            Pageable pageable
    );
}
