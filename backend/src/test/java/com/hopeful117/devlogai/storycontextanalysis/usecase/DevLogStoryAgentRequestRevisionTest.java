package com.hopeful117.devlogai.storycontextanalysis.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevLogStoryAgentRequestRevisionTest {

    private static DevLogStoryAgentRequest request(String revision) {
        return new DevLogStoryAgentRequest("devlog-ai", null, "intent", "question?", List.of(), Map.of(),
                "key", revision);
    }

    @Test
    void aFullCommitIsNormalizedToLowerCase() {
        assertEquals("a".repeat(40), request(" " + "A".repeat(40) + " ").revision());
    }

    @Test
    void blankOrAbsentRevisionMeansNoPin() {
        assertNull(request(null).revision());
        assertNull(request("  ").revision());
        assertNull(new DevLogStoryAgentRequest("devlog-ai", null, "intent", "question?", List.of(), Map.of(), "key")
                .revision());
    }

    @Test
    void branchNamesAndShortShasCannotPinAnExecution() {
        assertThrows(IllegalArgumentException.class, () -> request("main"));
        assertThrows(IllegalArgumentException.class, () -> request("0673d1d"));
    }
}
