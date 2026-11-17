package hopefull117.devlogai_mcp.mcp_server.config;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.client.McpToolEventIngestClient;
import hopefull117.devlogai_mcp.mcp_server.companion.DevlogCompanionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class DevlogBackendClientConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DevlogBackendClientConfiguration.class);

    @Bean
    DevlogProjectContextClient devLogProjectContextClient(
            @Value("${devlog.backend.base-url}") String baseUrl
    ) {
        return buildClient(baseUrl, DevlogProjectContextClient.class);
    }

    @Bean
    DevlogResourceClient devlogResourceClient(
            @Value("${devlog.backend.base-url}") String baseUrl
    ) {
        return buildClient(baseUrl, DevlogResourceClient.class);
    }

    @Bean
    DevlogAdminClient devlogAdminClient(
            @Value("${devlog.backend.base-url}") String baseUrl
    ) {
        return buildClient(baseUrl, DevlogAdminClient.class);
    }

    @Bean
    DevlogCompanionClient devlogCompanionClient(
            @Value("${devlog.backend.base-url}") String baseUrl
    ) {
        return buildClient(baseUrl, DevlogCompanionClient.class);
    }

    @Bean
    McpToolEventIngestClient mcpToolEventIngestClient(
            @Value("${devlog.backend.base-url}") String baseUrl
    ) {
        return buildClient(baseUrl, McpToolEventIngestClient.class);
    }

    private <T> T buildClient(String baseUrl, Class<T> clientType) {
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(loggingInterceptor())
                .build();

        RestClientAdapter adapter = RestClientAdapter.create(restClient);

        HttpServiceProxyFactory factory =
                HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(clientType);
    }

    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            long startedAt = System.nanoTime();
            try {
                var response = execution.execute(request, body);
                log.info("DevLog backend request completed method={} uri={} status={} durationMs={}",
                        request.getMethod(), request.getURI(), response.getStatusCode().value(),
                        elapsedMillis(startedAt));
                return response;
            } catch (Exception exception) {
                log.error("DevLog backend request failed method={} uri={} durationMs={} exceptionType={} message={}",
                        request.getMethod(), request.getURI(), elapsedMillis(startedAt),
                        exception.getClass().getName(), exception.getMessage(), exception);
                throw exception;
            }
        };
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
