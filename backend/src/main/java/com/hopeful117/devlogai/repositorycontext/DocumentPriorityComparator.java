package com.hopeful117.devlogai.repositorycontext;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;

/**
 * Deterministic comparator for repository document candidates.
 * Orders documents by priority before budget application (Story 0119, §5.3.4).
 *
 * Priority order (highest first):
 * 1. Story document (always first, from the current story)
 * 2. ADR documents — ordered by status (ACCEPTED > SUPERSEDED > PROPOSED > UNKNOWN) then by ADR number
 * 3. Referenced story documents — ordered by story number
 * 4. Roadmap document (if referenced)
 */
@Component
public class DocumentPriorityComparator implements Comparator<DocumentCandidate> {

    private static final Map<DocumentStatus, Integer> STATUS_PRIORITY = Map.of(
            DocumentStatus.ACCEPTED, 100,
            DocumentStatus.SUPERSEDED, 80,
            DocumentStatus.PROPOSED, 60,
            DocumentStatus.UNKNOWN, 40
    );

    @Override
    public int compare(DocumentCandidate a, DocumentCandidate b) {
        // Story document always first
        if (a.kind().equals("STORY_DOCUMENT") && a.isMainStory()) return -1;
        if (b.kind().equals("STORY_DOCUMENT") && b.isMainStory()) return 1;

        // ADR documents before referenced stories and roadmap
        if (a.kind().equals("ADR_DOCUMENT") && !b.kind().equals("ADR_DOCUMENT")) return -1;
        if (b.kind().equals("ADR_DOCUMENT") && !a.kind().equals("ADR_DOCUMENT")) return 1;

        // Both ADR: sort by status priority, then by ADR number
        if (a.kind().equals("ADR_DOCUMENT") && b.kind().equals("ADR_DOCUMENT")) {
            int statusA = STATUS_PRIORITY.getOrDefault(a.adrStatus(), 0);
            int statusB = STATUS_PRIORITY.getOrDefault(b.adrStatus(), 0);
            if (statusA != statusB) return Integer.compare(statusB, statusA); // higher priority first
            return Integer.compare(a.parsedAdrNumber(), b.parsedAdrNumber()); // lower number first
        }

        // Referenced stories before roadmap
        if (a.kind().equals("STORY_DOCUMENT") && !a.isMainStory() && b.kind().equals("ROADMAP_DOCUMENT")) return -1;
        if (b.kind().equals("STORY_DOCUMENT") && !b.isMainStory() && a.kind().equals("ROADMAP_DOCUMENT")) return 1;

        // Both referenced stories: sort by story number
        if (a.kind().equals("STORY_DOCUMENT") && !a.isMainStory()
                && b.kind().equals("STORY_DOCUMENT") && !b.isMainStory()) {
            return Integer.compare(a.storyNumber(), b.storyNumber());
        }

        // Roadmap last
        return 0;
    }
}