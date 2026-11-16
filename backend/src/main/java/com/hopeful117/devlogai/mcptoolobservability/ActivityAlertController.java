package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.authorization.*;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ActivityAlertController {
    private final AuthenticatedPrincipalResolver principalResolver;
    private final CoreProjectAuthorizationService authorizationService;
    private final ActivityAlertService service;

    @GetMapping("/api/v1/projects/{projectId}/activity-alerts")
    public ResponseEntity<ActivityAlertPageResponse> byProject(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        if (!authorizationService.isAuthorized(principal, projectId, "MCP_TOOL_EVENTS_READ")) {
            throw new EntityNotFoundException("Project", projectId);
        }
        return ResponseEntity.ok(service.listByProject(projectId, page, size));
    }

    @GetMapping("/api/v1/projects/{projectId}/activity-alerts/count")
    public ResponseEntity<ActivityAlertCountResponse> countByProject(
            @PathVariable UUID projectId,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        if (!authorizationService.isAuthorized(principal, projectId, "MCP_TOOL_EVENTS_READ")) {
            throw new EntityNotFoundException("Project", projectId);
        }
        return ResponseEntity.ok(service.countErrors(projectId));
    }
}
