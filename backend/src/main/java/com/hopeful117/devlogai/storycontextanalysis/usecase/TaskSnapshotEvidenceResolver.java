package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.reference.AiReference;
import com.hopeful117.devlogai.ai.reference.AiReferenceResolver;
import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.CausalAssessment;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.EvidenceAssertion;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.EvidenceLocator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves model-selected locators only against the immutable task snapshot. */
final class TaskSnapshotEvidenceResolver {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");

    CausalAssessment bind(CausalAssessment assessment, AiTask task) {
        if (assessment == null) throw failure("causalAssessment is required");
        if (task.getAiReferenceMappingSnapshot() == null) {
            throw failure("AI reference mapping snapshot is missing");
        }
        AiReferenceResolver references = AiReferenceResolver.fromMap(task.getAiReferenceMappingSnapshot());
        Set<String> seenReferences = new HashSet<>();
        Set<String> seenDigests = new HashSet<>();
        List<EvidenceAssertion> resolved = new ArrayList<>();
        for (EvidenceAssertion assertion : assessment.evidenceAssertions()) {
            EvidenceRef evidenceReference = assertion.evidenceReference();
            if (evidenceReference == null || evidenceReference.reference() == null) {
                throw failure("Evidence assertion reference is missing");
            }
            String reference = evidenceReference.reference();
            try {
                references.resolve(new AiReference(AiReferenceType.REPOSITORY_EVIDENCE,
                        reference, AiReferenceScope.REPOSITORY), "EVIDENCE_REFERENCE");
            } catch (RuntimeException exception) {
                throw failure("Reference is not authorized: " + reference);
            }
            if (!seenReferences.add(reference + "\u0000" + assertion.locator())) {
                throw failure("Duplicate evidence assertion: " + reference);
            }
            Map<String, Object> evidence = findEvidence(task.getSelectedKnowledgeSnapshot(), reference);
            String content = contentText(evidence);
            String status = contentValue(evidence, "status");
            if (content == null || !"COMPLETE".equals(status)) {
                throw failure("Evidence content is not resolvable for " + reference);
            }
            validateRevision(reference, contentMap(evidence));
            String resolvedContent = resolveLocator(content, assertion.locator());
            String digest = sha256(resolvedContent);
            if (!seenDigests.add(digest)) {
                throw failure("Duplicate resolved evidence content digest: " + digest);
            }
            if (assertion.resolvedContentDigest() != null
                    && !digest.equals(assertion.resolvedContentDigest())) {
                throw failure("Model-authored evidence digest does not match resolved content");
            }
            if (assertion.excerpt() != null && !assertion.excerpt().equals(resolvedContent)) {
                throw failure("Generated evidence excerpt does not match resolved content");
            }
            resolved.add(assertion.withResolvedContent(resolvedContent, digest));
        }
        return new CausalAssessment(assessment.question(), assessment.classification(), resolved,
                assessment.explanation());
    }

    /**
     * Comparative Baseline evidence-only resolution. This deliberately does not
     * construct or validate a causal assessment.
     */
    Map<String, Object> resolveComparativeCitation(
            Map<String, Object> citation,
            AiTask task,
            String repositoryId,
            String repositoryRevision,
            String providerVisibleSourceIdentity
    ) {
        String reference = text(citation, "reference");
        AiReferenceResolver references = AiReferenceResolver.fromMap(task.getAiReferenceMappingSnapshot());
        references.resolve(new AiReference(AiReferenceType.REPOSITORY_EVIDENCE,
                reference, AiReferenceScope.REPOSITORY), "EVIDENCE_REFERENCE");

        Map<String, Object> evidence = findEvidence(task.getSelectedKnowledgeSnapshot(), reference);
        String content = contentText(evidence);
        String status = contentValue(evidence, "status");
        if (content == null || !"COMPLETE".equals(status)) {
            throw failure("Evidence content is not resolvable for " + reference);
        }
        Map<String, Object> contentMap = contentMap(evidence);
        validateRevision(reference, contentMap);
        if (!repositoryRevision.equals(contentMap.get("revision"))) {
            throw failure("Evidence revision does not match the comparative repository revision: " + reference);
        }

        Map<String, Object> locator = citation.get("locator") instanceof Map<?, ?> value
                ? castMap(value) : null;
        String resolvedContent = locator == null ? content : resolveComparativeLocator(content, locator);
        String resolvedDigest = sha256(resolvedContent);
        List<Map<String, Object>> matches = new ArrayList<>();
        String excerpt = citation.get("excerpt") instanceof String value ? value : null;
        if (locator != null || excerpt != null) {
            if (excerpt == null || excerpt.isEmpty()) {
                throw failure("Comparative evidence excerpt must be non-empty");
            }
            int offset = resolvedContent.indexOf(excerpt);
            while (offset >= 0) {
                int startByte = resolvedContent.substring(0, offset).getBytes(StandardCharsets.UTF_8).length;
                int endByte = startByte + excerpt.getBytes(StandardCharsets.UTF_8).length;
                matches.add(Map.of("startByte", startByte, "endByte", endByte));
                offset = resolvedContent.indexOf(excerpt, offset + 1);
            }
            if (matches.isEmpty()) {
                throw failure("Comparative evidence excerpt does not match resolved content");
            }
        }

        Map<String, Object> canonicalIdentity = new LinkedHashMap<>();
        canonicalIdentity.put("repositoryId", repositoryId);
        canonicalIdentity.put("repositoryRevision", repositoryRevision);
        canonicalIdentity.put("typedReferenceKind", typedReferenceKind(reference, locator));
        canonicalIdentity.put("canonicalReference", reference);
        if (locator != null) {
            if (locator.get("path") != null) canonicalIdentity.put("path", locator.get("path"));
            if (locator.get("commit") != null) canonicalIdentity.put("commit", locator.get("commit"));
            if (locator.get("header") != null) canonicalIdentity.put("hunkIdentity", locator.get("header"));
        } else if (!reference.startsWith("commit:")) {
            canonicalIdentity.put("path", reference);
        } else {
            canonicalIdentity.put("commit", reference.substring("commit:".length()));
        }
        canonicalIdentity.put("normalizedLocator", locator == null ? Map.of() : locator);
        canonicalIdentity.put("resolvedContentDigest", resolvedDigest);
        canonicalIdentity.put("providerVisibleSourceIdentity", providerVisibleSourceIdentity);

        return Map.of(
                "reference", reference,
                "resolvedContentDigest", resolvedDigest,
                "excerptMatchOffsets", matches,
                "canonicalEvidenceIdentity", canonicalIdentity
        );
    }

    private String resolveComparativeLocator(String content, Map<String, Object> locator) {
        String kind = text(locator, "kind");
        return switch (kind) {
            case "LINE_RANGE" -> {
                int start = integer(locator, "startLine");
                int end = integer(locator, "endLine");
                yield resolveLocator(content, new EvidenceLocator(
                        EvidenceLocator.LocatorKind.LINE_RANGE, start, end, null));
            }
            case "SECTION" -> resolveLocator(content, new EvidenceLocator(
                    EvidenceLocator.LocatorKind.SECTION, null, null, text(locator, "heading")));
            case "COMMIT_HUNK" -> resolveCommitHunk(content, locator);
            default -> throw failure("Unsupported comparative locator kind: " + kind);
        };
    }

    private String resolveCommitHunk(String content, Map<String, Object> locator) {
        String header = text(locator, "header");
        List<String> lines = Arrays.asList(content.split("\\R", -1));
        int start = lines.indexOf(header);
        if (start < 0) throw failure("Commit hunk header is not present: " + header);
        int end = lines.size();
        for (int index = start + 1; index < lines.size(); index++) {
            if (lines.get(index).startsWith("@@")) {
                end = index;
                break;
            }
        }
        return String.join("\n", lines.subList(start, end));
    }

    private String typedReferenceKind(String reference, Map<String, Object> locator) {
        if (locator != null && "COMMIT_HUNK".equals(locator.get("kind"))) return "COMMIT_HUNK";
        return reference.startsWith("commit:") ? "COMMIT" : "DOCUMENT";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    private String text(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.isBlank()) {
            throw failure("Comparative citation field is invalid: " + key);
        }
        return text;
    }

    private int integer(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number number) || number.intValue() < 1) {
            throw failure("Comparative citation line field is invalid: " + key);
        }
        return number.intValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findEvidence(Map<String, Object> snapshot, String reference) {
        if (snapshot == null) throw failure("Selected knowledge snapshot is missing");
        Object rawContext = snapshot.get("repositoryContext");
        if (!(rawContext instanceof Map<?, ?> context)) throw failure("Repository context snapshot is missing");
        Object rawEvidence = context.get("evidence");
        if (!(rawEvidence instanceof List<?> evidenceItems)) throw failure("Repository evidence snapshot is missing");
        for (Object raw : evidenceItems) {
            if (raw instanceof Map<?, ?> value && reference.equals(value.get("reference"))) {
                return (Map<String, Object>) value;
            }
        }
        throw failure("Evidence reference is not present in the selected snapshot: " + reference);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contentMap(Map<String, Object> evidence) {
        Object raw = evidence.get("content");
        if (!(raw instanceof Map<?, ?> content)) throw failure("Evidence content is missing");
        return (Map<String, Object>) content;
    }

    private String contentText(Map<String, Object> evidence) {
        Object rawContent = evidence.get("content");
        if (!(rawContent instanceof Map<?, ?> content)) return null;
        Object text = content.get("text");
        return text instanceof String value ? value : null;
    }

    private String contentValue(Map<String, Object> evidence, String key) {
        Object rawContent = evidence.get("content");
        if (!(rawContent instanceof Map<?, ?> content)) return null;
        Object value = content.get(key);
        return value == null ? null : value.toString();
    }

    private void validateRevision(String reference, Map<String, Object> content) {
        Object revision = content.get("revision");
        if (!(revision instanceof String value) || value.isBlank()) {
            throw failure("Evidence revision is missing: " + reference);
        }
        if (reference.startsWith("document:")) {
            int at = reference.lastIndexOf('@');
            if (at < 0 || !value.equals(reference.substring(at + 1))) {
                throw failure("Evidence revision does not match reference: " + reference);
            }
        }
    }

    private String resolveLocator(String content, EvidenceLocator locator) {
        List<String> lines = Arrays.asList(content.split("\\R", -1));
        return switch (locator.kind()) {
            case LINE_RANGE -> {
                if (locator.endLine() > lines.size()) throw failure("Line locator exceeds content bounds");
                yield String.join("\n", lines.subList(locator.startLine() - 1, locator.endLine()));
            }
            case SECTION -> resolveSection(lines, locator.heading());
        };
    }

    private String resolveSection(List<String> lines, String heading) {
        int start = -1;
        int level = -1;
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = HEADING.matcher(lines.get(i));
            if (matcher.matches() && matcher.group(2).equals(heading)) {
                start = i;
                level = matcher.group(1).length();
                break;
            }
        }
        if (start < 0) throw failure("Section heading is not present: " + heading);
        int end = lines.size();
        for (int i = start + 1; i < lines.size(); i++) {
            Matcher matcher = HEADING.matcher(lines.get(i));
            if (matcher.matches() && matcher.group(1).length() <= level) {
                end = i;
                break;
            }
        }
        return String.join("\n", lines.subList(start, end));
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private IllegalStateException failure(String message) {
        return new IllegalStateException("Invalid authoritative evidence assertion: " + message);
    }
}
