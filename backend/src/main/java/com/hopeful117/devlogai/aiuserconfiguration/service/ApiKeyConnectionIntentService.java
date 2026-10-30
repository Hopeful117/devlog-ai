package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.model.AiProviderIdentity;
import com.hopeful117.devlogai.aicredentialbinding.service.AiCredentialAssociationService;
import com.hopeful117.devlogai.aicredentialbinding.service.AiEngineCredentialAssociationClient;
import com.hopeful117.devlogai.ai.engine.config.AIEngineProperties;
import com.hopeful117.devlogai.aiuserconfiguration.entity.OAuthConnectionIntent;
import com.hopeful117.devlogai.aiuserconfiguration.repository.OAuthConnectionIntentRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyConnectionIntentService {
    private static final String PROVIDER = "anthropic";
    private final OAuthConnectionIntentRepository repository;
    private final AiCredentialAssociationService associationService;
    private final AiEngineCredentialAssociationClient aiEngineClient;
    private final AIEngineProperties aiEngineProperties;
    private final Clock clock;
    @Value("${devlog.service-auth.secret:}") private String serviceSecret;
    @Value("${api-key.connection-intent.ttl:PT5M}") private Duration intentTtl;

    @Transactional
    public EnrollmentStart start(AuthenticatedPrincipal principal) {
        if (principal == null || serviceSecret == null || serviceSecret.isBlank())
            throw new IllegalStateException("API-key enrollment is not configured");
        Instant now = clock.instant();
        OAuthConnectionIntent intent = new OAuthConnectionIntent();
        intent.setPrincipalId(principal.principalId());
        intent.setProvider(PROVIDER);
        intent.setAuthenticationMethod(AiAuthenticationMethod.API_KEY);
        intent.setCreatedAt(now);
        intent.setExpiresAt(now.plus(intentTtl));
        intent = repository.save(intent);
        long issued = intent.getCreatedAt().getEpochSecond();
        long expires = intent.getExpiresAt().getEpochSecond();
        String binding = OAuthConnectionIntentSigner.principalBinding(serviceSecret, principal.principalId());
        String signature = OAuthConnectionIntentSigner.sign(serviceSecret, intent.getId(), PROVIDER,
                issued, expires, binding, AiAuthenticationMethod.API_KEY.name());
        return new EnrollmentStart(aiEngineProperties.baseUrl() + "/api/v1/auth/api-key/enroll",
                intent.getId(), PROVIDER, AiAuthenticationMethod.API_KEY.name(), issued, expires, binding, signature,
                intent.getExpiresAt());
    }

    @Transactional
    public AiCredentialAssociation complete(AuthenticatedPrincipal principal, UUID intentId,
                                             String credentialHandle) {
        if (principal == null || intentId == null || credentialHandle == null || credentialHandle.isBlank())
            throw new IllegalArgumentException("API-key enrollment data is incomplete");
        OAuthConnectionIntent intent = repository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new EntityNotFoundException("API-key enrollment intent"));
        if (!principal.principalId().equals(intent.getPrincipalId())
                || !PROVIDER.equals(intent.getProvider())
                || intent.getAuthenticationMethod() != AiAuthenticationMethod.API_KEY)
            throw new EntityNotFoundException("API-key enrollment intent");
        if (intent.getConsumedAt() != null) {
            if (!credentialHandle.equals(intent.getCompletionCode())) throw new IllegalStateException("enrollment intent is not valid");
            return associationService.require(principal, PROVIDER);
        }
        if (!clock.instant().isBefore(intent.getExpiresAt())) throw new IllegalStateException("enrollment intent is not valid");
        boolean verifiedForIntent = false;
        try {
            String principalBinding = OAuthConnectionIntentSigner.principalBinding(serviceSecret, principal.principalId());
            aiEngineClient.verifyApiKeyEnrollment(intentId, credentialHandle, principalBinding);
            verifiedForIntent = true;
            AiCredentialAssociation association = associationService.associate(principal, null, credentialHandle,
                    PROVIDER, AiAuthenticationMethod.API_KEY);
            intent.setCompletionCode(credentialHandle);
            intent.setConsumedAt(clock.instant());
            repository.save(intent);
            return association;
        } catch (RuntimeException exception) {
            if (verifiedForIntent) {
                try {
                    aiEngineClient.compensateApiKey(credentialHandle);
                } catch (RuntimeException compensationFailure) {
                    associationService.scheduleRevocation(credentialHandle, PROVIDER);
                }
            }
            throw exception;
        }
    }

    public record EnrollmentStart(String intakeEndpoint, UUID intentId, String provider,
                                  String authenticationMethod, long issuedAt, long expiresAt, String principalBinding,
                                  String signature, Instant expiresAtInstant) { }
}
