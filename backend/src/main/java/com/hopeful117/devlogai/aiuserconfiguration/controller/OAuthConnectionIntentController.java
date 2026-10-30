package com.hopeful117.devlogai.aiuserconfiguration.controller;

import com.hopeful117.devlogai.aiuserconfiguration.service.OAuthConnectionIntentService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/configuration/oauth/chatgpt")
public class OAuthConnectionIntentController {
    private final OAuthConnectionIntentService service;
    private final AuthenticatedPrincipalResolver principalResolver;

    public OAuthConnectionIntentController(OAuthConnectionIntentService service,
                                           AuthenticatedPrincipalResolver principalResolver) {
        this.service = service;
        this.principalResolver = principalResolver;
    }

    @PostMapping("/start")
    public ResponseEntity<AuthorizationResponse> start(HttpServletRequest request) {
        var principal = principalResolver.resolve(request)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        var result = service.start(principal);
        return ResponseEntity.ok(new AuthorizationResponse(result.authorizationUrl(), result.intentId(), result.expiresAt()));
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> complete(HttpServletRequest request,
                                         @Valid @RequestBody CompletionRequest completion) {
        var principal = principalResolver.resolve(request)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        service.completeIntent(principal, completion.intentId(), completion.completionCode());
        return ResponseEntity.noContent().build();
    }

    public record AuthorizationResponse(String authorizationUrl, UUID intentId, java.time.Instant expiresAt) { }

    public record CompletionRequest(UUID intentId, @NotBlank String completionCode) { }
}
