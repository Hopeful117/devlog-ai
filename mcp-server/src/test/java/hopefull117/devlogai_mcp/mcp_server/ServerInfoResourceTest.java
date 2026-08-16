package hopefull117.devlogai_mcp.mcp_server;


import hopefull117.devlogai_mcp.mcp_server.resource.ServerInfoResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerInfoResourceTest {
    private final ServerInfoResource serverInfoResource = new ServerInfoResource();
    @Test
    void shouldReturnServerInfo() {
        String result = serverInfoResource.getServerInfo();
        assertThat(result)
                .contains("\"name\": \"devlog-mcp\"")
                .contains("\"version\": \"0.1.0\"")
                .contains("\"status\": \"ready\"");
    }
}
