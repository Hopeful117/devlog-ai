package com.hopeful117.devlogai.storycontextanalysis.usecase;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevLogStoryAgentRequestTest {

    @Test
    void acceptsEmptyFilesAndNormalizesOptionalValues() {
        DevLogStoryAgentRequest request = new DevLogStoryAgentRequest(
                "devlog-ai", null, "engineering-story-context-analysis", "What changed?",
                List.of(), null, "   ");

        assertEquals(List.of(), request.files());
        assertEquals("What changed?", request.question());
        assertEquals(Map.of(), request.guidance());
        assertEquals(null, request.idempotencyKey());
    }

    @Test
    void rejectsBlankRequiredValues() {
        assertThrows(IllegalArgumentException.class, () -> request(" ", "intent"));
        assertThrows(IllegalArgumentException.class, () -> request("project", ""));
    }

    @Test
    void rejectsNullOrBlankFiles() {
        assertThrows(NullPointerException.class,
                () -> new DevLogStoryAgentRequest("project", null, "intent", "question", null, null, null));
        assertThrows(NullPointerException.class,
                () -> new DevLogStoryAgentRequest("project", null, "intent", "question", List.of((String) null), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DevLogStoryAgentRequest("project", null, "intent", "question", List.of(" "), null, null));
    }

    @Test
    void copiesMutableCollections() {
        List<String> files = new ArrayList<>(List.of("README.md"));
        Map<String, Object> guidance = new HashMap<>(Map.of("focus", "contracts"));

        DevLogStoryAgentRequest request =
                new DevLogStoryAgentRequest("project", null, "intent", "question", files, guidance, null);

        files.add("docs/story.md");
        guidance.put("audience", "developer");

        assertEquals(List.of("README.md"), request.files());
        assertEquals(Map.of("focus", "contracts"), request.guidance());
    }

    private static DevLogStoryAgentRequest request(String projectSlug, String intent) {
        return new DevLogStoryAgentRequest(projectSlug, null, intent, "question", List.of(), null, null);
    }
}
