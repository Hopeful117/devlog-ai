package com.hopeful117.devlogai.temporal.service;

import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.temporal.domain.TemporalAssessment;

/**
 * Service for evaluating the temporal freshness of a trusted Insight
 * without mutating its governed authority.
 *
 * V1 uses repository-state transition comparison as the ONLY conclusion-producing
 * signal. ChangedFile / deletion history is corroborating evidence only.
 */
public interface TemporalAssessmentService {

    /**
     * Assess the temporal freshness of the given Insight.
     *
     * @param insight an ACTIVE Insight to assess
     * @return TemporalAssessment with conclusion CURRENT, SUSPECTED_STALE, or UNKNOWN
     * @throws IllegalStateException if the Insight is not ACTIVE (NOT_APPLICABLE)
     */
    TemporalAssessment assess(Insight insight);
}
