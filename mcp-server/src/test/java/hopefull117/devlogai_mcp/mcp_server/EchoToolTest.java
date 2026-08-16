package hopefull117.devlogai_mcp.mcp_server;

import hopefull117.devlogai_mcp.mcp_server.tool.EchoTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EchoToolTest {
    private final EchoTool tool = new EchoTool();

    @Test
    void shouldReturnProvidedMessage() {
        String result = tool.echoMessage("hello");

        assertThat(result).isEqualTo("hello");
    }
}
