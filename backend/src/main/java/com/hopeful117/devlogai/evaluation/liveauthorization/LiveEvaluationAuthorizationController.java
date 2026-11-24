package com.hopeful117.devlogai.evaluation.liveauthorization;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/live-evaluation-authorizations")
@RequiredArgsConstructor
public class LiveEvaluationAuthorizationController {
    private final LiveEvaluationAuthorizationService service;
    private final AuthenticatedPrincipalResolver principalResolver;

    @PostMapping("/requests")
    public ResponseEntity<LiveEvaluationAuthorizationResponse> request(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody LiveEvaluationAuthorizationRequest request,
            HttpServletRequest servletRequest) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        return ResponseEntity.ok(service.request(request, idempotencyKey, principal));
    }

    @PostMapping("/{authorizationId}/approve")
    public ResponseEntity<LiveEvaluationAuthorizationResponse> approve(
            @PathVariable UUID authorizationId,
            @RequestParam String planDigest,
            HttpServletRequest servletRequest) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        return ResponseEntity.ok(service.approve(authorizationId, planDigest, principal));
    }

    @GetMapping("/{authorizationId}")
    public ResponseEntity<LiveEvaluationAuthorizationResponse> get(
            @PathVariable UUID authorizationId, HttpServletRequest servletRequest) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        return ResponseEntity.ok(service.get(authorizationId, principal));
    }

    @PostMapping("/{authorizationId}/consume")
    public ResponseEntity<LiveEvaluationAuthorizationResponse> consume(
            @PathVariable UUID authorizationId,
            @RequestParam String planDigest,
            @RequestHeader(value = "Idempotency-Key", required = false) String executionIdempotencyKey,
            HttpServletRequest servletRequest) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        return ResponseEntity.ok(service.consume(authorizationId, planDigest, executionIdempotencyKey, principal));
    }
}
