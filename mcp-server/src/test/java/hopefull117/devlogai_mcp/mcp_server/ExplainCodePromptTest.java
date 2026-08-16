package hopefull117.devlogai_mcp.mcp_server;

import hopefull117.devlogai_mcp.mcp_server.prompt.ExplainCodePrompt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ExplainCodePromptTest {
    private final ExplainCodePrompt explainCodePrompt = new ExplainCodePrompt();
    @Test
    void shouldReturnPromptText() {
        String result=explainCodePrompt.explainCode("Java");
        assertThat(result)
                .contains("You are reviewing Java code.")
                .contains("Explain what the provided code does");

    }
}
