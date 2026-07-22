package com.hopeful117.devlogai.analysis.repository;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    @EntityGraph(attributePaths = "project")
    Optional<Analysis> findWithProjectById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select analysis from Analysis analysis where analysis.id = :id")
    Optional<Analysis> findByIdForUpdate(@Param("id") UUID id);
    List<Analysis> findByProjectIdOrderByCreatedAtDesc(
            UUID projectId
    );

    List<Analysis> findByProjectIdAndTypeOrderByCreatedAtDesc(
            UUID projectId,
            AnalysisType type
    );

    List<Analysis> findByProjectIdAndStatusOrderByCreatedAtDesc(
            UUID projectId,
            AnalysisStatus status
    );

    List<Analysis> findByProjectIdAndIdNotOrderByCreatedAtDescIdDesc(
            UUID projectId,
            UUID analysisId,
            Pageable pageable
    );
}
