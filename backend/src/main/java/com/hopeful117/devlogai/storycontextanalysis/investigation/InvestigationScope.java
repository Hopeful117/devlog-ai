package com.hopeful117.devlogai.storycontextanalysis.investigation;

import java.util.List;

public record InvestigationScope(String projectSlug, String revision, List<String> allowedPathPrefixes) {
    public InvestigationScope {
        allowedPathPrefixes = List.copyOf(allowedPathPrefixes == null ? List.of() : allowedPathPrefixes);
    }
}
