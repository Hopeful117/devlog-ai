package com.hopeful117.devlogai.aicredentialbinding.controller;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;
import com.hopeful117.devlogai.aicredentialbinding.service.AiCredentialBindingService;
import com.hopeful117.devlogai.aicredentialbinding.service.AiEngineBindingClient;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Authenticated Core boundary for user-authorized AI credential bindings. */
@RestController
@RequestMapping("/api/v1/ai-credential-bindings")
public class AiCredentialBindingController {
    private final AiCredentialBindingService bindingService;
    private final AiEngineBindingClient bindingClient;
    private final AuthenticatedPrincipalResolver principalResolver;

    public AiCredentialBindingController(
            AiCredentialBindingService bindingService,
            AiEngineBindingClient bindingClient,
            AuthenticatedPrincipalResolver principalResolver) {
        this.bindingService = bindingService;
        this.bindingClient = bindingClient;
        this.principalResolver = principalResolver;
    }

    @PostMapping
    public ResponseEntity<BindingResponse> issue(
            @Valid @RequestBody IssueBindingRequest request,
            HttpServletRequest servletRequest) {
        AiCredentialBinding binding = bindingService.issue(
                principalResolver.resolve(servletRequest)
                        .orElseThrow(UnauthenticatedPrincipalException::new),
                request.taskId(), request.provider());
        bindingClient.register(binding);
        BindingResponse response = BindingResponse.from(binding);
        return ResponseEntity.created(URI.create("/api/v1/ai-credential-bindings/" + response.id()))
                .body(response);
    }

    @DeleteMapping("/{bindingId}")
    public ResponseEntity<Void> revoke(
            @PathVariable UUID bindingId,
            @Valid @RequestBody RevokeBindingRequest request,
            HttpServletRequest servletRequest) {
        bindingService.revoke(
                principalResolver.resolve(servletRequest)
                        .orElseThrow(UnauthenticatedPrincipalException::new),
                bindingId, request.reason());
        return ResponseEntity.noContent().build();
    }

    public record IssueBindingRequest(
            @NotNull
            UUID taskId,
            @NotBlank String provider) {
    }

    public record RevokeBindingRequest(String reason) {
    }

    public record BindingResponse(
            UUID id,
            UUID taskId,
            String provider,
            String status,
            java.time.Instant issuedAt,
            java.time.Instant expiresAt) {
        static BindingResponse from(AiCredentialBinding binding) {
            return new BindingResponse(
                    binding.getId(),
                    binding.getAiTask().getId(),
                    binding.getProvider(),
                    binding.getStatus().name(),
                    binding.getIssuedAt(),
                    binding.getExpiresAt());
        }
    }
}
