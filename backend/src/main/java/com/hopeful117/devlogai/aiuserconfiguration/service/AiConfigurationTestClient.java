package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiConfigurationTestStatus;
import com.hopeful117.devlogai.aicredentialbinding.service.AiEngineBindingRequestSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.net.SocketTimeoutException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiConfigurationTestClient {
    private static final String TEST_PATH = "/api/v1/ai/configuration/test";

    @Qualifier("aiEngineRestClient")
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @Value("${devlog.service-auth.secret:}") private String serviceSecret;

    public Result test(String provider, AiAuthenticationMethod authenticationMethod,
                       String model, String credentialHandle) {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            return new Result(AiConfigurationTestStatus.CONFIGURATION_INVALID, "SERVICE_AUTH_UNCONFIGURED");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("provider", provider);
        payload.put("authenticationMethod", authenticationMethod.name());
        payload.put("model", model);
        if (credentialHandle != null) payload.put("credentialHandle", credentialHandle);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
            Map<String, String> headers = new AiEngineBindingRequestSigner(serviceSecret).sign(
                    "POST", TEST_PATH, body, clock.instant().getEpochSecond(),
                    UUID.randomUUID().toString(), UUID.randomUUID().toString());
            Map<?, ?> response = restClient.post().uri(TEST_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .body(body).retrieve().body(Map.class);
            String status = response == null ? null : (String) response.get("status");
            String diagnostic = response == null ? null : (String) response.get("diagnosticCode");
            return new Result(AiConfigurationTestStatus.valueOf(status), diagnostic);
        } catch (RestClientResponseException exception) {
            return new Result(AiConfigurationTestStatus.PROVIDER_UNAVAILABLE, "AI_ENGINE_UNAVAILABLE");
        } catch (ResourceAccessException exception) {
            return new Result(hasTimeoutCause(exception)
                    ? AiConfigurationTestStatus.TIMEOUT
                    : AiConfigurationTestStatus.PROVIDER_UNAVAILABLE,
                    hasTimeoutCause(exception) ? "PROVIDER_TIMEOUT" : "AI_ENGINE_UNAVAILABLE");
        } catch (RuntimeException exception) {
            return new Result(AiConfigurationTestStatus.PROVIDER_UNAVAILABLE, "CONFIGURATION_TEST_FAILED");
        }
    }

    private static boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof InterruptedIOException
                    || cause instanceof TimeoutException) return true;
        }
        return false;
    }

    public record Result(AiConfigurationTestStatus status, String diagnosticCode) { }
}
