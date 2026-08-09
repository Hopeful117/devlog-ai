package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.history.context.CommitDiffAnalysisContext;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EngineeringEventExecutionKeyTest {
    @Test
    void createsAStableSha256IdentityForTheBoundedExecution() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        var intent = new IntentCatalog().resolve("analyze-engineering-event-v1");
        var context = mock(CommitDiffAnalysisContext.class);
        var first = new PreparedEngineeringEventExecution(projectId, sourceId, "a".repeat(40),
                "b".repeat(40), null, intent, Map.of("id", sourceId.toString()), context);
        var changed = new PreparedEngineeringEventExecution(projectId, sourceId, "a".repeat(40),
                "c".repeat(40), null, intent, Map.of("id", sourceId.toString()), context);
        EngineeringEventExecutionKey keys = new EngineeringEventExecutionKey(JsonMapper.builder().build());

        assertEquals(keys.compute(first), keys.compute(first));
        assertEquals(64, keys.compute(first).length());
        assertNotEquals(keys.compute(first), keys.compute(changed));
    }
}
