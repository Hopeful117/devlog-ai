package com.hopeful117.devlogai.agentpresence.adapter.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopeful117.devlogai.agentpresence.AgentPresenceMessage;
import com.hopeful117.devlogai.agentpresence.AgentPresencePort;
import com.hopeful117.devlogai.agentpresence.config.AgentPresenceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@Slf4j
public class HttpAgentPresenceAdapter implements AgentPresencePort {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpAgentPresenceAdapter(AgentPresenceProperties properties) {
        this(properties, new ObjectMapper());
    }

    HttpAgentPresenceAdapter(AgentPresenceProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(AgentPresenceMessage message) {
        AgentPresenceHttpRequest request = new AgentPresenceHttpRequest(
                message.source(),
                message.title(),
                message.body(),
                message.level().name()
        );

        final String json;

        try {
            json = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize Agent Presence message", e);
            return;
        }

        try {
            restClient.post()
                    .uri("/message")
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(json.getBytes(StandardCharsets.UTF_8).length)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Agent Presence device unavailable; notification dropped: {}", e.getMessage());
        }
    }
}
