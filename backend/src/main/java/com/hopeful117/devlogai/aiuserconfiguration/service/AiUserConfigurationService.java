package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.aiuserconfiguration.entity.AiUserConfiguration;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiModelSelectionOrigin;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiConfigurationTestStatus;
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
        Map<String, Object> parameters = validateParameters(providerEntry, allowedParameters);

        Instant now = clock.instant();
        AiUserConfiguration configuration = repository.findByPrincipalId(principal.principalId())
                .orElseGet(AiUserConfiguration::new);
        configuration.setPrincipalId(principal.principalId());
        configuration.setProvider(provider);
        configuration.setAuthenticationMethod(authenticationMethod);
        configuration.setSelectedModel(selectedModel);
        configuration.setModelSelectionOrigin(modelSelectionOrigin);
        configuration.setAllowedParameters(parameters);
        configuration.setLastConfigurationTestStatus(null);
        configuration.setLastConfigurationTestId(null);
        if (configuration.getCreatedAt() == null) {
            configuration.setCreatedAt(now);
        }
        configuration.setUpdatedAt(now);
        return repository.save(configuration);
    }

    @Transactional
    public void recordConfigurationTest(AuthenticatedPrincipal principal, java.util.UUID testId,
                                        AiConfigurationTestStatus status) {
        requirePrincipal(principal);
        repository.findByPrincipalId(principal.principalId()).ifPresent(configuration -> {
            if (testId != null && !testId.equals(configuration.getLastConfigurationTestId())) {
                return;
            }
            configuration.setLastConfigurationTestStatus(status);
            configuration.setUpdatedAt(clock.instant());
            repository.save(configuration);
        });
    }

    @Transactional
    public java.util.UUID beginConfigurationTest(AuthenticatedPrincipal principal) {
        requirePrincipal(principal);
        return repository.findByPrincipalId(principal.principalId()).map(configuration -> {
            var testId = java.util.UUID.randomUUID();
            configuration.setLastConfigurationTestId(testId);
            configuration.setLastConfigurationTestStatus(null);
            configuration.setUpdatedAt(clock.instant());
            repository.save(configuration);
            return testId;
        }).orElse(null);
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
                || !validPersistedParameters(provider, value.getAllowedParameters())) {
            return AiUserConfigurationState.CONFIGURATION_INCOMPLETE;
        }
        if (value.getModelSelectionOrigin() == AiModelSelectionOrigin.RECOMMENDED
                && !value.getSelectedModel().equals(provider.recommendedModel())) {
            return AiUserConfigurationState.CONFIGURATION_INCOMPLETE;
        }
        if (model.availability() != AiProviderModelAvailability.AVAILABLE) {
            return AiUserConfigurationState.MODEL_UNAVAILABLE;
        }
        if (value.getLastConfigurationTestStatus() != null
                && value.getLastConfigurationTestStatus() != AiConfigurationTestStatus.SUCCESS) {
            return switch (value.getLastConfigurationTestStatus()) {
                case CREDENTIAL_INVALID -> AiUserConfigurationState.CREDENTIAL_INVALID;
                case MODEL_UNAVAILABLE -> AiUserConfigurationState.MODEL_UNAVAILABLE;
                case PROVIDER_UNAVAILABLE, TIMEOUT -> AiUserConfigurationState.PROVIDER_UNAVAILABLE;
                case CONFIGURATION_INVALID -> AiUserConfigurationState.CONFIGURATION_INCOMPLETE;
                case SUCCESS -> AiUserConfigurationState.READY;
            };
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

    private Map<String, Object> validateParameters(AiProviderCatalogEntry provider,
                                                   Map<String, Object> allowedParameters) {
        if (allowedParameters == null || allowedParameters.isEmpty()) return Map.of();
        var supported = provider.parameters().stream().map(parameter -> parameter.name()).toList();
        if (allowedParameters.size() != supported.size() || !supported.containsAll(allowedParameters.keySet())) {
            throw new InvalidParameterException("allowedParameters", allowedParameters.keySet());
        }
        Object raw = allowedParameters.get(AiProviderCatalogService.MAX_OUTPUT_TOKENS);
        if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()
                || number.intValue() < AiProviderCatalogService.MIN_MAX_OUTPUT_TOKENS
                || number.intValue() > AiProviderCatalogService.MAX_MAX_OUTPUT_TOKENS) {
            throw new InvalidParameterException("maxOutputTokens", raw);
        }
        return Map.of(AiProviderCatalogService.MAX_OUTPUT_TOKENS, number.intValue());
    }

    private boolean validPersistedParameters(AiProviderCatalogEntry provider, Map<String, Object> parameters) {
        try {
            validateParameters(provider, parameters);
            return true;
        } catch (InvalidParameterException exception) {
            return false;
        }
    }

    private void requirePrincipal(AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("authenticated principal is required");
        }
    }
}
