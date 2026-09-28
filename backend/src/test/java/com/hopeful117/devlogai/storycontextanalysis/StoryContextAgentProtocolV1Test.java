package com.hopeful117.devlogai.storycontextanalysis;

import com.hopeful117.devlogai.contracts.storycontextagent.StoryContextAgentProtocolV1;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoryContextAgentProtocolV1Test {
    @Test
    void taskIdentityRequiresSnapshotAliasAndCanonicalDigests() {
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> new StoryContextAgentProtocolV1.TaskIdentity(
                id, id, "a".repeat(64), "b".repeat(64), "sca/v1"));
        assertThrows(IllegalArgumentException.class, () -> new StoryContextAgentProtocolV1.TaskIdentity(
                id, UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "sca/v1"));
        assertThrows(IllegalArgumentException.class, () -> new StoryContextAgentProtocolV1.TaskIdentity(
                id, id, "A".repeat(64), "b".repeat(64), "sca/v1"));
    }

    @Test
    void accountingIsBoundedAndWarningsAreStableCopies() {
        assertDoesNotThrow(() -> new StoryContextAgentProtocolV1.Accounting(
                2, 1, 1, 8, 8, true, List.of(Map.of("code", "BUDGET"))));
        assertThrows(IllegalArgumentException.class, () -> new StoryContextAgentProtocolV1.Accounting(
                1, 1, 0, 9, 8, true, List.of()));
    }
}
