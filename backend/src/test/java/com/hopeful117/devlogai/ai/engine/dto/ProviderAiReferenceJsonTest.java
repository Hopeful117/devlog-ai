package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderAiReferenceJsonTest {
    @Test
    void serializesTheThreeFieldProviderContract() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new ProviderAiReference(
                AiReferenceType.FACT, "F001", AiReferenceScope.ANALYSIS_CONTEXT));

        assertEquals("{\"type\":\"FACT\",\"ref\":\"F001\",\"scope\":\"ANALYSIS_CONTEXT\"}", json);
        assertFalse(json.contains("canonicalSourceIdentity"));
        assertFalse(json.contains("groundingCapabilities"));
        assertTrue(json.contains("\"ref\":\"F001\""));
    }
}
