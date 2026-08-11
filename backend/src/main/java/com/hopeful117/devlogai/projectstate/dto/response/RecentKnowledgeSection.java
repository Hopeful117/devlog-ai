package com.hopeful117.devlogai.projectstate.dto.response;

import com.hopeful117.devlogai.projectstate.dto.inner.KnowledgeSummary;

import java.util.List;

public record RecentKnowledgeSection(
        List<KnowledgeSummary> recentKnowledge
) {
}