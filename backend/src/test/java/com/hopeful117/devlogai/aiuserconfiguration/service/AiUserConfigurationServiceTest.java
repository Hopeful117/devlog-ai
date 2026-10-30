package com.hopeful117.devlogai.aiuserconfiguration.service;

import com.hopeful117.devlogai.aiuserconfiguration.entity.AiUserConfiguration;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiModelSelectionOrigin;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiUserConfigurationState;
import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.aicatalog.model.AiCatalogLifecycleStatus;
import com.hopeful117.devlogai.aicatalog.model.AiProviderCatalogEntry;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModel;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModelAvailability;
import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import com.hopeful117.devlogai.aiuserconfiguration.repository.AiUserConfigurationRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.shared.exception.InvalidParameterException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class AiUserConfigurationServiceTest {
    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal("principal-1", PrincipalKind.HUMAN, "issuer");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void distinguishesMissingConfigurationFromAuthenticatedAccess() {
        var repository = mock(AiUserConfigurationRepository.class);
        when(repository.findByPrincipalId("principal-1")).thenReturn(Optional.empty());
        var service = service(repository, catalog());

        var configuration = service.find(PRINCIPAL);

        assertThat(configuration).isEmpty();
        assertThat(service.state(configuration)).isEqualTo(AiUserConfigurationState.NOT_CONFIGURED);
        verify(repository).findByPrincipalId("principal-1");
    }

    @Test
    void persistsConfigurationOwnedByAuthenticatedPrincipalWithoutSecrets() {
        var repository = mock(AiUserConfigurationRepository.class);
        when(repository.findByPrincipalId("principal-1")).thenReturn(Optional.empty());
        when(repository.save(any(AiUserConfiguration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = service(repository, catalog());

        var saved = service.save(PRINCIPAL, "mock", AiAuthenticationMethod.NONE,
                "deterministic-v1", AiModelSelectionOrigin.RECOMMENDED, Map.of());

        assertThat(saved.getPrincipalId()).isEqualTo("principal-1");
        assertThat(saved.getProvider()).isEqualTo("mock");
        assertThat(saved.getAllowedParameters()).isEmpty();
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(service.state(Optional.of(saved))).isEqualTo(AiUserConfigurationState.READY);
    }

    @Test
    void scopesReadsAndDeletesToTheAuthenticatedPrincipal() {
        var repository = mock(AiUserConfigurationRepository.class);
        when(repository.findByPrincipalId("principal-1")).thenReturn(Optional.empty());
        var service = service(repository, catalog());
        var otherPrincipal = new AuthenticatedPrincipal("principal-2", PrincipalKind.HUMAN, "issuer");

        service.find(otherPrincipal);
        service.delete(otherPrincipal);

        verify(repository, times(2)).findByPrincipalId("principal-2");
        verify(repository, never()).delete(any());
    }

    @Test
    void deletesAnExistingConfigurationForTheAuthenticatedPrincipal() {
        var repository = mock(AiUserConfigurationRepository.class);
        var existing = new AiUserConfiguration();
        existing.setPrincipalId("principal-1");
        when(repository.findByPrincipalId("principal-1")).thenReturn(Optional.of(existing));
        var service = service(repository, catalog());

        service.delete(PRINCIPAL);

        verify(repository).delete(existing);
    }

    @Test
    void rejectsNonWhitelistedParametersAndInvalidRecommendedSelection() {
        var repository = mock(AiUserConfigurationRepository.class);
        var catalog = catalog();
        var alternative = new AiProviderModel("alternative", "Alternative", List.of(),
                AiProviderModelAvailability.AVAILABLE);
        when(catalog.resolveModel("mock", "alternative")).thenReturn(alternative);
        var service = service(repository, catalog);

        assertThatThrownBy(() -> service.save(PRINCIPAL, "mock", AiAuthenticationMethod.NONE,
                "deterministic-v1", AiModelSelectionOrigin.RECOMMENDED,
                Map.of("temperature", 0.2)))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.save(PRINCIPAL, "mock", AiAuthenticationMethod.NONE,
                "alternative", AiModelSelectionOrigin.RECOMMENDED, Map.of()))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void preservesExplicitUnavailableModelAndRemovesReadyState() {
        var repository = mock(AiUserConfigurationRepository.class);
        var unavailableModel = new AiProviderModel("known-model", "Known model", List.of(),
                AiProviderModelAvailability.UNAVAILABLE);
        var unavailableCatalog = new AiProviderCatalogEntry(
                "mock", "Mock", List.of(AiAuthenticationMethod.NONE), List.of(unavailableModel),
                null, List.of(), AiCatalogLifecycleStatus.EXPERIMENTAL);
        var catalog = mock(AiProviderCatalogService.class);
        when(catalog.resolve("mock")).thenReturn(unavailableCatalog);
        when(catalog.resolveModel("mock", "known-model")).thenReturn(unavailableModel);
        when(repository.findByPrincipalId("principal-1")).thenReturn(Optional.empty());
        when(repository.save(any(AiUserConfiguration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = service(repository, catalog);

        var saved = service.save(PRINCIPAL, "mock", AiAuthenticationMethod.NONE,
                "known-model", AiModelSelectionOrigin.EXPLICIT, Map.of());

        assertThat(saved.getSelectedModel()).isEqualTo("known-model");
        assertThat(saved.getModelSelectionOrigin()).isEqualTo(AiModelSelectionOrigin.EXPLICIT);
        assertThat(service.state(Optional.of(saved))).isEqualTo(AiUserConfigurationState.MODEL_UNAVAILABLE);
    }

    @Test
    void keepsExplicitSelectionWhenCatalogRecommendationChanges() {
        var repository = mock(AiUserConfigurationRepository.class);
        var catalog = catalog();
        var alternative = new AiProviderModel("alternative", "Alternative", List.of(),
                AiProviderModelAvailability.AVAILABLE);
        when(catalog.resolveModel("mock", "alternative")).thenReturn(alternative);
        when(repository.findByPrincipalId("principal-1")).thenReturn(Optional.empty());
        when(repository.save(any(AiUserConfiguration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = service(repository, catalog);

        var saved = service.save(PRINCIPAL, "mock", AiAuthenticationMethod.NONE,
                "alternative", AiModelSelectionOrigin.EXPLICIT, Map.of());

        assertThat(saved.getSelectedModel()).isEqualTo("alternative");
        assertThat(saved.getModelSelectionOrigin()).isEqualTo(AiModelSelectionOrigin.EXPLICIT);
        verify(repository).save(any(AiUserConfiguration.class));
    }

    private static AiUserConfigurationService service(AiUserConfigurationRepository repository,
                                                      AiProviderCatalogService catalog) {
        return new AiUserConfigurationService(repository, catalog, CLOCK);
    }

    private static AiProviderCatalogService catalog() {
        var catalog = mock(AiProviderCatalogService.class);
        var model = new AiProviderModel("deterministic-v1", "Deterministic v1", List.of("STRUCTURED_OUTPUT"),
                AiProviderModelAvailability.AVAILABLE);
        var provider = new AiProviderCatalogEntry("mock", "Mock", List.of(AiAuthenticationMethod.NONE),
                List.of(model), "deterministic-v1", List.of("STRUCTURED_OUTPUT"), AiCatalogLifecycleStatus.STABLE);
        when(catalog.resolve("mock")).thenReturn(provider);
        when(catalog.resolveModel(eq("mock"), eq("deterministic-v1"))).thenReturn(model);
        return catalog;
    }
}
