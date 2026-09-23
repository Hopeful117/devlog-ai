package com.hopeful117.devlogai.evidence.resolution;

import java.util.UUID;
import com.hopeful117.devlogai.shared.exception.InvalidParameterException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP projection over the Core-owned evidence resolution facade. */
@RestController
@RequestMapping("/api/v1/evidence")
@RequiredArgsConstructor
public class EvidenceResolutionController {
    private final EvidenceResolutionFacade facade;

    @GetMapping("/resolve")
    public ResponseEntity<EvidenceResolutionResult> resolve(
            @RequestParam String reference,
            @RequestParam EvidenceResolutionMode mode,
            @RequestParam(required = false) UUID analysisId
    ) {
        if (reference.isBlank()) {
            throw new InvalidParameterException("reference", "blank");
        }
        return ResponseEntity.ok(facade.resolve(
                new EvidenceResolutionRequest(reference, mode, analysisId)));
    }
}
