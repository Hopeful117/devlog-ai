package com.hopeful117.devlogai.evaluation.livepreparation;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/live-evaluation-preparations")
@RequiredArgsConstructor
public class LiveEvaluationPreparationController {
    private final LiveEvaluationPreparationService service;
    private final AuthenticatedPrincipalResolver principalResolver;

    @PostMapping("/projects/{projectSlug}")
    public ResponseEntity<LiveEvaluationPreparationResponse> prepare(
            @PathVariable String projectSlug,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody LiveEvaluationPreparationRequest request,
            HttpServletRequest servletRequest) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        return ResponseEntity.ok(service.prepare(projectSlug, idempotencyKey, principal, request));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<LiveEvaluationPreparationResponse> get(
            @PathVariable UUID planId,
            HttpServletRequest servletRequest) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        return ResponseEntity.ok(service.get(planId, principal));
    }
}
