package com.hopeful117.devlogai.profile.service;

import com.hopeful117.devlogai.profile.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProjectProfileRendererTest {
    private final ProjectProfileRenderer renderer = new ProjectProfileRenderer();

    @Test
    void shouldRenderFrenchSummaryFromStructuredCharacteristicsOnly() {
        List<ProfileSection> sections = List.of(
                section(ProfileCategory.ARCHITECTURE, "SPRING_BOOT_REST_APPLICATION"),
                section(ProfileCategory.INFRASTRUCTURE, "CONTAINERIZED_PROJECT"));

        String summary = renderer.render(sections, ProfileCompletenessStatus.COMPLETE);

        assertEquals("Le projet est une application REST développée avec Spring Boot. "
                + "Le projet est conteneurisé avec Docker et Docker Compose.", summary);
    }

    @Test
    void shouldExplicitlyQualifyPartialProfiles() {
        String summary = renderer.render(
                List.of(section(ProfileCategory.TESTING, "AUTOMATED_TESTS")),
                ProfileCompletenessStatus.PARTIAL);

        assertTrue(summary.startsWith("L’analyse partielle"));
        assertTrue(summary.endsWith("Certaines caractéristiques peuvent ne pas avoir été détectées."));
    }

    @Test
    void shouldRejectCharacteristicWithoutObservationTraceability() {
        assertThrows(IllegalArgumentException.class, () -> new ProfileCharacteristic(
                "CODE", "Label", "Description", CharacteristicStatus.CONFIRMED,
                List.of(), 0, Map.of()));
    }

    private ProfileSection section(ProfileCategory category, String code) {
        ProfileCharacteristic characteristic = new ProfileCharacteristic(
                code, code, code, CharacteristicStatus.CONFIRMED,
                List.of(UUID.randomUUID()), 1, Map.of());
        return new ProfileSection(category, List.of(characteristic), code);
    }
}
