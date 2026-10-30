package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.ai.engine.config.AIEngineProperties;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthConnectionIntentServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal("principal-1", PrincipalKind.HUMAN, "issuer");

    @Test
    void createsOpaquePrincipalBoundIntentUrl() {
        var repository = mock(OAuthConnectionIntentRepository.class);
        var service = service(repository, mock(AiCredentialAssociationService.class));
        when(repository.save(any(OAuthConnectionIntent.class))).thenAnswer(invocation -> {
            OAuthConnectionIntent intent = invocation.getArgument(0);
            intent.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return intent;
        });

        var result = service.start(PRINCIPAL);

        assertThat(result.authorizationUrl()).contains("intentId=11111111-1111-1111-1111-111111111111")
                .contains("intentProvider=openai_chatgpt")
                .contains("intentSignature=")
                .doesNotContain("token", "secret", "principal-1");
        String signature = result.authorizationUrl().substring(
                result.authorizationUrl().indexOf("intentSignature=") + "intentSignature=".length());
        String principalBinding = OAuthConnectionIntentSigner.principalBinding("service-secret", "principal-1");
        assertThat(OAuthConnectionIntentSigner.isValid("service-secret",
                result.intentId(), "openai_chatgpt", result.expiresAt().getEpochSecond(),
                principalBinding, signature)).isTrue();
        verify(repository).save(any(OAuthConnectionIntent.class));
    }

    @Test
    void completesOnlyForTheAuthenticatedPrincipal() {
        var repository = mock(OAuthConnectionIntentRepository.class);
        var associations = mock(AiCredentialAssociationService.class);
        var aiEngine = mock(AiEngineCredentialAssociationClient.class);
        var service = service(repository, associations, aiEngine);
        var intent = intent(NOW.plus(Duration.ofMinutes(5)));
        when(repository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(associations.associate(any(), any(), any(), any())).thenReturn(new AiCredentialAssociation());
        when(aiEngine.redeemCompletion(any(), any())).thenReturn(
                new AiEngineCredentialAssociationClient.OAuthCompletion("opaque", "subject", "scopes", "client"));

        service.completeIntent(PRINCIPAL, intent.getId(), "completion-code");

        assertThat(intent.getConsumedAt()).isEqualTo(NOW);
        verify(repository).save(intent);
        verify(aiEngine).acknowledgeCompletion(intent.getId().toString(), "completion-code");
    }

    @Test
    void rejectsReplayExpiryAndProviderMismatchBeforeAssociation() {
        var repository = mock(OAuthConnectionIntentRepository.class);
        var associations = mock(AiCredentialAssociationService.class);
        var aiEngine = mock(AiEngineCredentialAssociationClient.class);
        var service = service(repository, associations, aiEngine);
        var consumed = intent(NOW.plus(Duration.ofMinutes(5)));
        consumed.setConsumedAt(NOW.minusSeconds(1));
        consumed.setCompletionCode("code");
        when(repository.findByIdForUpdate(consumed.getId())).thenReturn(Optional.of(consumed));
        when(associations.require(PRINCIPAL, "openai_chatgpt")).thenReturn(new AiCredentialAssociation());
        assertThat(service.completeIntent(PRINCIPAL, consumed.getId(), "code")).isNotNull();

        var expired = intent(NOW.minusSeconds(1));
        when(repository.findByIdForUpdate(expired.getId())).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.completeIntent(PRINCIPAL, expired.getId(), "code"))
                .isInstanceOf(IllegalStateException.class);

        var mismatch = intent(NOW.plus(Duration.ofMinutes(5)));
        mismatch.setProvider("openai");
        when(repository.findByIdForUpdate(mismatch.getId())).thenReturn(Optional.of(mismatch));
        assertThatThrownBy(() -> service.completeIntent(PRINCIPAL, mismatch.getId(), "code"))
                .isInstanceOf(IllegalStateException.class);
        verify(associations, never()).associate(any(), any(), any(), any());
    }

    @Test
    void compensatesTheAiCredentialWhenCoreAssociationFails() {
        var repository = mock(OAuthConnectionIntentRepository.class);
        var associations = mock(AiCredentialAssociationService.class);
        var aiEngine = mock(AiEngineCredentialAssociationClient.class);
        var service = service(repository, associations, aiEngine);
        var intent = intent(NOW.plus(Duration.ofMinutes(5)));
        when(repository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(aiEngine.redeemCompletion(any(), any())).thenReturn(
                new AiEngineCredentialAssociationClient.OAuthCompletion("opaque", "subject", "scopes", "client"));
        when(associations.associate(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("association failed"));

        assertThatThrownBy(() -> service.completeIntent(PRINCIPAL, intent.getId(), "completion-code"))
                .isInstanceOf(IllegalStateException.class);

        verify(aiEngine).compensateCompletion(intent.getId().toString(), "completion-code");
        verify(repository, never()).save(any());
    }

    private static OAuthConnectionIntentService service(OAuthConnectionIntentRepository repository,
                                                        AiCredentialAssociationService associations) {
        return service(repository, associations, mock(AiEngineCredentialAssociationClient.class));
    }

    private static OAuthConnectionIntentService service(OAuthConnectionIntentRepository repository,
                                                        AiCredentialAssociationService associations,
                                                        AiEngineCredentialAssociationClient aiEngine) {
        var service = new OAuthConnectionIntentService(repository, associations, aiEngine,
                new AIEngineProperties("http://ai", Duration.ofSeconds(1), Duration.ofSeconds(1)), CLOCK);
        ReflectionTestUtils.setField(service, "serviceSecret", "service-secret");
        ReflectionTestUtils.setField(service, "intentTtl", Duration.ofMinutes(5));
        return service;
    }

    private static OAuthConnectionIntent intent(Instant expiresAt) {
        var intent = new OAuthConnectionIntent();
        intent.setId(UUID.randomUUID());
        intent.setPrincipalId("principal-1");
        intent.setProvider("openai_chatgpt");
        intent.setCreatedAt(NOW);
        intent.setExpiresAt(expiresAt);
        return intent;
    }
}
