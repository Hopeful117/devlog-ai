package hopefull117.devlogai_mcp.mcp_server.resource;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResourceSupportTest {

    private static final UUID PROJECT_ID = UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
    private static final String SLUG = "devlog-ai";

    private final DevlogProjectContextClient projectContextClient = mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requireProjectId_usesAuthenticatedEndpointWhenPrincipalAvailable() {
        McpAuthenticatedPrincipalProvider principalProvider = mock(McpAuthenticatedPrincipalProvider.class);
        McpAuthenticatedPrincipalProvider.McpPrincipal principal =
                new McpAuthenticatedPrincipalProvider.McpPrincipal(
                        "agent-id", McpAuthenticatedPrincipalProvider.PrincipalKind.AI_AGENT,
                        "http://issuer", "Bearer tok", null);
        when(principalProvider.currentPrincipal()).thenReturn(Optional.of(principal));
        when(projectContextClient.getProjectContextAuthenticated(eq(SLUG), eq("Bearer tok"), eq("")))
                .thenReturn(new ProjectContext(PROJECT_ID, SLUG, SLUG, "", "ACTIVE", List.of()));

        ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper, principalProvider);

        UUID id = support.requireProjectId(SLUG);

        assertThat(id).isEqualTo(PROJECT_ID);
        verify(projectContextClient).getProjectContextAuthenticated(SLUG, "Bearer tok", "");
        verify(projectContextClient, never()).getProjectContext(any());
    }

    @Test
    void requireProjectId_passesdelegationTokenWhenPresent() {
        McpAuthenticatedPrincipalProvider principalProvider = mock(McpAuthenticatedPrincipalProvider.class);
        McpAuthenticatedPrincipalProvider.McpPrincipal principal =
                new McpAuthenticatedPrincipalProvider.McpPrincipal(
                        "user-id", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                        "http://issuer", "Bearer tok", "delegate-token");
        when(principalProvider.currentPrincipal()).thenReturn(Optional.of(principal));
        when(projectContextClient.getProjectContextAuthenticated(eq(SLUG), eq("Bearer tok"), eq("delegate-token")))
                .thenReturn(new ProjectContext(PROJECT_ID, SLUG, SLUG, "", "ACTIVE", List.of()));

        ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper, principalProvider);

        UUID id = support.requireProjectId(SLUG);

        assertThat(id).isEqualTo(PROJECT_ID);
        verify(projectContextClient).getProjectContextAuthenticated(SLUG, "Bearer tok", "delegate-token");
    }

    @Test
    void requireProjectId_fallsBackToUnauthenticatedWhenNoPrincipal() {
        McpAuthenticatedPrincipalProvider principalProvider = mock(McpAuthenticatedPrincipalProvider.class);
        when(principalProvider.currentPrincipal()).thenReturn(Optional.empty());
        when(projectContextClient.getProjectContext(SLUG))
                .thenReturn(new ProjectContext(PROJECT_ID, SLUG, SLUG, "", "ACTIVE", List.of()));

        ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper, principalProvider);

        UUID id = support.requireProjectId(SLUG);

        assertThat(id).isEqualTo(PROJECT_ID);
        verify(projectContextClient).getProjectContext(SLUG);
    }

    @Test
    void requireProjectId_throwsNotFoundForUnknownSlug() {
        ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper);
        when(projectContextClient.getProjectContext(SLUG))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> support.requireProjectId(SLUG))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("not found");
    }

    @Test
    void mapsBackendUnauthorizedAsCoreAuthenticationWithProvenance() {
        ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper);
        when(resourceClient.listProjectInsights(PROJECT_ID))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> support.findActiveInsight(PROJECT_ID, "insight-id"))
                .isInstanceOf(McpError.class)
                .satisfies(exception -> {
                    McpError error = (McpError) exception;
                    assertThat(error.getJsonRpcError().message())
                            .isEqualTo("Authentication is required to access DevLog backend");
                    assertThat(error.getJsonRpcError().data())
                            .isEqualTo(java.util.Map.of(
                                    "source", "CORE", "category", "CORE_AUTHENTICATION",
                                    "code", "CORE_AUTHENTICATION_FAILED", "layer", "CORE",
                                    "retryable", false, "action", "CHECK_CORE_AUTHORIZATION"));
                    assertThat(objectMapper.writeValueAsString(error.getJsonRpcError()))
                            .contains("\"data\":")
                            .contains("\"category\":\"CORE_AUTHENTICATION\"")
                            .contains("\"source\":\"CORE\"");
                });
    }

    @Test
    void mapsCoreTimeoutAndServerFailureAtResourceBoundary() {
        ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper);

        for (RuntimeException failure : List.of(
                new ResourceAccessException("backend timeout"),
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null))) {
            assertThatThrownBy(() -> support.getScoped(() -> {
                throw failure;
            }, "resource not found"))
                    .isInstanceOf(McpError.class)
                    .satisfies(exception -> {
                        McpError error = (McpError) exception;
                        assertThat(error.getJsonRpcError().data())
                                .isEqualTo(java.util.Map.of(
                                        "source", "CORE", "category", "TRANSIENT_INFRASTRUCTURE",
                                        "code", "TRANSIENT_INFRASTRUCTURE", "layer", "CORE",
                                        "retryable", false, "action", "RETRY_ONLY_IF_OPERATION_SAFE"));
                    });
        }
    }

    @Test
    void mapsProjectResolutionUnauthorizedAsCoreAuthentication() {
        when(projectContextClient.getProjectContext(SLUG))
                .thenThrow(unauthorized());

        ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper);

        assertCoreAuthentication(() -> support.requireProjectId(SLUG));
    }

    @Test
    void mapsAuthorizedSnapshotUnauthorizedAsCoreAuthentication() {
        ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper);

        assertCoreAuthentication(() -> support.getAuthorizedSnapshot(() -> {
            throw unauthorized();
        }, "snapshot not found"));
    }

    @Test
    void mapsAuthorizedFollowUpUnauthorizedAsCoreAuthentication() {
        ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper);

        assertCoreAuthentication(() -> support.submitAuthorizedSnapshot(() -> {
            throw unauthorized();
        }, "snapshot not found"));
    }

    private void assertCoreAuthentication(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(McpError.class)
                .satisfies(exception -> {
                    McpError error = (McpError) exception;
                    assertThat(error.getJsonRpcError().data())
                            .isEqualTo(java.util.Map.of(
                                    "source", "CORE", "category", "CORE_AUTHENTICATION",
                                    "code", "CORE_AUTHENTICATION_FAILED", "layer", "CORE",
                                    "retryable", false, "action", "CHECK_CORE_AUTHORIZATION"));
                });
    }

    private static HttpClientErrorException unauthorized() {
        return HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
    }
}
