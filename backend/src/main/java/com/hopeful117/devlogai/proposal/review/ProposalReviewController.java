package com.hopeful117.devlogai.proposal.review;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProposalReviewController {
    private final ProposalReviewService service;

    @GetMapping("/api/v1/analyses/{analysisId}/proposal-review")
    public ProposalReviewResponse get(@PathVariable UUID analysisId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return service.get(analysisId, page, size);
    }
}
