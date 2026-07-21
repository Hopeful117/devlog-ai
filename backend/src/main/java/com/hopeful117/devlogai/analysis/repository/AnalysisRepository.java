package com.hopeful117.devlogai.analysis.repository;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
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
