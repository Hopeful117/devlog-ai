package com.hopeful117.devlogai.contextmaintenance.config;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "devlog.context-maintenance.agent")
public class MaintenanceAgentProperties {

    /**
     * Minimum confidence level for assessments to be persisted.
     * Assessments below this threshold are suppressed and logged for debugging.
     * Default: MEDIUM (assessments with LOW or VERY_LOW confidence are suppressed).
     */
    private MaintenanceAssessmentConfidenceLevel assessmentMinimumConfidence =
            MaintenanceAssessmentConfidenceLevel.MEDIUM;

    public MaintenanceAssessmentConfidenceLevel getAssessmentMinimumConfidence() {
        return assessmentMinimumConfidence;
    }

    public void setAssessmentMinimumConfidence(
            MaintenanceAssessmentConfidenceLevel assessmentMinimumConfidence
    ) {
        this.assessmentMinimumConfidence = assessmentMinimumConfidence;
    }

    /**
     * Returns true if the given confidence level meets the minimum threshold.
     */
    public boolean isAboveThreshold(MaintenanceAssessmentConfidenceLevel confidence) {
        if (confidence == null) {
            return false;
        }
        return confidence.ordinal() <= assessmentMinimumConfidence.ordinal();
    }
}
