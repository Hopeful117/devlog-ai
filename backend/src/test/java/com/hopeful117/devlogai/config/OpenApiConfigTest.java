package com.hopeful117.devlogai.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();

    @Test
    void shouldDefineCoreApiMetadata() {
        OpenAPI openAPI = openApiConfig.devlogAiOpenApi();

        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("DevLog AI Core API");
        assertThat(openAPI.getInfo().getDescription()).isEqualTo("REST API of the DevLog AI Java Core");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
    }
}
