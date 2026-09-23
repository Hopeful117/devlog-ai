package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceResourceTest {
    private static final String REFERENCE =
            "git:dddd1111-2222-3333-4444-555555555555:3cd3723206eae38d518eb696a1dd50c0476264d0";

    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final EvidenceResource resource = new EvidenceResource(
            resourceClient,
            new ResourceSupport(mock(DevlogProjectContextClient.class), resourceClient,
                    new ObjectMapper()));

    @Test
    void shouldProxyCanonicalReferenceWithoutProjectIdentity() {
        when(resourceClient.resolveEvidence(REFERENCE, "CURRENT", null))
                .thenReturn("{\"metadata\":{\"canonicalReference\":\"" + REFERENCE + "\"}}");

        assertThat(resource.resolve(REFERENCE)).contains(REFERENCE);
        verify(resourceClient).resolveEvidence(REFERENCE, "CURRENT", null);
    }

    @Test
    void shouldRejectBlankReferenceBeforeCallingBackend() {
        assertThatThrownBy(() -> resource.resolve(" "))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("must not be blank");
    }
}
