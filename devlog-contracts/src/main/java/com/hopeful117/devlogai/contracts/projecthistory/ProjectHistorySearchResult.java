package com.hopeful117.devlogai.contracts.projecthistory;

import java.util.List;

public record ProjectHistorySearchResult(

        String query,
        int totalMatches,
        boolean truncated,
        List<ProjectHistoryCommitMatch> results
) {
    public ProjectHistorySearchResult {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
