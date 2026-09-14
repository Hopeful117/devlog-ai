package com.hopeful117.devlogai.ai.interactiontrace.repository;

import com.hopeful117.devlogai.ai.interactiontrace.entity.AiInteractionTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiInteractionTraceRepository extends JpaRepository<AiInteractionTrace, UUID> {
    List<AiInteractionTrace> findByAiTaskIdOrderByAttemptAscIdAsc(UUID aiTaskId);

    List<AiInteractionTrace> findByAnalysisIdOrderByAttemptAscIdAsc(UUID analysisId);
}
