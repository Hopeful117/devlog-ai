package com.hopeful117.devlogai.engineeringcontext.controller;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.ProjectRole;
import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class EngineeringContextController {
    private final EngineeringContextFacade engineeringContextFacade;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final ProjectRepository projectRepository;
    private final CoreProjectAuthorizationService authorizationService;

    public EngineeringContextController(EngineeringContextFacade engineeringContextFacade) {
        this(engineeringContextFacade, null, null, null);
    }

    @Autowired
    public EngineeringContextController(
            EngineeringContextFacade engineeringContextFacade,
            AuthenticatedPrincipalResolver principalResolver,
            ProjectRepository projectRepository,
            CoreProjectAuthorizationService authorizationService) {
        this.engineeringContextFacade = engineeringContextFacade;
        this.principalResolver = principalResolver;
        this.projectRepository = projectRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{projectSlug}/engineering-context")
    public ResponseEntity<EngineeringContext> getEngineeringContext(
            @PathVariable String projectSlug,
            @RequestParam String intent,
            @RequestParam(required = false) List<String> files,
            @RequestParam(required = false) UUID storyId,
            HttpServletRequest servletRequest
    ) {
        if (principalResolver != null) {
            var principal = principalResolver.resolve(servletRequest)
                    .orElseThrow(UnauthenticatedPrincipalException::new);
            var project = projectRepository.findBySlug(projectSlug)
                    .orElseThrow(() -> new com.hopeful117.devlogai.shared.exception.EntityNotFoundException(
                            "Project", projectSlug));
            if (!authorizationService.isAuthorized(principal, project, "MCP_ENGINEERING_CONTEXT_READ")) {
                throw new com.hopeful117.devlogai.shared.exception.EntityNotFoundException(
                        "Project", projectSlug);
            }
        }
        // Convert null files to empty list for consistent behavior
        List<String> effectiveFiles = files != null ? files : List.of();
        return ResponseEntity.ok(
                engineeringContextFacade.getEngineeringContext(
                        projectSlug,
                        intent,
                        effectiveFiles,
                        storyId
                )
        );
    }
}
