package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBindingStatus;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialAssociationRepository;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialBindingRepository;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialRevocationOutboxRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AiCredentialAssociationServiceImplTest {
    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal("principal-1", PrincipalKind.HUMAN, "issuer");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void doesNotPersistAnAssociationWhenAiEngineVerificationFails() {
        var repository = mock(AiCredentialAssociationRepository.class);
        var client = mock(AiEngineCredentialAssociationClient.class);
        var bindings = mock(AiCredentialBindingRepository.class);
        var outbox = mock(AiCredentialRevocationOutboxRepository.class);
        when(repository.findByPrincipalIdAndProviderForUpdate("principal-1", "chatgpt"))
                .thenReturn(Optional.empty());
        when(client.verify(any(AiCredentialAssociation.class)))
                .thenThrow(new CredentialAssociationVerificationException());
        var service = new AiCredentialAssociationServiceImpl(repository, bindings, outbox, client, CLOCK);

        assertThatThrownBy(() -> service.associate(PRINCIPAL, "subject", "opaque-handle", "openai_chatgpt"))
                .isInstanceOf(CredentialAssociationVerificationException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void mapsProductProviderToInternalCredentialProviderBeforeVerification() {
        var repository = mock(AiCredentialAssociationRepository.class);
        var client = mock(AiEngineCredentialAssociationClient.class);
        var bindings = mock(AiCredentialBindingRepository.class);
        var outbox = mock(AiCredentialRevocationOutboxRepository.class);
        when(repository.findByPrincipalIdAndProviderForUpdate("principal-1", "chatgpt"))
                .thenReturn(Optional.empty());
        when(client.verify(any(AiCredentialAssociation.class))).thenReturn(
                new AiEngineCredentialAssociationClient.VerificationResult(
                        com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState.PLAN_USAGE_AUTHORIZED,
                        "resource.invoke chatgpt.tokens.use.direct"));
        when(repository.save(any(AiCredentialAssociation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AiCredentialAssociationServiceImpl(repository, bindings, outbox, client, CLOCK);

        var association = service.associate(PRINCIPAL, "subject", "opaque-handle", "openai_chatgpt");

        org.assertj.core.api.Assertions.assertThat(association.getProvider()).isEqualTo("chatgpt");
        verify(client).verify(any(AiCredentialAssociation.class));
    }

    @Test
    void preservesRevokedAssociationStateForRuntimeProjection() {
        var repository = mock(AiCredentialAssociationRepository.class);
        var client = mock(AiEngineCredentialAssociationClient.class);
        var bindings = mock(AiCredentialBindingRepository.class);
        var outbox = mock(AiCredentialRevocationOutboxRepository.class);
        var association = new AiCredentialAssociation();
        association.setPrincipalId("principal-1");
        association.setProvider("chatgpt");
        association.setCredentialHandle("opaque-handle");
        association.setState(com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState.PLAN_USAGE_AUTHORIZED);
        var binding = new AiCredentialBinding();
        binding.setCredentialHandle("opaque-handle");
        binding.setStatus(AiCredentialBindingStatus.ACTIVE);
        when(bindings.findAllByCredentialHandleAndStatus("opaque-handle", AiCredentialBindingStatus.ACTIVE))
                .thenReturn(List.of(binding));
        when(repository.findByPrincipalIdAndProviderForUpdate("principal-1", "chatgpt"))
                .thenReturn(Optional.of(association));
        when(repository.save(any(AiCredentialAssociation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new AiCredentialAssociationServiceImpl(repository, bindings, outbox, client, CLOCK);

        service.revoke(PRINCIPAL, "openai_chatgpt");

        assertThat(association.getState())
                .isEqualTo(com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState.REVOKED);
        verify(repository, times(1)).save(association);
        verify(outbox).enqueueIfAbsent(any(), eq("opaque-handle"), eq("chatgpt"), any());
        assertThat(binding.getStatus()).isEqualTo(AiCredentialBindingStatus.REVOKED);
        assertThat(binding.getRevocationReason()).isEqualTo("CREDENTIAL_ASSOCIATION_REVOKED");
    }

    @Test
    void doesNotRevokeThePreviousCredentialWhenReplacementPersistenceFails() {
        var repository = mock(AiCredentialAssociationRepository.class);
        var client = mock(AiEngineCredentialAssociationClient.class);
        var bindings = mock(AiCredentialBindingRepository.class);
        var outbox = mock(AiCredentialRevocationOutboxRepository.class);
        var association = new AiCredentialAssociation();
        association.setPrincipalId("principal-1");
        association.setProvider("chatgpt");
        association.setCredentialHandle("old-handle");
        when(repository.findByPrincipalIdAndProviderForUpdate("principal-1", "chatgpt"))
                .thenReturn(Optional.of(association));
        when(client.verify(any(AiCredentialAssociation.class))).thenReturn(
                new AiEngineCredentialAssociationClient.VerificationResult(
                        com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState.PLAN_USAGE_AUTHORIZED,
                        "resource.invoke chatgpt.tokens.use.direct"));
        when(repository.save(any(AiCredentialAssociation.class)))
                .thenThrow(new IllegalStateException("persistence failed"));
        var service = new AiCredentialAssociationServiceImpl(repository, bindings, outbox, client, CLOCK);

        assertThatThrownBy(() -> service.associate(PRINCIPAL, "subject", "new-handle", "openai_chatgpt"))
                .isInstanceOf(IllegalStateException.class);

        verify(client, never()).revoke(any(), any());
    }
}
