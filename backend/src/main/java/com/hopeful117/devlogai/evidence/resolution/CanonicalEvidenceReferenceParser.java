package com.hopeful117.devlogai.evidence.resolution;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class CanonicalEvidenceReferenceParser {
    private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{7,64}");

    public ParsedEvidenceReference parse(String reference) {
        if (reference == null || reference.isBlank()) {
            throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                    "Evidence reference is missing");
        }
        if (reference.startsWith("git:")) {
            return parseGit(reference);
        }
        if (reference.startsWith("document:")) {
            return parseDocument(reference);
        }
        if (reference.startsWith("fact:")) {
            return parseFact(reference);
        }
        if (reference.startsWith("decision:")) {
            return parsePersisted(reference, EvidenceResolutionFamily.DECISION, "Decision");
        }
        if (reference.startsWith("event:")) {
            return parsePersisted(reference, EvidenceResolutionFamily.ENGINEERING_EVENT,
                    "Engineering Event");
        }
        if (reference.startsWith("story:")) {
            return parsePersisted(reference, EvidenceResolutionFamily.STORY, "Story");
        }
        if (reference.startsWith("analysis:")) {
            return parsePersisted(reference, EvidenceResolutionFamily.ANALYSIS, "Analysis");
        }
        if (reference.startsWith("artifact:")) {
            return parsePersisted(reference, EvidenceResolutionFamily.ARTIFACT, "Artifact");
        }
        if (reference.startsWith("challenge:")) {
            return parsePersisted(reference, EvidenceResolutionFamily.CHALLENGE, "Challenge");
        }
        if (hasKnownUnsupportedFamily(reference)) {
            throw failure(EvidenceResolutionFailureCode.UNSUPPORTED_REFERENCE_TYPE, reference,
                    "Evidence reference family is not supported by this resolution slice");
        }
        throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                "Evidence reference family is unknown");
    }

    private ParsedEvidenceReference parseFact(String reference) {
        String[] parts = reference.split(":", -1);
        if (parts.length != 2) {
            throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                    "Canonical Fact reference must be fact:{uuid}");
        }
        try {
            UUID.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                    "Canonical Fact reference contains an invalid UUID");
        }
        return new ParsedEvidenceReference(reference, EvidenceResolutionFamily.FACT,
                null, null, parts[1]);
    }

    private ParsedEvidenceReference parsePersisted(
            String reference, EvidenceResolutionFamily family, String label) {
        String[] parts = reference.split(":", -1);
        if (parts.length != 2) {
            throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                    "Canonical %s reference must be %s:{uuid}".formatted(label, familyPrefix(family)));
        }
        try {
            UUID.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                    "Canonical %s reference contains an invalid UUID".formatted(label));
        }
        return new ParsedEvidenceReference(reference, family, null, null, parts[1]);
    }

    private String familyPrefix(EvidenceResolutionFamily family) {
        return switch (family) {
            case DECISION -> "decision";
            case ENGINEERING_EVENT -> "event";
            case STORY -> "story";
            case ANALYSIS -> "analysis";
            case ARTIFACT -> "artifact";
            case CHALLENGE -> "challenge";
            default -> family.name().toLowerCase();
        };
    }

    private ParsedEvidenceReference parseGit(String reference) {
        String[] parts = reference.split(":", -1);
        if (parts.length != 3 || parts[1].isBlank() || !SHA.matcher(parts[2]).matches()) {
            throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                    "Canonical Git reference must be git:{sourceId}:{sha}");
        }
        return new ParsedEvidenceReference(reference, EvidenceResolutionFamily.GIT_COMMIT,
                sourceId(parts[1], reference), parts[2], parts[2]);
    }

    private ParsedEvidenceReference parseDocument(String reference) {
        String[] prefixAndBody = reference.split(":", 2);
        String[] sourceAndPath = prefixAndBody.length == 2
                ? prefixAndBody[1].split(":", 2) : new String[0];
        if (sourceAndPath.length != 2 || sourceAndPath[0].isBlank()) {
            throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                    "Canonical document reference must be document:{sourceId}:{path}@{revision}");
        }
        int revisionSeparator = sourceAndPath[1].lastIndexOf('@');
        if (revisionSeparator <= 0 || revisionSeparator == sourceAndPath[1].length() - 1) {
            throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                    "Canonical document reference must include a path and revision");
        }
        String path = sourceAndPath[1].substring(0, revisionSeparator);
        String revision = sourceAndPath[1].substring(revisionSeparator + 1);
        return new ParsedEvidenceReference(reference, EvidenceResolutionFamily.DOCUMENT,
                sourceId(sourceAndPath[0], reference), revision, path);
    }

    private UUID sourceId(String value, String reference) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw failure(EvidenceResolutionFailureCode.UNKNOWN_REFERENCE, reference,
                    "Canonical evidence reference contains an invalid source id");
        }
    }

    private boolean hasKnownUnsupportedFamily(String reference) {
        return reference.startsWith("diff:")
                || reference.startsWith("file:")
                || reference.startsWith("observation:")
                || reference.startsWith("insight:");
    }

    private EvidenceResolutionException failure(
            EvidenceResolutionFailureCode code,
            String reference,
            String message
    ) {
        return new EvidenceResolutionException(code, reference, null, message);
    }
}
