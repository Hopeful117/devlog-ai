package com.hopeful117.devlogai.aicredentialbinding.controller;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.service.AiCredentialAssociationService;
import com.hopeful117.devlogai.aicredentialbinding.model.AiProviderIdentity;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import jakarta.validation.Valid;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai-credential-associations")
public class AiCredentialAssociationController {
    private static final String CHATGPT_PROVIDER = AiProviderIdentity.ENGINE_CHATGPT;
    private final AiCredentialAssociationService service;
    private final StoryContextAgentCallbackAuthenticator callbackAuthenticator;
    private final ObjectMapper objectMapper;

    public AiCredentialAssociationController(AiCredentialAssociationService service) {
        this(service, null, null);
    }

    @Autowired
    public AiCredentialAssociationController(AiCredentialAssociationService service,
                                             StoryContextAgentCallbackAuthenticator callbackAuthenticator,
                                             ObjectMapper objectMapper) {
        this.service = service;
        this.callbackAuthenticator = callbackAuthenticator;
        this.objectMapper = objectMapper;
    }

    @DeleteMapping("/chatgpt")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt) {
        var principal = authenticatedPrincipal(jwt);
        service.revoke(principal, CHATGPT_PROVIDER);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revoked")
    public ResponseEntity<Void> revoked(HttpServletRequest request,
                                        @RequestHeader(value = "X-DevLog-Service-Signature", required = false) String signature,
                                        @RequestHeader(value = "X-DevLog-Service-Timestamp", required = false) String timestamp,
                                        @RequestHeader(value = "X-DevLog-Service-Nonce", required = false) String nonce,
                                        @RequestHeader(value = "X-DevLog-Service-Request-Id", required = false) String requestId,
                                        @RequestBody byte[] body) {
        if (callbackAuthenticator == null || !callbackAuthenticator.isServiceValid(
                request.getMethod(), request.getRequestURI(), body, signature,
                timestamp, nonce, requestId)) {
            return ResponseEntity.status(401).build();
        }
        try {
            var payload = objectMapper.readValue(body, Map.class);
            service.revokeByCredentialHandle((String) payload.get("credentialHandle"),
                    (String) payload.get("provider"));
        } catch (Exception exception) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedPrincipal authenticatedPrincipal(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
                || jwt.getIssuer() == null) {
            throw new UnauthenticatedPrincipalException();
        }
        return new AuthenticatedPrincipal(jwt.getSubject(), PrincipalKind.HUMAN, jwt.getIssuer().toString());
    }

    public record AssociationResponse(String provider, String state, String grantedScopes, Instant updatedAt) {
        static AssociationResponse from(AiCredentialAssociation association) {
            return new AssociationResponse(association.getProvider(),
                    association.getState() == null ? "IDENTITY_AUTHORIZED"
                            : association.getState().name(), association.getGrantedScopes() == null ? ""
                            : association.getGrantedScopes(),
                    association.getUpdatedAt());
        }
    }
}
