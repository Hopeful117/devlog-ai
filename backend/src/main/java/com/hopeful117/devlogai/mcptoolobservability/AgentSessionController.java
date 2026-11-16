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
public class AgentSessionController {
    private final AuthenticatedPrincipalResolver principalResolver;
    private final CoreProjectAuthorizationService authorizationService;
    private final AgentSessionService service;

    @GetMapping("/api/v1/projects/{projectId}/agent-sessions")
    public ResponseEntity<AgentSessionPageResponse> byProject(
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

    @GetMapping("/api/v1/projects/{projectId}/agent-sessions/{sessionId}/operations")
    public ResponseEntity<McpOperationPageResponse> operationsBySession(
            @PathVariable UUID projectId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        if (!authorizationService.isAuthorized(principal, projectId, "MCP_TOOL_EVENTS_READ")) {
            throw new EntityNotFoundException("Project", projectId);
        }
        return ResponseEntity.ok(service.listOperationsBySession(projectId, sessionId, page, size));
    }
}
