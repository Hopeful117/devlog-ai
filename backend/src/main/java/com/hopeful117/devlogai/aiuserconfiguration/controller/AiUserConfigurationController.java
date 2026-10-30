package com.hopeful117.devlogai.aiuserconfiguration.controller;

import com.hopeful117.devlogai.aiuserconfiguration.entity.AiUserConfiguration;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiModelSelectionOrigin;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiUserConfigurationState;
import com.hopeful117.devlogai.aiuserconfiguration.service.AiUserConfigurationService;
import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/configuration")
public class AiUserConfigurationController {
    private final AiUserConfigurationService service;
    private final AuthenticatedPrincipalResolver principalResolver;

    public AiUserConfigurationController(AiUserConfigurationService service,
                                         AuthenticatedPrincipalResolver principalResolver) {
        this.service = service;
        this.principalResolver = principalResolver;
    }

    @GetMapping
    public ResponseEntity<ConfigurationResponse> get(HttpServletRequest request) {
        var principal = principalResolver.resolve(request)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        var configuration = service.find(principal);
        return ResponseEntity.ok(ConfigurationResponse.from(configuration, service.state(configuration)));
    }

    @PutMapping
    public ResponseEntity<ConfigurationResponse> put(
            @Valid @RequestBody ConfigurationRequest request,
            HttpServletRequest servletRequest) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        var configuration = service.save(principal, request.provider(), request.authenticationMethod(),
                request.selectedModel(), request.modelSelectionOrigin(), request.allowedParameters());
        return ResponseEntity.ok(ConfigurationResponse.from(
                java.util.Optional.of(configuration), service.state(java.util.Optional.of(configuration))));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(HttpServletRequest request) {
        var principal = principalResolver.resolve(request)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        service.delete(principal);
        return ResponseEntity.noContent().build();
    }

    public record ConfigurationRequest(
            @NotBlank String provider,
            @NotNull AiAuthenticationMethod authenticationMethod,
            @NotBlank String selectedModel,
            @NotNull AiModelSelectionOrigin modelSelectionOrigin,
            Map<String, Object> allowedParameters) {
    }

    public record ConfigurationResponse(
            String provider,
            AiAuthenticationMethod authenticationMethod,
            String selectedModel,
            AiModelSelectionOrigin modelSelectionOrigin,
            Map<String, Object> allowedParameters,
            AiUserConfigurationState state) {
        static ConfigurationResponse from(java.util.Optional<AiUserConfiguration> configuration,
                                           AiUserConfigurationState state) {
            return configuration.map(value -> new ConfigurationResponse(
                            value.getProvider(), value.getAuthenticationMethod(), value.getSelectedModel(),
                            value.getModelSelectionOrigin(), value.getAllowedParameters(), state))
                    .orElseGet(() -> new ConfigurationResponse(null, null, null, null, Map.of(), state));
        }
    }
}
