package com.hopeful117.devlogai.insight.repository;

import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InsightRepository extends JpaRepository<Insight, UUID> {

    List<Insight> findByProjectIdOrderByCreatedAtDesc(
            UUID projectId
    );

    List<Insight> findByAnalysisIdOrderByCreatedAtDesc(
            UUID analysisId
    );

    List<Insight> findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId);

    List<Insight> findByAnalysisIdOrderByCreatedAtDescIdDesc(UUID analysisId);

    List<Insight> findByProjectIdAndTypeOrderByCreatedAtDesc(
            UUID projectId,
            InsightType type
    );

    List<Insight> findByProjectIdAndSeverityOrderByCreatedAtDesc(
            UUID projectId,
            InsightSeverity severity
    );

    List<Insight> findByProjectIdAndTypeAndSeverityOrderByCreatedAtDesc(
            UUID projectId,
            InsightType type,
            InsightSeverity severity
    );
}
