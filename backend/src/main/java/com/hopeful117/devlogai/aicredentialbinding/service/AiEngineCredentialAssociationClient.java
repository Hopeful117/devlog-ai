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
import java.util.HashMap;
import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState;

@Component
@RequiredArgsConstructor
public class AiEngineCredentialAssociationClient {
    private static final String VERIFY_PATH = "/api/v1/ai/bindings/credentials/verify";
    private static final String REVOKE_PATH = "/api/v1/ai/bindings/credentials/revoke";
    private static final String COMPLETION_REDEEM_PATH = "/api/v1/auth/chatgpt/completion/redeem";
    private static final String COMPLETION_ACK_PATH = "/api/v1/auth/chatgpt/completion/ack";
    private static final String COMPLETION_COMPENSATE_PATH = "/api/v1/auth/chatgpt/completion/compensate";
    private static final String API_KEY_COMPENSATE_PATH = "/api/v1/auth/api-key/compensate";

    @Qualifier("aiEngineRestClient")
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @Value("${devlog.service-auth.secret:}")
    private String serviceSecret;

    public VerificationResult verify(AiCredentialAssociation association) {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            throw new IllegalStateException("AI Engine service authentication is not configured");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("credentialHandle", association.getCredentialHandle());
        payload.put("provider", association.getProvider());
        payload.put("authenticationMethod", association.getAuthenticationMethod() == null
                ? AiAuthenticationMethod.OAUTH.name() : association.getAuthenticationMethod().name());
        if (association.getChatgptSubject() != null) payload.put("subject", association.getChatgptSubject());
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
        String state = response.get("state") instanceof String value ? value : "IDENTITY_AUTHORIZED";
        String scopes = response.get("scopes") instanceof String value ? value : "";
        try {
            return new VerificationResult(AiCredentialAssociationState.valueOf(state), scopes);
        } catch (IllegalArgumentException exception) {
            throw new CredentialAssociationVerificationException();
        }
    }

    public void revoke(String credentialHandle, String provider) {
        request(REVOKE_PATH, Map.of(
                "credentialHandle", credentialHandle,
                "provider", provider));
    }

    public void verifyApiKeyEnrollment(UUID intentId, String credentialHandle, String principalBinding) {
        Map<String, String> payload = new HashMap<>();
        payload.put("credentialHandle", credentialHandle);
        payload.put("provider", "anthropic");
        payload.put("authenticationMethod", AiAuthenticationMethod.API_KEY.name());
        payload.put("intentId", intentId.toString());
        payload.put("principalBinding", principalBinding);
        Map<?, ?> response = request(VERIFY_PATH, payload);
        if (!Boolean.TRUE.equals(response.get("verified"))) {
            throw new CredentialAssociationVerificationException();
        }
    }

    public OAuthCompletion redeemCompletion(String intentId, String completionCode) {
        Map<?, ?> response = request(COMPLETION_REDEEM_PATH, Map.of(
                "intentId", intentId,
                "completionCode", completionCode));
        try {
            return new OAuthCompletion(
                    (String) response.get("credentialHandle"),
                    (String) response.get("subject"),
                    (String) response.get("scopes"),
                    (String) response.get("clientId"));
        } catch (RuntimeException exception) {
            throw new CredentialAssociationVerificationException();
        }
    }

    public void acknowledgeCompletion(String intentId, String completionCode) {
        request(COMPLETION_ACK_PATH, Map.of(
                "intentId", intentId,
                "completionCode", completionCode));
    }

    public void compensateCompletion(String intentId, String completionCode) {
        request(COMPLETION_COMPENSATE_PATH, Map.of(
                "intentId", intentId,
                "completionCode", completionCode));
    }

    public void compensateApiKey(String credentialHandle) {
        request(API_KEY_COMPENSATE_PATH, Map.of("credentialHandle", credentialHandle));
    }

    private Map<?, ?> request(String path, Map<String, String> payload) {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            throw new IllegalStateException("AI Engine service authentication is not configured");
        }
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("unable to serialize AI Engine request", exception);
        }
        Map<String, String> headers = new AiEngineBindingRequestSigner(serviceSecret)
                .sign("POST", path, body, clock.instant().getEpochSecond(),
                        UUID.randomUUID().toString(), UUID.randomUUID().toString());
        try {
            Map<?, ?> response = restClient.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .body(body).retrieve().body(Map.class);
            return response == null ? Map.of() : response;
        } catch (RestClientResponseException exception) {
            throw new CredentialAssociationVerificationException();
        }
    }

    public record VerificationResult(AiCredentialAssociationState state, String grantedScopes) { }

    public record OAuthCompletion(String credentialHandle, String subject,
                                  String scopes, String clientId) { }
}
