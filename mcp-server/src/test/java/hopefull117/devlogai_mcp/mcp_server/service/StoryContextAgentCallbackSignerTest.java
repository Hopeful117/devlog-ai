package hopefull117.devlogai_mcp.mcp_server.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryContextAgentCallbackSignerTest {
    @Test
    void signsCallbackAndFailsClosedWithoutSecret() {
        UUID taskId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        Map<String, Object> request = Map.of("status", "COMPLETED");
        String signature = new StoryContextAgentCallbackSigner(new ObjectMapper(), "secret")
                .sign(taskId, request);
        assertTrue(signature.matches("sha256=[0-9a-f]{64}"));
        assertThrows(IllegalStateException.class,
                () -> new StoryContextAgentCallbackSigner(new ObjectMapper(), "").sign(taskId, request));
    }
}
