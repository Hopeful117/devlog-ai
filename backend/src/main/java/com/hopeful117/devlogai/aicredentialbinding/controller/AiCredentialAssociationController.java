package com.hopeful117.devlogai.aicredentialbinding.controller;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.service.AiCredentialAssociationService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/ai-credential-associations")
public class AiCredentialAssociationController {
    private static final String CHATGPT_PROVIDER = "chatgpt";
    private final AiCredentialAssociationService service;

    public AiCredentialAssociationController(AiCredentialAssociationService service) {
        this.service = service;
    }

    @PostMapping("/chatgpt")
    public ResponseEntity<AssociationResponse> associate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AssociationRequest request) {
        var principal = authenticatedPrincipal(jwt);
        var association = service.associate(principal, request.chatgptSubject(),
                request.credentialHandle(), CHATGPT_PROVIDER);
        return ResponseEntity.ok(AssociationResponse.from(association));
    }

    @DeleteMapping("/chatgpt")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt) {
        var principal = authenticatedPrincipal(jwt);
        service.revoke(principal, CHATGPT_PROVIDER);
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedPrincipal authenticatedPrincipal(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
                || jwt.getIssuer() == null) {
            throw new UnauthenticatedPrincipalException();
        }
        return new AuthenticatedPrincipal(jwt.getSubject(), PrincipalKind.HUMAN, jwt.getIssuer().toString());
    }

    public record AssociationRequest(@NotBlank String chatgptSubject, @NotBlank String credentialHandle) { }

    public record AssociationResponse(String principalId, String chatgptSubject, String provider, Instant updatedAt) {
        static AssociationResponse from(AiCredentialAssociation association) {
            return new AssociationResponse(association.getPrincipalId(), association.getChatgptSubject(),
                    association.getProvider(), association.getUpdatedAt());
        }
    }
}
