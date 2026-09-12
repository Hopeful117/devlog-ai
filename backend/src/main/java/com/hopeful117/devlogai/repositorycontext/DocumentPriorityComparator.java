package com.hopeful117.devlogai.repositorycontext;

import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Deterministic comparator for repository document candidates.
 * Orders documents by priority before budget application (Story 0119, §5.3.4).
 *
 * Priority order (highest first):
 * 1. Story document (always first, from the current story)
 * 2. ADR documents — ordered by ADR number, then path
 * 3. Referenced story documents — ordered by story number
 * 4. Roadmap document (if referenced)
 */
@Component
public class DocumentPriorityComparator implements Comparator<DocumentCandidate> {

    @Override
    public int compare(DocumentCandidate a, DocumentCandidate b) {
        int categoryOrder = Integer.compare(categoryPriority(a), categoryPriority(b));
        if (categoryOrder != 0) return categoryOrder;

        if (a.kind().equals("ADR_DOCUMENT") && b.kind().equals("ADR_DOCUMENT")) {
            int numberOrder = Integer.compare(a.parsedAdrNumber(), b.parsedAdrNumber());
            if (numberOrder != 0) return numberOrder;
        }

        if (a.kind().equals("STORY_DOCUMENT") && !a.isMainStory()
                && b.kind().equals("STORY_DOCUMENT") && !b.isMainStory()) {
            int numberOrder = Integer.compare(a.storyNumber(), b.storyNumber());
            if (numberOrder != 0) return numberOrder;
        }

        int pathOrder = a.path().compareTo(b.path());
        if (pathOrder != 0) return pathOrder;
        return a.evidenceRef().compareTo(b.evidenceRef());
    }

    private int categoryPriority(DocumentCandidate candidate) {
        if (candidate.kind().equals("STORY_DOCUMENT") && candidate.isMainStory()) return 0;
        if (candidate.kind().equals("ADR_DOCUMENT")) return 1;
        if (candidate.kind().equals("STORY_DOCUMENT")) return 2;
        if (candidate.kind().equals("ROADMAP_DOCUMENT")) return 3;
        return 4;
    }
}
