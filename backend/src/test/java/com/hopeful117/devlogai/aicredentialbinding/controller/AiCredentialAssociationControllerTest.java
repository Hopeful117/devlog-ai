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
    void revokesTheAssociationForTheValidatedJwt() {
        AiCredentialAssociationService service = mock(AiCredentialAssociationService.class);
        var response = new AiCredentialAssociationController(service).revoke(jwt());

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(service).revoke(argThat(principal -> principal.principalId().equals("principal-1")), eq("chatgpt"));
    }

    @Test
    void rejectsRequestsWithoutAValidatedJwt() {
        AiCredentialAssociationService service = mock(AiCredentialAssociationService.class);

        assertThatThrownBy(() -> new AiCredentialAssociationController(service).revoke(null))
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

}
