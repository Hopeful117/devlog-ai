package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialAssociationRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AiCredentialAssociationServiceImpl implements AiCredentialAssociationService {
    private final AiCredentialAssociationRepository repository;
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
        Instant now = clock.instant();
        AiCredentialAssociation association = repository
                .findByPrincipalIdAndProvider(principal.principalId(), provider)
                .orElseGet(AiCredentialAssociation::new);
        association.setPrincipalId(principal.principalId());
        association.setChatgptSubject(chatgptSubject);
        association.setCredentialHandle(credentialHandle);
        association.setProvider(provider);
        if (association.getCreatedAt() == null) association.setCreatedAt(now);
        association.setUpdatedAt(now);
        AiCredentialAssociation saved = repository.save(association);
        aiEngineClient.verify(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public AiCredentialAssociation require(AuthenticatedPrincipal principal, String provider) {
        requirePrincipal(principal);
        return repository.findByPrincipalIdAndProvider(principal.principalId(), provider)
                .orElseThrow(() -> new EntityNotFoundException("AI credential association"));
    }

    @Override
    @Transactional
    public void revoke(AuthenticatedPrincipal principal, String provider) {
        AiCredentialAssociation association = require(principal, provider);
        repository.delete(association);
    }

    private void requirePrincipal(AuthenticatedPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("authenticated principal is required");
    }

    private void requireValue(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
