package com.hopeful117.devlogai.repositorycontext;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic extractor of document references from Story Markdown content.
 * V1: one-hop only. Extracts exact ADR-NNN identifiers, Story NNNN identifiers,
 * and exact repository-relative Markdown links.
 */
@Component
public class DocumentReferenceExtractor {

    private static final Pattern ADR_IDENTIFIER = Pattern.compile(
            "\\bADR-(\\d{1,5})\\b");
    private static final Pattern STORY_IDENTIFIER = Pattern.compile(
            "\\bStory\\s+(\\d{1,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_LINK = Pattern.compile(
            "\\[([^]]*)\\]\\(([^)]+)\\)");
    private static final Pattern ROADMAP_LINK = Pattern.compile(
            "(?:docs/)?roadmap\\.md", Pattern.CASE_INSENSITIVE);

    public ExtractedReferences extract(String storyMarkdown) {
        Set<String> adrNumbers = new LinkedHashSet<>();
        Set<String> storyNumbers = new LinkedHashSet<>();
        Set<String> markdownLinks = new LinkedHashSet<>();
        boolean hasRoadmapReference = false;

        if (storyMarkdown == null || storyMarkdown.isBlank()) {
            return new ExtractedReferences(adrNumbers, storyNumbers, markdownLinks, hasRoadmapReference);
        }

        Matcher adrMatcher = ADR_IDENTIFIER.matcher(storyMarkdown);
        while (adrMatcher.find()) {
            adrNumbers.add(adrMatcher.group(1));
        }

        Matcher storyMatcher = STORY_IDENTIFIER.matcher(storyMarkdown);
        while (storyMatcher.find()) {
            storyNumbers.add(storyMatcher.group(1));
        }

        Matcher linkMatcher = MARKDOWN_LINK.matcher(storyMarkdown);
        while (linkMatcher.find()) {
            String target = linkMatcher.group(2).strip();
            if (!target.startsWith("http://") && !target.startsWith("https://")
                    && !target.startsWith("#")) {
                markdownLinks.add(target);
                if (ROADMAP_LINK.matcher(target).find()) {
                    hasRoadmapReference = true;
                }
            }
        }

        return new ExtractedReferences(adrNumbers, storyNumbers, markdownLinks, hasRoadmapReference);
    }

    public record ExtractedReferences(
            Set<String> adrNumbers,
            Set<String> storyNumbers,
            Set<String> markdownLinks,
            boolean hasRoadmapReference
    ) {
    }
}
