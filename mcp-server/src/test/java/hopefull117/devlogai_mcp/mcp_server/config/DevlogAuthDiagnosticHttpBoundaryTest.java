package hopefull117.devlogai_mcp.mcp_server.config;

import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import hopefull117.devlogai_mcp.mcp_server.service.CoreCallPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class DevlogAuthDiagnosticHttpBoundaryTest {

    @Test
    void preservesBackendDiagnosticThroughRestClientAndMcpMapping() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://backend/api/v1/projects/devlog-ai/context"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .header(DevlogAuthDiagnostic.HEADER_NAME, "TOKEN_EXPIRED"));
        RestClient client = builder.baseUrl("http://backend").build();

        assertThatThrownBy(() -> client.get()
                .uri("/api/v1/projects/devlog-ai/context")
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(RestClientResponseException.class)
                .satisfies(error -> {
                    RestClientResponseException responseException =
                            (RestClientResponseException) error;
                    McpError mapped = CoreCallPolicy.map(
                            responseException, "not found", false, "reading the resource");

                    assertThat(DevlogAuthDiagnostic.from(responseException))
                            .isEqualTo("TOKEN_EXPIRED");
                    assertThat(((java.util.Map<?, ?>) mapped.getJsonRpcError().data())
                            .get("authDiagnostic")).isEqualTo("TOKEN_EXPIRED");
                });

        server.verify();
    }
}
