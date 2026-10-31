package com.hopeful117.devlogai.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mcp/delegations")
@RequiredArgsConstructor
public class McpDelegationController {
    private final AuthenticatedPrincipalResolver principalResolver;
    private final McpDelegationGrantService delegationService;

    @PostMapping
    public ResponseEntity<IssuedDelegationResponse> issue(
            @Valid @RequestBody IssueDelegationRequest request,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        McpDelegationGrantService.IssuedDelegation issued = delegationService.issue(
                principal, request.projectSlug(), request.scopes(), request.ttlSeconds());
        return ResponseEntity.ok(new IssuedDelegationResponse(
                issued.grantId(), issued.token(), issued.expiresAt(), issued.scopes()));
    }

    @DeleteMapping("/{grantId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID grantId, HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        delegationService.revoke(principal, grantId);
        return ResponseEntity.noContent().build();
    }

    public record IssueDelegationRequest(
            @NotBlank String projectSlug,
            @NotEmpty List<@NotBlank String> scopes,
            Long ttlSeconds) {}

    public record IssuedDelegationResponse(UUID grantId, String token, Instant expiresAt,
                                           List<String> scopes) {}
}
