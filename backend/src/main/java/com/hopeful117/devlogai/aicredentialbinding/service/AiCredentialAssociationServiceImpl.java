package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialAssociationRepository;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialBindingRepository;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialRevocationOutboxRepository;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBindingStatus;
import com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState;
import com.hopeful117.devlogai.aicredentialbinding.model.AiProviderIdentity;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiCredentialAssociationServiceImpl implements AiCredentialAssociationService {
    private final AiCredentialAssociationRepository repository;
    private final AiCredentialBindingRepository bindingRepository;
    private final AiCredentialRevocationOutboxRepository outboxRepository;
    private final AiEngineCredentialAssociationClient aiEngineClient;
    private final Clock clock;

    @Override
    @Transactional
    public AiCredentialAssociation associate(AuthenticatedPrincipal principal, String chatgptSubject,
                                             String credentialHandle, String provider) {
        requirePrincipal(principal);
        requireValue(chatgptSubject, "chatgptSubject");
        requireValue(credentialHandle, "credentialHandle");
        requireValue(provider, "provider");
        String engineProvider = AiProviderIdentity.engineProvider(provider);
        Instant now = clock.instant();
        AiCredentialAssociation association = repository
                .findByPrincipalIdAndProviderForUpdate(principal.principalId(), engineProvider)
                .orElseGet(AiCredentialAssociation::new);
        String previousHandle = association.getCredentialHandle();
        association.setPrincipalId(principal.principalId());
        association.setChatgptSubject(chatgptSubject);
        association.setCredentialHandle(credentialHandle);
        association.setProvider(engineProvider);
        if (association.getCreatedAt() == null) association.setCreatedAt(now);
        association.setUpdatedAt(now);
        // Verify the candidate before making it visible to Core. A failed exchange
        // must not create or replace an apparently usable association.
        var verification = aiEngineClient.verify(association);
        association.setState(verification.state());
        association.setGrantedScopes(verification.grantedScopes());
        AiCredentialAssociation saved = repository.save(association);
        if (previousHandle != null && !previousHandle.equals(credentialHandle)) {
            revokeCoreBindings(previousHandle);
            enqueueRevocation(previousHandle, engineProvider);
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public AiCredentialAssociation require(AuthenticatedPrincipal principal, String provider) {
        requirePrincipal(principal);
        return repository.findByPrincipalIdAndProvider(principal.principalId(), AiProviderIdentity.engineProvider(provider))
                .orElseThrow(() -> new EntityNotFoundException("AI credential association"));
    }

    @Override
    @Transactional
    public void revoke(AuthenticatedPrincipal principal, String provider) {
        AiCredentialAssociation association = repository
                .findByPrincipalIdAndProviderForUpdate(principal.principalId(), AiProviderIdentity.engineProvider(provider))
                .orElseThrow(() -> new EntityNotFoundException("AI credential association"));
        revokeCoreBindings(association.getCredentialHandle());
        boolean newlyRevoked = association.getState() != AiCredentialAssociationState.REVOKED;
        association.setState(AiCredentialAssociationState.REVOKED);
        association.setUpdatedAt(clock.instant());
        repository.save(association);
        if (newlyRevoked) {
            enqueueRevocation(association.getCredentialHandle(), association.getProvider());
        }
    }

    @Override
    @Transactional
    public void revokeByCredentialHandle(String credentialHandle, String provider) {
        requireValue(credentialHandle, "credentialHandle");
        requireValue(provider, "provider");
        repository.findByCredentialHandleAndProviderForUpdate(credentialHandle, AiProviderIdentity.engineProvider(provider))
                .ifPresent(association -> {
                    revokeCoreBindings(credentialHandle);
                    association.setState(AiCredentialAssociationState.REVOKED);
                    association.setUpdatedAt(clock.instant());
                    repository.save(association);
                });
    }

    private void requirePrincipal(AuthenticatedPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("authenticated principal is required");
    }

    private void revokeCoreBindings(String credentialHandle) {
        var bindings = bindingRepository.findAllByCredentialHandleAndStatus(
                credentialHandle, AiCredentialBindingStatus.ACTIVE);
        bindings.forEach(binding -> {
                    binding.setStatus(AiCredentialBindingStatus.REVOKED);
                    binding.setRevokedAt(clock.instant());
                    binding.setRevocationReason("CREDENTIAL_ASSOCIATION_REVOKED");
                });
        if (!bindings.isEmpty()) {
            bindingRepository.saveAll(bindings);
        }
    }

    private void enqueueRevocation(String credentialHandle, String provider) {
        outboxRepository.enqueueIfAbsent(UUID.randomUUID(), credentialHandle, provider, clock.instant());
    }

    private void requireValue(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
