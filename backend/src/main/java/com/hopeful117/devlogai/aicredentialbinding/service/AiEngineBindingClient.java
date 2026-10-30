package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** Registers only opaque binding metadata with the AI Engine over signed HTTP. */
@Component
@RequiredArgsConstructor
public class AiEngineBindingClient {
    private static final String REGISTER_PATH = "/api/v1/ai/bindings/register";

    @Qualifier("aiEngineRestClient")
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @Value("${devlog.service-auth.secret:}")
    private String serviceSecret;

    public void register(AiCredentialBinding binding) {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            throw new IllegalStateException("AI Engine service authentication is not configured");
        }
        Map<String, Object> payload = Map.of(
                "bindingId", binding.getId(),
                "taskId", binding.getAiTask().getId(),
                "provider", binding.getProvider(),
                "credentialHandle", binding.getCredentialHandle(),
                "expiresAt", binding.getExpiresAt().toString());
        byte[] body = body(payload);
        long timestamp = clock.instant().getEpochSecond();
        String nonce = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        Map<String, String> headers = new AiEngineBindingRequestSigner(serviceSecret)
                .sign("POST", REGISTER_PATH, body, timestamp, nonce, requestId);

        restClient.post()
                .uri(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private byte[] body(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("unable to serialize AI binding registration", exception);
        }
    }
}
