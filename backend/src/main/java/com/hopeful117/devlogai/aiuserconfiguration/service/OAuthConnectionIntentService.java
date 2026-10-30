package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.ai.engine.config.AIEngineProperties;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.model.AiProviderIdentity;
import com.hopeful117.devlogai.aicredentialbinding.service.AiCredentialAssociationService;
import com.hopeful117.devlogai.aicredentialbinding.service.AiEngineCredentialAssociationClient;
import com.hopeful117.devlogai.aiuserconfiguration.entity.OAuthConnectionIntent;
import com.hopeful117.devlogai.aiuserconfiguration.repository.OAuthConnectionIntentRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthConnectionIntentService {
    private static final String PRODUCT_PROVIDER = AiProviderIdentity.PRODUCT_OPENAI_CHATGPT;
    private final OAuthConnectionIntentRepository repository;
    private final AiCredentialAssociationService associationService;
    private final AiEngineCredentialAssociationClient aiEngineClient;
    private final AIEngineProperties aiEngineProperties;
    private final Clock clock;
    @Value("${devlog.service-auth.secret:}")
    private String serviceSecret;
    @Value("${oauth.connection-intent.ttl:PT5M}")
    private Duration intentTtl;

    @Transactional
    public AuthorizationStart start(AuthenticatedPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("authenticated principal is required");
        if (serviceSecret == null || serviceSecret.isBlank()) {
            throw new IllegalStateException("AI Engine service authentication is not configured");
        }
        if (intentTtl == null || intentTtl.isZero() || intentTtl.isNegative()) {
            throw new IllegalStateException("OAuth connection intent TTL must be positive");
        }
        Instant now = clock.instant();
        OAuthConnectionIntent intent = new OAuthConnectionIntent();
        intent.setPrincipalId(principal.principalId());
        intent.setProvider(PRODUCT_PROVIDER);
        intent.setCreatedAt(now);
        intent.setExpiresAt(now.plus(intentTtl));
        intent = repository.save(intent);
        long expiresAt = intent.getExpiresAt().getEpochSecond();
        String principalBinding = OAuthConnectionIntentSigner.principalBinding(serviceSecret, principal.principalId());
        String signature = OAuthConnectionIntentSigner.sign(serviceSecret, intent.getId(), PRODUCT_PROVIDER,
                expiresAt, principalBinding);
        String url = aiEngineProperties.baseUrl() + "/api/v1/auth/chatgpt/start?intentId="
                + encode(intent.getId().toString()) + "&intentProvider=" + encode(PRODUCT_PROVIDER)
                + "&intentExpiresAt=" + expiresAt + "&intentPrincipalId="
                + encode(principalBinding) + "&intentSignature=" + encode(signature);
        return new AuthorizationStart(url, intent.getId(), intent.getExpiresAt());
    }

    @Transactional
    public AiCredentialAssociation completeIntent(AuthenticatedPrincipal principal, UUID intentId,
                                                  String completionCode) {
        if (principal == null || intentId == null || completionCode == null || completionCode.isBlank()) {
            throw new IllegalArgumentException("OAuth completion data is incomplete");
        }
        OAuthConnectionIntent intent = repository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new EntityNotFoundException("OAuth connection intent"));
        if (!principal.principalId().equals(intent.getPrincipalId())) {
            throw new EntityNotFoundException("OAuth connection intent");
        }
        Instant now = clock.instant();
        if (!PRODUCT_PROVIDER.equals(intent.getProvider())
                || (intent.getConsumedAt() == null && !now.isBefore(intent.getExpiresAt()))) {
            throw new IllegalStateException("OAuth connection intent is not valid");
        }
        if (intent.getConsumedAt() != null) {
            if (!completionCode.equals(intent.getCompletionCode())) {
                throw new IllegalStateException("OAuth connection intent is not valid");
            }
            aiEngineClient.acknowledgeCompletion(intentId.toString(), completionCode);
            return associationService.require(principal, PRODUCT_PROVIDER);
        }
        var completion = aiEngineClient.redeemCompletion(intentId.toString(), completionCode);
        try {
            AiCredentialAssociation association = associationService.associate(principal,
                    completion.subject(), completion.credentialHandle(), PRODUCT_PROVIDER);
            intent.setCompletionCode(completionCode);
            intent.setConsumedAt(now);
            repository.save(intent);
            acknowledgeAfterCommit(intentId, completionCode);
            return association;
        } catch (RuntimeException exception) {
            try {
                aiEngineClient.compensateCompletion(intentId.toString(), completionCode);
            } catch (RuntimeException compensationFailure) {
                exception.addSuppressed(compensationFailure);
            }
            throw exception;
        }
    }

    private void acknowledgeAfterCommit(UUID intentId, String completionCode) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            aiEngineClient.acknowledgeCompletion(intentId.toString(), completionCode);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aiEngineClient.acknowledgeCompletion(intentId.toString(), completionCode);
            }
        });
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record AuthorizationStart(String authorizationUrl, UUID intentId, Instant expiresAt) { }
}
