package com.hopeful117.devlogai.aiuserconfiguration.controller;

import com.hopeful117.devlogai.aiuserconfiguration.service.ApiKeyConnectionIntentService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/configuration/api-key/anthropic")
public class ApiKeyConnectionIntentController {
    private final ApiKeyConnectionIntentService service;
    private final AuthenticatedPrincipalResolver principalResolver;
    public ApiKeyConnectionIntentController(ApiKeyConnectionIntentService service,
                                            AuthenticatedPrincipalResolver principalResolver) {
        this.service = service; this.principalResolver = principalResolver;
    }
    @PostMapping("/start")
    public EnrollmentResponse start(HttpServletRequest request) {
        return EnrollmentResponse.from(service.start(principalResolver.resolve(request)
                .orElseThrow(UnauthenticatedPrincipalException::new)));
    }
    @PostMapping("/complete")
    public ResponseEntity<Void> complete(HttpServletRequest request, @Valid @RequestBody CompletionRequest body) {
        service.complete(principalResolver.resolve(request).orElseThrow(UnauthenticatedPrincipalException::new),
                body.intentId(), body.credentialHandle());
        return ResponseEntity.noContent().build();
    }
    public record CompletionRequest(@NotNull UUID intentId, @NotBlank String credentialHandle) { }
    public record EnrollmentResponse(String intakeEndpoint, UUID intentId, String provider,
                                     String authenticationMethod, long issuedAt, long expiresAt, String principalBinding,
                                     String signature, Instant expiresAtInstant) {
        static EnrollmentResponse from(ApiKeyConnectionIntentService.EnrollmentStart value) {
            return new EnrollmentResponse(value.intakeEndpoint(), value.intentId(), value.provider(),
                    value.authenticationMethod(), value.issuedAt(), value.expiresAt(), value.principalBinding(), value.signature(),
                    value.expiresAtInstant());
        }
    }
}
