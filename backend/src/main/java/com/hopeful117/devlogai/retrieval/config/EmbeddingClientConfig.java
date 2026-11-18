package com.hopeful117.devlogai.retrieval.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({EmbeddingClientProperties.class, BackfillProperties.class})
public class EmbeddingClientConfig {

    @Bean("embeddingRestClient")
    RestClient embeddingRestClient(
            EmbeddingClientProperties properties,
            @Value("${ai-engine.base-url}") String baseUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(properties.timeout());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
