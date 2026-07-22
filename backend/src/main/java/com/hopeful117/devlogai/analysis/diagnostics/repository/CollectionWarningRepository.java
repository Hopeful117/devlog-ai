package com.hopeful117.devlogai.analysis.diagnostics.repository;

import com.hopeful117.devlogai.analysis.diagnostics.entity.CollectionWarningEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CollectionWarningRepository extends JpaRepository<CollectionWarningEntity, UUID> {
    List<CollectionWarningEntity> findByAnalysisIdOrderByOccurredAtAscIdAsc(UUID analysisId);
}
