package com.hopeful117.devlogai.intent.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserGuidanceJsonTest {

    @Test
    void shouldNotExposeDerivedEmptyPropertyInAiEngineContract() throws Exception {
        UserGuidance guidance = new UserGuidance(
                "Architecture",
                "Developers",
                "Concise",
                "Technical",
                "Internal presentation",
                List.of("Explain the components")
        );

        String json = new ObjectMapper().writeValueAsString(guidance);

        assertFalse(json.contains("\"empty\""));
        assertTrue(json.contains("\"focus\":\"Architecture\""));
    }
}
