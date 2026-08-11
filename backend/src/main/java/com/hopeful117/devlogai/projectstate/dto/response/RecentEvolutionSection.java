package com.hopeful117.devlogai.projectstate.dto.response;

import com.hopeful117.devlogai.projectstate.dto.inner.EvolutionSummary;

import java.util.List;

public record RecentEvolutionSection(
        List<EvolutionSummary> recentEvolution
) {
}