package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.aicatalog.model.AiCatalogLifecycleStatus;
import com.hopeful117.devlogai.aicatalog.model.AiProviderCatalogEntry;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModel;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModelAvailability;
import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState;
import com.hopeful117.devlogai.aicredentialbinding.service.AiCredentialAssociationService;
import com.hopeful117.devlogai.aiuserconfiguration.entity.AiUserConfiguration;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiConfigurationTestStatus;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class AiConfigurationTestServiceTest {
    private final AiUserConfigurationService configurationService = mock(AiUserConfigurationService.class);
    private final AiProviderCatalogService catalog = mock(AiProviderCatalogService.class);
    private final AiCredentialAssociationService associationService = mock(AiCredentialAssociationService.class);
    private final AiConfigurationTestClient client = mock(AiConfigurationTestClient.class);
    private final AiConfigurationTestService service =
            new AiConfigurationTestService(configurationService, catalog, associationService, client);
    private final AuthenticatedPrincipal principal =
            new AuthenticatedPrincipal("principal-1", PrincipalKind.HUMAN, "issuer");

    @Test
    void mapsMissingAssociationToCredentialInvalid() {
        var configuration = configuration("anthropic", "claude-opus-5-5", AiAuthenticationMethod.API_KEY);
        var provider = provider("anthropic", AiAuthenticationMethod.API_KEY);
        when(configurationService.find(principal)).thenReturn(Optional.of(configuration));
        when(catalog.resolve("anthropic")).thenReturn(provider);
        when(catalog.resolveModel("anthropic", "claude-opus-5-5")).thenReturn(model());
        when(associationService.require(principal, "anthropic"))
                .thenThrow(new EntityNotFoundException("AI credential association", "principal-1"));

        var result = service.test(principal);

        assertThat(result.status()).isEqualTo(AiConfigurationTestStatus.CREDENTIAL_INVALID);
        assertThat(result.diagnosticCode()).isEqualTo("CREDENTIAL_INVALID");
        verifyNoInteractions(client);
    }

    @Test
    void mapsUnknownModelToModelUnavailable() {
        var configuration = configuration("anthropic", "missing-model", AiAuthenticationMethod.API_KEY);
        when(configurationService.find(principal)).thenReturn(Optional.of(configuration));
        when(catalog.resolve("anthropic")).thenReturn(provider("anthropic", AiAuthenticationMethod.API_KEY));
        when(catalog.resolveModel("anthropic", "missing-model"))
                .thenThrow(new EntityNotFoundException("AI model", "anthropic:missing-model"));

        var result = service.test(principal);

        assertThat(result.status()).isEqualTo(AiConfigurationTestStatus.MODEL_UNAVAILABLE);
        assertThat(result.diagnosticCode()).isEqualTo("MODEL_UNAVAILABLE");
        verifyNoInteractions(associationService, client);
    }

    @Test
    void rejectsAssociationWithDifferentAuthenticationMethodBeforeCallingEngine() {
        var configuration = configuration("anthropic", "claude-opus-5-5", AiAuthenticationMethod.API_KEY);
        var provider = provider("anthropic", AiAuthenticationMethod.API_KEY);
        var association = new AiCredentialAssociation();
        association.setAuthenticationMethod(AiAuthenticationMethod.OAUTH);
        association.setState(AiCredentialAssociationState.PLAN_USAGE_AUTHORIZED);
        association.setCredentialHandle("opaque-handle");
        when(configurationService.find(principal)).thenReturn(Optional.of(configuration));
        when(catalog.resolve("anthropic")).thenReturn(provider);
        when(catalog.resolveModel("anthropic", "claude-opus-5-5")).thenReturn(model());
        when(associationService.require(principal, "anthropic")).thenReturn(association);

        var result = service.test(principal);

        assertThat(result.status()).isEqualTo(AiConfigurationTestStatus.CREDENTIAL_INVALID);
        assertThat(result.diagnosticCode()).isEqualTo("CREDENTIAL_METHOD_MISMATCH");
        verifyNoInteractions(client);
    }

    @Test
    void mapsSuccessfulEngineResultAndPersistsOnlyTheBoundedStatus() {
        var configuration = configuration("anthropic", "claude-opus-5-5", AiAuthenticationMethod.API_KEY);
        var testId = java.util.UUID.randomUUID();
        var association = new AiCredentialAssociation();
        association.setAuthenticationMethod(AiAuthenticationMethod.API_KEY);
        association.setState(AiCredentialAssociationState.PLAN_USAGE_AUTHORIZED);
        association.setCredentialHandle("opaque-handle");
        when(configurationService.find(principal)).thenReturn(Optional.of(configuration));
        when(configurationService.beginConfigurationTest(principal)).thenReturn(testId);
        when(catalog.resolve("anthropic")).thenReturn(provider("anthropic", AiAuthenticationMethod.API_KEY));
        when(catalog.resolveModel("anthropic", "claude-opus-5-5")).thenReturn(model());
        when(associationService.require(principal, "anthropic")).thenReturn(association);
        when(client.test("anthropic", AiAuthenticationMethod.API_KEY, "claude-opus-5-5", "opaque-handle"))
                .thenReturn(new AiConfigurationTestClient.Result(
                        AiConfigurationTestStatus.SUCCESS, "CONFIGURATION_VALID"));

        var result = service.test(principal);

        assertThat(result.status()).isEqualTo(AiConfigurationTestStatus.SUCCESS);
        verify(configurationService).recordConfigurationTest(any(), any(java.util.UUID.class),
                eq(AiConfigurationTestStatus.SUCCESS));
    }

    private static AiUserConfiguration configuration(String provider, String model,
                                                      AiAuthenticationMethod authenticationMethod) {
        var configuration = new AiUserConfiguration();
        configuration.setProvider(provider);
        configuration.setSelectedModel(model);
        configuration.setAuthenticationMethod(authenticationMethod);
        return configuration;
    }

    private static AiProviderCatalogEntry provider(String id, AiAuthenticationMethod authenticationMethod) {
        return new AiProviderCatalogEntry(id, id, List.of(authenticationMethod),
                List.of(), null, List.of(), AiCatalogLifecycleStatus.STABLE);
    }

    private static AiProviderModel model() {
        return new AiProviderModel("claude-opus-5-5", "Claude Opus 5.5", List.of(),
                AiProviderModelAvailability.AVAILABLE);
    }
}
