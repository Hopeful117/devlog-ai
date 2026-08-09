package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectUnderstandingExecutionKeyTest {
    private final ProjectUnderstandingExecutionKey keys =
            new ProjectUnderstandingExecutionKey(new ObjectMapper());
    private final IntentDefinition intent = new IntentDefinition(
            "describe-project", "v1", "describe", List.of(), List.of(), Map.of(), "prompt");
    private final UUID project = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final UUID source = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void normalizesDefaultRevisionAndTrimmedGuidanceDeterministically() {
        String first = keys.compute(project, source, null, intent,
                new UserGuidance(" architecture ", null, null, null, null, List.of(" API ")));
        String second = keys.compute(project, source, "  ", intent,
                new UserGuidance("architecture", null, null, null, null, List.of("API")));

        assertThat(first).hasSize(64).isEqualTo(second);
    }

    @Test
    void changesForARequestedRevisionOrSource() {
        String baseline = keys.compute(project, source, null, intent, null);

        assertThat(keys.compute(project, source, "main", intent, null)).isNotEqualTo(baseline);
        assertThat(keys.compute(project, UUID.randomUUID(), null, intent, null)).isNotEqualTo(baseline);
    }
}
