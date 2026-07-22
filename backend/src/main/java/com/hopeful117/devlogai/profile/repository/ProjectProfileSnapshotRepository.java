package com.hopeful117.devlogai.profile.repository;

import com.hopeful117.devlogai.profile.entity.ProjectProfileSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProjectProfileSnapshotRepository extends JpaRepository<ProjectProfileSnapshot, UUID> {
    Optional<ProjectProfileSnapshot> findByAnalysisId(UUID analysisId);
    Optional<ProjectProfileSnapshot> findFirstByProjectIdOrderByGeneratedAtDescIdDesc(UUID projectId);
}
