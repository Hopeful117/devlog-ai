package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiEngineCredentialAssociationClient {
    private static final String VERIFY_PATH = "/api/v1/ai/bindings/credentials/verify";

    @Qualifier("aiEngineRestClient")
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @Value("${devlog.service-auth.secret:}")
    private String serviceSecret;

    public void verify(AiCredentialAssociation association) {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            throw new IllegalStateException("AI Engine service authentication is not configured");
        }
        Map<String, Object> payload = Map.of(
                "credentialHandle", association.getCredentialHandle(),
                "provider", association.getProvider(),
                "subject", association.getChatgptSubject());
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("unable to serialize credential association verification", exception);
        }
        Map<String, String> headers = new AiEngineBindingRequestSigner(serviceSecret)
                .sign("POST", VERIFY_PATH, body, clock.instant().getEpochSecond(),
                        UUID.randomUUID().toString(), UUID.randomUUID().toString());
        Map<?, ?> response;
        try {
            response = restClient.post().uri(VERIFY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .body(body).retrieve().body(Map.class);
        } catch (RestClientResponseException exception) {
            throw new CredentialAssociationVerificationException();
        }
        if (!Boolean.TRUE.equals(response == null ? null : response.get("verified"))) {
            throw new CredentialAssociationVerificationException();
        }
    }
}
