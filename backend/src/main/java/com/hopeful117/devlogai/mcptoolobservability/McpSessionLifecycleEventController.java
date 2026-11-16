package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.authorization.*;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class McpSessionLifecycleEventController {
    private final AuthenticatedPrincipalResolver principalResolver;
    private final CoreProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final McpSessionLifecycleEventService service;

    @PostMapping("/api/v1/mcp/session-events")
    public ResponseEntity<Void> ingest(
            @Valid @RequestBody McpSessionLifecycleEventIngestRequest request,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        if (principal.kind() != PrincipalKind.AI_AGENT) {
            throw new UnauthenticatedPrincipalException();
        }
        Project project = projectRepository.findBySlug(request.projectSlug())
                .orElseThrow(() -> new EntityNotFoundException("Project", request.projectSlug()));
        if (!authorizationService.isAuthorized(principal, project, "MCP_TOOL_EVENTS_INGEST")) {
            throw new EntityNotFoundException("Project", request.projectSlug());
        }
        service.ingest(project, request);
        return ResponseEntity.ok().build();
    }
}
