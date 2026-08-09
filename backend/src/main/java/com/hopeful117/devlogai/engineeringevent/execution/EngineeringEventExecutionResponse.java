package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.engineeringevent.EvolutionComparisonPolicy;
import java.util.UUID;

public record EngineeringEventExecutionResponse(
        String version, UUID analysisId, AnalysisStatus status, UUID projectId, UUID sourceId,
        String baseCommit, String targetCommit, EvolutionComparisonPolicy comparisonPolicy,
        boolean mergeCommit, String intentId, String intentVersion, Outcome outcome) {
    public static final String PROJECTION_VERSION = "engineering-event-execution-v1";
    public enum Outcome { CREATED, REUSED }
}
