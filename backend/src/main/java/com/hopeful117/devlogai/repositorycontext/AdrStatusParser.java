package com.hopeful117.devlogai.repositorycontext;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic parser for ADR document status from the {@code ## Status} Markdown section.
 * V1: only parses the explicit Status heading. No full Markdown AST. No AI.
 */
@Component
public class AdrStatusParser {

    private static final Pattern STATUS_HEADING = Pattern.compile(
            "^##\\s+Status\\s*$", Pattern.MULTILINE);
    private static final Pattern STATUS_VALUE = Pattern.compile(
            "\\*\\*([^*]+)\\**", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPERSEDED_BY = Pattern.compile(
            "\\*{0,2}(?:superseded|replaced)\\*{0,2}\\s+by\\s+\\[([^\\]]+)\\]",
            Pattern.CASE_INSENSITIVE);

    public AdrStatusResult parse(String markdownContent) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return new AdrStatusResult(DocumentStatus.UNKNOWN, null);
        }
        Matcher headingMatcher = STATUS_HEADING.matcher(markdownContent);
        if (!headingMatcher.find()) {
            return new AdrStatusResult(DocumentStatus.UNKNOWN, null);
        }
        int statusStart = headingMatcher.end();
        String afterHeading = markdownContent.substring(statusStart);
        String statusLine = extractNextNonEmptyLine(afterHeading);
        if (statusLine == null || statusLine.isBlank()) {
            return new AdrStatusResult(DocumentStatus.UNKNOWN, null);
        }
        Matcher valueMatcher = STATUS_VALUE.matcher(statusLine);
        if (!valueMatcher.find()) {
            return new AdrStatusResult(DocumentStatus.UNKNOWN, null);
        }
        String rawValue = valueMatcher.group(1).trim();
        DocumentStatus status = normalizeStatus(rawValue);
        String supersededBy = extractSupersededBy(statusLine);
        return new AdrStatusResult(status, supersededBy);
    }

    private String extractNextNonEmptyLine(String text) {
        for (String line : text.split("\\R", 2)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private DocumentStatus normalizeStatus(String rawValue) {
        String lower = rawValue.toLowerCase(Locale.ROOT).strip();
        return switch (lower) {
            case "accepted" -> DocumentStatus.ACCEPTED;
            case "proposed" -> DocumentStatus.PROPOSED;
            case "superseded" -> DocumentStatus.SUPERSEDED;
            case "deprecated" -> DocumentStatus.SUPERSEDED;
            case "rejected" -> DocumentStatus.REJECTED;
            default -> DocumentStatus.UNKNOWN;
        };
    }

    private String extractSupersededBy(String statusLine) {
        Matcher matcher = SUPERSEDED_BY.matcher(statusLine);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    public record AdrStatusResult(DocumentStatus status, String supersededBy) {
    }
}
