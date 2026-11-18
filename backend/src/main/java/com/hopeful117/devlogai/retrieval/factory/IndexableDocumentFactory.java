package com.hopeful117.devlogai.retrieval.factory;

import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.retrieval.contract.IndexableDocument;
import com.hopeful117.devlogai.story.entity.EngineeringStory;

import java.util.Map;
import java.util.UUID;

public final class IndexableDocumentFactory {

    private IndexableDocumentFactory() {}

    public static IndexableDocument fromInsight(Insight insight) {
        return new IndexableDocument() {
            public UUID projectId() { return insight.getProject().getId(); }
            public String sourceType() { return "INSIGHT"; }
            public UUID sourceId() { return insight.getId(); }
            public String sourceRevision() { return null; }
            public String trustTier() { return "TRUSTED"; }
            public String status() { return insight.getStatus().name(); }
            public String title() { return insight.getTitle(); }
            public String content() { return insight.getTitle() + "\n\n" + insight.getContent(); }
            public Map<String, Object> metadata() { return Map.of("type", insight.getType().name()); }
        };
    }

    public static IndexableDocument fromStory(EngineeringStory story) {
        return new IndexableDocument() {
            public UUID projectId() { return story.getProject().getId(); }
            public String sourceType() { return "STORY"; }
            public UUID sourceId() { return story.getId(); }
            public String sourceRevision() { return null; }
            public String trustTier() { return "HUMAN_AUTHORED"; }
            public String status() { return story.getStatus().name(); }
            public String title() { return story.getTitle(); }
            public String content() { return "Story " + story.getStoryNumber() + ": " + story.getTitle(); }
            public Map<String, Object> metadata() { return Map.of("storyNumber", story.getStoryNumber()); }
        };
    }

    public static IndexableDocument fromDecision(Decision decision) {
        return new IndexableDocument() {
            public UUID projectId() { return decision.getProject().getId(); }
            public String sourceType() { return "DECISION"; }
            public UUID sourceId() { return decision.getId(); }
            public String sourceRevision() { return null; }
            public String trustTier() { return "TRUSTED"; }
            public String status() { return "ACCEPTED"; }
            public String title() { return decision.getTitle(); }
            public String content() {
                return decision.getTitle() + "\n\n" + decision.getContext() + "\n\n" + decision.getChoice();
            }
            public Map<String, Object> metadata() { return Map.of(); }
        };
    }
}
