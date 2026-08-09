package com.hopeful117.devlogai.projectfreshness;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

interface ProjectSourceFreshnessRepository extends JpaRepository<ProjectSourceFreshness, UUID> {
    @EntityGraph(attributePaths = {"project", "source", "baselineAnalysis"})
    Optional<ProjectSourceFreshness> findByProjectIdAndSourceId(UUID projectId, UUID sourceId);
}
