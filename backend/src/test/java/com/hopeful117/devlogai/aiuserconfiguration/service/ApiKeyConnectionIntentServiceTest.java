package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.aicredentialbinding.service.AiCredentialAssociationService;
import com.hopeful117.devlogai.aicredentialbinding.service.AiEngineCredentialAssociationClient;
import com.hopeful117.devlogai.aiuserconfiguration.entity.OAuthConnectionIntent;
import com.hopeful117.devlogai.aiuserconfiguration.repository.OAuthConnectionIntentRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyConnectionIntentServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void usesOnlyTheConfiguredPublicIntakeUrl() {
        var repository = mock(OAuthConnectionIntentRepository.class);
        var service = service(repository);
        when(repository.save(any(OAuthConnectionIntent.class))).thenAnswer(invocation -> {
            OAuthConnectionIntent intent = invocation.getArgument(0);
            intent.setId(UUID.randomUUID());
            return intent;
        });

        var result = service.start(new AuthenticatedPrincipal("principal", PrincipalKind.HUMAN, "issuer"));

        assertThat(result.intakeEndpoint()).startsWith("https://public-ai.example/api/v1/auth/api-key/enroll");
    }

    @Test
    void failsClosedWhenThePublicIntakeUrlIsMissing() {
        var service = service(mock(OAuthConnectionIntentRepository.class));
        ReflectionTestUtils.setField(service, "publicBaseUrl", "");

        assertThatThrownBy(() -> service.start(new AuthenticatedPrincipal("principal", PrincipalKind.HUMAN, "issuer")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public base URL");
    }

    @Test
    void rejectsInsecureNonLocalPublicIntakeUrl() {
        var service = service(mock(OAuthConnectionIntentRepository.class));
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://public-ai.example");

        assertThatThrownBy(() -> service.start(new AuthenticatedPrincipal("principal", PrincipalKind.HUMAN, "issuer")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    private static ApiKeyConnectionIntentService service(OAuthConnectionIntentRepository repository) {
        var service = new ApiKeyConnectionIntentService(repository,
                mock(AiCredentialAssociationService.class),
                mock(AiEngineCredentialAssociationClient.class), CLOCK);
        ReflectionTestUtils.setField(service, "serviceSecret", "service-secret");
        ReflectionTestUtils.setField(service, "intentTtl", Duration.ofMinutes(5));
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://public-ai.example");
        return service;
    }
}
