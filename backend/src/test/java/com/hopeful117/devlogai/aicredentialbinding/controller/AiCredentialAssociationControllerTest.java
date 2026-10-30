package com.hopeful117.devlogai.aicredentialbinding.controller;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.service.AiCredentialAssociationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCredentialAssociationControllerTest {
    @Test
    void buildsTheDomainPrincipalFromTheValidatedJwt() {
        AiCredentialAssociationService service = mock(AiCredentialAssociationService.class);
        AiCredentialAssociation association = association();
        when(service.associate(argThat(principal -> principal != null
                        && principal.principalId().equals("principal-1")
                        && principal.authenticationSource().equals("https://issuer.example")),
                eq("chatgpt-subject"), eq("credential-handle"), eq("chatgpt")))
                .thenReturn(association);

        var response = new AiCredentialAssociationController(service).associate(
                jwt(), new AiCredentialAssociationController.AssociationRequest(
                        "chatgpt-subject", "credential-handle"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().principalId()).isEqualTo("principal-1");
        verify(service).associate(argThat(principal -> principal.principalId().equals("principal-1")),
                eq("chatgpt-subject"), eq("credential-handle"), eq("chatgpt"));
    }

    @Test
    void rejectsRequestsWithoutAValidatedJwt() {
        AiCredentialAssociationService service = mock(AiCredentialAssociationService.class);

        assertThatThrownBy(() -> new AiCredentialAssociationController(service).associate(null,
                new AiCredentialAssociationController.AssociationRequest("subject", "handle")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Authentication is required");
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://issuer.example")
                .subject("principal-1")
                .build();
    }

    private static AiCredentialAssociation association() {
        AiCredentialAssociation association = new AiCredentialAssociation();
        association.setPrincipalId("principal-1");
        association.setChatgptSubject("chatgpt-subject");
        association.setProvider("chatgpt");
        association.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return association;
    }
}
