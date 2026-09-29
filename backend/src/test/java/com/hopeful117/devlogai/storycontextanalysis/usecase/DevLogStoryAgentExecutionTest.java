package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevLogStoryAgentExecutionTest {

    @Test
    void acceptsCompletedExecution() {
        UUID taskId = UUID.randomUUID();

        DevLogStoryAgentExecution execution = execution(AiTaskStatus.COMPLETED, taskId, taskId);

        assertEquals(AiTaskStatus.COMPLETED, execution.status());
        assertEquals(taskId, execution.aiTaskId());
        assertEquals(taskId, execution.snapshotId());
    }

    @Test
    void acceptsProcessingExecutionWithEmptyResult() {
        UUID taskId = UUID.randomUUID();

        DevLogStoryAgentExecution execution = new DevLogStoryAgentExecution(
                AiTaskStatus.PROCESSING, taskId, taskId, Map.of(), Map.of());

        assertEquals(Map.of(), execution.result());
    }

    @Test
    void rejectsNullRequiredValues() {
        UUID taskId = UUID.randomUUID();

        assertThrows(NullPointerException.class,
                () -> new DevLogStoryAgentExecution(null, taskId, taskId, Map.of(), Map.of()));
        assertThrows(NullPointerException.class,
                () -> new DevLogStoryAgentExecution(AiTaskStatus.COMPLETED, null, taskId, Map.of(), Map.of()));
        assertThrows(NullPointerException.class,
                () -> new DevLogStoryAgentExecution(AiTaskStatus.COMPLETED, taskId, null, Map.of(), Map.of()));
    }

    @Test
    void rejectsDifferentTaskAndSnapshotIds() {
        assertThrows(IllegalArgumentException.class,
                () -> execution(AiTaskStatus.COMPLETED, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void copiesMutableMaps() {
        UUID taskId = UUID.randomUUID();
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> diagnostics = new HashMap<>();
        result.put("confidence", "HIGH");
        diagnostics.put("attempt", 1);

        DevLogStoryAgentExecution execution =
                new DevLogStoryAgentExecution(AiTaskStatus.COMPLETED, taskId, taskId, result, diagnostics);

        result.put("mutated", true);
        diagnostics.put("mutated", true);

        assertEquals(Map.of("confidence", "HIGH"), execution.result());
        assertEquals(Map.of("attempt", 1), execution.diagnostics());
    }

    private static DevLogStoryAgentExecution execution(
            AiTaskStatus status, UUID aiTaskId, UUID snapshotId) {
        return new DevLogStoryAgentExecution(status, aiTaskId, snapshotId, Map.of(), Map.of());
    }
}
