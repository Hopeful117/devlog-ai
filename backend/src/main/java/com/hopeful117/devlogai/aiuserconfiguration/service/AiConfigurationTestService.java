package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModel;
import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState;
import com.hopeful117.devlogai.aicredentialbinding.service.AiCredentialAssociationService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiConfigurationTestStatus;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiConfigurationTestService {
    private final AiUserConfigurationService configurationService;
    private final AiProviderCatalogService catalog;
    private final AiCredentialAssociationService associationService;
    private final AiConfigurationTestClient client;

    public Result test(AuthenticatedPrincipal principal) {
        var configuration = configurationService.find(principal);
        if (configuration.isEmpty()) return invalid(null, null, "NOT_CONFIGURED");
        var value = configuration.get();
        var testId = java.util.Optional.ofNullable(configurationService.beginConfigurationTest(principal))
                .orElseGet(java.util.UUID::randomUUID);
        try {
            var provider = catalog.resolve(value.getProvider());
            if (!provider.authenticationMethods().contains(value.getAuthenticationMethod())) {
                return finish(principal, testId, invalid(AiConfigurationTestStatus.CONFIGURATION_INVALID, value.getProvider(),
                        value.getSelectedModel(), "AUTHENTICATION_UNSUPPORTED"));
            }
            AiProviderModel model;
            try {
                model = catalog.resolveModel(value.getProvider(), value.getSelectedModel());
            } catch (EntityNotFoundException exception) {
                return finish(principal, testId, invalid(AiConfigurationTestStatus.MODEL_UNAVAILABLE, value.getProvider(),
                        value.getSelectedModel(), "MODEL_UNAVAILABLE"));
            }
            if (model.availability().name().equals("UNAVAILABLE")) {
                return finish(principal, testId, invalid(AiConfigurationTestStatus.MODEL_UNAVAILABLE, value.getProvider(),
                        value.getSelectedModel(), "MODEL_UNAVAILABLE"));
            }
            if (value.getAuthenticationMethod() == AiAuthenticationMethod.NONE) {
                return finish(principal, testId, invalid(AiConfigurationTestStatus.CONFIGURATION_INVALID, value.getProvider(),
                        value.getSelectedModel(), "AUTHENTICATION_UNSUPPORTED"));
            }
            AiCredentialAssociation association;
            try {
                association = associationService.require(principal, provider.providerId());
            } catch (EntityNotFoundException exception) {
                return finish(principal, testId, invalid(AiConfigurationTestStatus.CREDENTIAL_INVALID, value.getProvider(),
                        value.getSelectedModel(), "CREDENTIAL_INVALID"));
            }
            if (association.getAuthenticationMethod() != value.getAuthenticationMethod()) {
                return finish(principal, testId, invalid(AiConfigurationTestStatus.CREDENTIAL_INVALID, value.getProvider(),
                        value.getSelectedModel(), "CREDENTIAL_METHOD_MISMATCH"));
            }
            if (association.getState() != AiCredentialAssociationState.PLAN_USAGE_AUTHORIZED) {
                return finish(principal, testId, invalid(AiConfigurationTestStatus.CREDENTIAL_INVALID, value.getProvider(),
                        value.getSelectedModel(), "CREDENTIAL_INVALID"));
            }
            var result = client.test(value.getProvider(), value.getAuthenticationMethod(),
                    value.getSelectedModel(), association.getCredentialHandle());
            return finish(principal, testId, new Result(result.status(), value.getProvider(), value.getSelectedModel(),
                    result.diagnosticCode()));
        } catch (RuntimeException exception) {
            return finish(principal, testId, invalid(AiConfigurationTestStatus.CONFIGURATION_INVALID, value.getProvider(),
                    value.getSelectedModel(), "CONFIGURATION_INVALID"));
        }
    }

    private Result finish(AuthenticatedPrincipal principal, java.util.UUID testId, Result result) {
        configurationService.recordConfigurationTest(principal, testId, result.status());
        return result;
    }

    private Result invalid(String provider, String model, String diagnosticCode) {
        return invalid(AiConfigurationTestStatus.CONFIGURATION_INVALID, provider, model, diagnosticCode);
    }

    private Result invalid(AiConfigurationTestStatus status, String provider, String model,
                           String diagnosticCode) {
        return new Result(status, provider, model, diagnosticCode);
    }

    public record Result(AiConfigurationTestStatus status, String provider, String model, String diagnosticCode) { }
}
