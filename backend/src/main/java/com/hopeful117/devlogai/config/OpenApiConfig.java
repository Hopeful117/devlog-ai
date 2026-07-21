package com.hopeful117.devlogai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI devlogAiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DevLog AI Core API")
                        .description("REST API of the DevLog AI Java Core")
                        .version("v1"));
    }
}
