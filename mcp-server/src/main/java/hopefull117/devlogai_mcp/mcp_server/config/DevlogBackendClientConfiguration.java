package hopefull117.devlogai_mcp.mcp_server.config;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class DevlogBackendClientConfiguration {

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

    private <T> T buildClient(String baseUrl, Class<T> clientType) {
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        RestClientAdapter adapter = RestClientAdapter.create(restClient);

        HttpServiceProxyFactory factory =
                HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(clientType);
    }
}
