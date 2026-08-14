package com.hopeful117.devlogai.contextmaintenance.config;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceAgentPropertiesTest {

    private final MaintenanceAgentProperties properties = new MaintenanceAgentProperties();

    @Test
    void shouldAcceptHighConfidenceWhenThresholdIsMedium() {
        properties.setAssessmentMinimumConfidence(MaintenanceAssessmentConfidenceLevel.MEDIUM);
        assertTrue(properties.isAboveThreshold(MaintenanceAssessmentConfidenceLevel.HIGH));
    }

    @Test
    void shouldAcceptMediumConfidenceWhenThresholdIsMedium() {
        properties.setAssessmentMinimumConfidence(MaintenanceAssessmentConfidenceLevel.MEDIUM);
        assertTrue(properties.isAboveThreshold(MaintenanceAssessmentConfidenceLevel.MEDIUM));
    }

    @Test
    void shouldRejectLowConfidenceWhenThresholdIsMedium() {
        properties.setAssessmentMinimumConfidence(MaintenanceAssessmentConfidenceLevel.MEDIUM);
        assertFalse(properties.isAboveThreshold(MaintenanceAssessmentConfidenceLevel.LOW));
    }

    @Test
    void shouldRejectVeryLowConfidenceWhenThresholdIsMedium() {
        properties.setAssessmentMinimumConfidence(MaintenanceAssessmentConfidenceLevel.MEDIUM);
        assertFalse(properties.isAboveThreshold(MaintenanceAssessmentConfidenceLevel.VERY_LOW));
    }

    @Test
    void shouldAcceptLowConfidenceWhenThresholdIsLow() {
        properties.setAssessmentMinimumConfidence(MaintenanceAssessmentConfidenceLevel.LOW);
        assertTrue(properties.isAboveThreshold(MaintenanceAssessmentConfidenceLevel.LOW));
    }

    @Test
    void shouldRejectVeryLowConfidenceWhenThresholdIsLow() {
        properties.setAssessmentMinimumConfidence(MaintenanceAssessmentConfidenceLevel.LOW);
        assertFalse(properties.isAboveThreshold(MaintenanceAssessmentConfidenceLevel.VERY_LOW));
    }

    @Test
    void shouldRejectNullConfidence() {
        properties.setAssessmentMinimumConfidence(MaintenanceAssessmentConfidenceLevel.MEDIUM);
        assertFalse(properties.isAboveThreshold(null));
    }
}
