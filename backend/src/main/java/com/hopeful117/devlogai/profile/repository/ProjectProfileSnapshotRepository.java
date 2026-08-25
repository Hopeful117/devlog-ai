package com.hopeful117.devlogai.profile.repository;

import com.hopeful117.devlogai.profile.entity.ProjectProfileSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectProfileSnapshotRepository extends JpaRepository<ProjectProfileSnapshot, UUID> {
    Optional<ProjectProfileSnapshot> findByAnalysisId(UUID analysisId);
    Optional<ProjectProfileSnapshot> findFirstByProjectIdOrderByGeneratedAtDescIdDesc(UUID projectId);

    @Query("select profile from ProjectProfileSnapshot profile join fetch profile.analysis analysis " +
            "where profile.project.id = :projectId and analysis.selectedSource.id = :sourceId " +
            "and analysis.status in (com.hopeful117.devlogai.analysis.entity.AnalysisStatus.COMPLETED, " +
            "com.hopeful117.devlogai.analysis.entity.AnalysisStatus.IN_PROGRESS) " +
            "and analysis.intentId = 'describe-project' and analysis.intentVersion = 'v1' " +
            "order by analysis.createdAt desc, analysis.id desc")
    List<ProjectProfileSnapshot> findLatestComparable(
            @Param("projectId") UUID projectId,
            @Param("sourceId") UUID sourceId,
            Pageable pageable);
}
