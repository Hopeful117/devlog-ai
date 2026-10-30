package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.aiuserconfiguration.entity.AiUserConfiguration;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiModelSelectionOrigin;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiUserConfigurationState;
import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.aicatalog.model.AiProviderCatalogEntry;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModel;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModelAvailability;
import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import com.hopeful117.devlogai.aiuserconfiguration.repository.AiUserConfigurationRepository;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.exception.InvalidParameterException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiUserConfigurationService {
    private final AiUserConfigurationRepository repository;
    private final AiProviderCatalogService catalog;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<AiUserConfiguration> find(AuthenticatedPrincipal principal) {
        requirePrincipal(principal);
        return repository.findByPrincipalId(principal.principalId());
    }

    @Transactional
    public AiUserConfiguration save(AuthenticatedPrincipal principal,
                                    String provider,
                                    AiAuthenticationMethod authenticationMethod,
                                    String selectedModel,
                                    AiModelSelectionOrigin modelSelectionOrigin,
                                    Map<String, Object> allowedParameters) {
        requirePrincipal(principal);
        AiProviderCatalogEntry providerEntry = resolveProvider(provider);
        AiProviderModel model = resolveModel(provider, selectedModel);
        validateAuthenticationMethod(providerEntry, authenticationMethod);
        validateSelection(providerEntry, selectedModel, modelSelectionOrigin);
        Map<String, Object> parameters = validateParameters(allowedParameters);

        Instant now = clock.instant();
        AiUserConfiguration configuration = repository.findByPrincipalId(principal.principalId())
                .orElseGet(AiUserConfiguration::new);
        configuration.setPrincipalId(principal.principalId());
        configuration.setProvider(provider);
        configuration.setAuthenticationMethod(authenticationMethod);
        configuration.setSelectedModel(selectedModel);
        configuration.setModelSelectionOrigin(modelSelectionOrigin);
        configuration.setAllowedParameters(parameters);
        if (configuration.getCreatedAt() == null) {
            configuration.setCreatedAt(now);
        }
        configuration.setUpdatedAt(now);
        return repository.save(configuration);
    }

    @Transactional
    public void delete(AuthenticatedPrincipal principal) {
        requirePrincipal(principal);
        repository.findByPrincipalId(principal.principalId()).ifPresent(repository::delete);
    }

    public AiUserConfigurationState state(Optional<AiUserConfiguration> configuration) {
        return state(configuration, Optional.empty());
    }

    public AiUserConfigurationState state(Optional<AiUserConfiguration> configuration,
                                          Optional<AiCredentialAssociation> association) {
        if (configuration.isEmpty()) {
            return AiUserConfigurationState.NOT_CONFIGURED;
        }
        AiUserConfiguration value = configuration.get();
        AiProviderCatalogEntry provider;
        AiProviderModel model;
        try {
            provider = catalog.resolve(value.getProvider());
            model = catalog.resolveModel(value.getProvider(), value.getSelectedModel());
        } catch (RuntimeException exception) {
            return AiUserConfigurationState.CONFIGURATION_INCOMPLETE;
        }
        if (!provider.authenticationMethods().contains(value.getAuthenticationMethod())
                || value.getModelSelectionOrigin() == null
                || value.getAllowedParameters() == null
                || !value.getAllowedParameters().isEmpty()) {
            return AiUserConfigurationState.CONFIGURATION_INCOMPLETE;
        }
        if (value.getModelSelectionOrigin() == AiModelSelectionOrigin.RECOMMENDED
                && !value.getSelectedModel().equals(provider.recommendedModel())) {
            return AiUserConfigurationState.CONFIGURATION_INCOMPLETE;
        }
        if (model.availability() != AiProviderModelAvailability.AVAILABLE) {
            return AiUserConfigurationState.MODEL_UNAVAILABLE;
        }
        if (value.getAuthenticationMethod() == AiAuthenticationMethod.NONE) {
            return AiUserConfigurationState.READY;
        }
        if (association.isEmpty()) {
            return AiUserConfigurationState.REAUTH_REQUIRED;
        }
        return switch (association.get().getState()) {
            case PLAN_USAGE_AUTHORIZED -> AiUserConfigurationState.READY;
            case REAUTH_REQUIRED -> AiUserConfigurationState.REAUTH_REQUIRED;
            case REVOKED -> AiUserConfigurationState.REVOKED;
            case IDENTITY_AUTHORIZED -> AiUserConfigurationState.CONFIGURATION_INCOMPLETE;
        };
    }

    private AiProviderCatalogEntry resolveProvider(String provider) {
        try {
            return catalog.resolve(provider);
        } catch (EntityNotFoundException exception) {
            throw new InvalidParameterException("provider", provider);
        }
    }

    private AiProviderModel resolveModel(String provider, String model) {
        try {
            return catalog.resolveModel(provider, model);
        } catch (EntityNotFoundException exception) {
            throw new InvalidParameterException("selectedModel", model);
        }
    }

    private void validateAuthenticationMethod(AiProviderCatalogEntry provider,
                                              AiAuthenticationMethod authenticationMethod) {
        if (authenticationMethod == null || !provider.authenticationMethods().contains(authenticationMethod)) {
            throw new InvalidParameterException("authenticationMethod", authenticationMethod);
        }
    }

    private void validateSelection(AiProviderCatalogEntry provider,
                                   String selectedModel,
                                   AiModelSelectionOrigin origin) {
        if (origin == null) {
            throw new InvalidParameterException("modelSelectionOrigin", null);
        }
        if (origin == AiModelSelectionOrigin.RECOMMENDED
                && !selectedModel.equals(provider.recommendedModel())) {
            throw new InvalidParameterException("selectedModel", selectedModel);
        }
    }

    private Map<String, Object> validateParameters(Map<String, Object> allowedParameters) {
        if (allowedParameters != null && !allowedParameters.isEmpty()) {
            throw new InvalidParameterException("allowedParameters", allowedParameters.keySet());
        }
        return Map.of();
    }

    private void requirePrincipal(AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("authenticated principal is required");
        }
    }
}
