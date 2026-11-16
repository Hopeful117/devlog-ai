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
public class McpOperationController {
    private final AuthenticatedPrincipalResolver principalResolver;
    private final CoreProjectAuthorizationService authorizationService;
    private final McpOperationService service;
    private final ContextTraceService contextTraceService;

    @GetMapping("/api/v1/projects/{projectId}/mcp-operations")
    public ResponseEntity<McpOperationPageResponse> byProject(
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

    @GetMapping("/api/v1/projects/{projectId}/mcp-operations/{instanceId}/{operationId}/context-trace")
    public ResponseEntity<ContextTraceView> contextTrace(
            @PathVariable java.util.UUID projectId,
            @PathVariable String instanceId,
            @PathVariable String operationId,
            jakarta.servlet.http.HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        if (!authorizationService.isAuthorized(principal, projectId, "MCP_TOOL_EVENTS_READ")) {
            throw new EntityNotFoundException("Project", projectId);
        }
        return ResponseEntity.ok(contextTraceService.getContextTrace(projectId, instanceId, operationId));
    }

    @GetMapping("/api/v1/context-traces/{aiTaskId}")
    public ResponseEntity<ContextTraceView> contextTraceByAiTaskId(
            @PathVariable UUID aiTaskId,
            jakarta.servlet.http.HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        UUID projectId = contextTraceService.findProjectIdByAiTaskId(aiTaskId);
        if (projectId == null || !authorizationService.isAuthorized(principal, projectId, "MCP_TOOL_EVENTS_READ")) {
            throw new EntityNotFoundException("ContextTrace", aiTaskId);
        }
        return ResponseEntity.ok(contextTraceService.getContextTraceByAiTaskId(projectId, aiTaskId));
    }
}
