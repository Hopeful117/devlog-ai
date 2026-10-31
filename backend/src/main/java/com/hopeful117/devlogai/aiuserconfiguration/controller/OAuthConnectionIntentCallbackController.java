package com.hopeful117.devlogai.aiuserconfiguration.controller;

import com.hopeful117.devlogai.aiuserconfiguration.service.OAuthConnectionIntentService;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/configuration/oauth/chatgpt")
public class OAuthConnectionIntentCallbackController {
    private final OAuthConnectionIntentService service;
    private final StoryContextAgentCallbackAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    public OAuthConnectionIntentCallbackController(OAuthConnectionIntentService service,
                                                   StoryContextAgentCallbackAuthenticator authenticator,
                                                   ObjectMapper objectMapper) {
        this.service = service;
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> complete(
            HttpServletRequest request,
            @RequestHeader(value = "X-DevLog-Service-Signature", required = false) String signature,
            @RequestHeader(value = "X-DevLog-Service-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-DevLog-Service-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-DevLog-Service-Request-Id", required = false) String requestId,
            @RequestBody byte[] body) {
        if (!authenticator.isServiceValid(request.getMethod(), request.getRequestURI(), body,
                signature, timestamp, nonce, requestId)) {
            return ResponseEntity.status(401).build();
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(body, Map.class);
            UUID intentId = UUID.fromString(String.valueOf(payload.get("intentId")));
            String completionCode = String.valueOf(payload.get("completionCode"));
            service.completeFromAiEngine(intentId, completionCode);
            return ResponseEntity.ok(Map.of("status", "CONNECTED"));
        } catch (IllegalArgumentException | EntityNotFoundException exception) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).build();
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).build();
        }
    }
}
