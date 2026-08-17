package com.hopeful117.devlogai.engineeringcontext.controller;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
public class EngineeringContextController {
    private final EngineeringContextFacade engineeringContextFacade;

    @GetMapping("/{projectSlug}/engineering-context")
    public ResponseEntity<EngineeringContext> getEngineeringContext(
            @PathVariable String projectSlug,
            @RequestParam String intent
    ) {
        return ResponseEntity.ok(
                engineeringContextFacade.getEngineeringContext(
                        projectSlug,
                        intent
                )
        );
    }


}
