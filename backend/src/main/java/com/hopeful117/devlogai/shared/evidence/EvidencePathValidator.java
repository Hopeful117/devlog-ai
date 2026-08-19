package com.hopeful117.devlogai.shared.evidence;

public final class EvidencePathValidator {

    private static final String[] NON_FILE_NAMESPACE_PREFIXES =
            {"analysis:", "source:", "commit:", "git:", "diff:", "fact:", "observation:",
                    "decision:", "insight:", "story:", "artifact:", "milestone:", "repository:"};

    private EvidencePathValidator() {
        // utility class
    }

    /**
     * Normalizes a repository evidence reference by replacing backslashes with forward slashes.
     * Identical to the normalization performed in
     * {@link com.hopeful117.devlogai.collection.service.KnowledgeCollectionServiceImpl#validateEvidenceReference(String)}.
     */
    public static String normalize(String reference) {
        if (reference == null) {
            return "";
        }
        return reference.replace('\\', '/');
    }

    /**
     * Returns {@code true} if the given reference is a valid relative repository path.
     * Rejects references that start with "/", have a drive letter (e.g. "C:/..."),
     * are exactly "..", start with "../", or contain "/../".
     * This logic is identical to the existing validation in
     * {@link com.hopeful117.devlogai.collection.service.KnowledgeCollectionServiceImpl#validateEvidenceReference(String)}.
     */
    public static boolean isValidRelativePath(String reference) {
        if (reference == null) {
            return false;
        }
        String normalized = normalize(reference);
        return !(normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:/.*")
                || normalized.equals("..")
                || normalized.startsWith("../")
                || normalized.contains("/../"));
    }

    /**
     * Returns {@code true} if the given reference starts with a known non-file namespace prefix.
     * Known prefixes: analysis:, source:, commit:, git:, diff:, fact:, observation:, decision:,
     * insight:, story:, artifact:, milestone:, repository:
     * The check is case-insensitive.
     */
    public static boolean hasNonFileNamespacePrefix(String reference) {
        if (reference == null) {
            return false;
        }
        String normalized = normalize(reference).toLowerCase();
        for (String prefix : NON_FILE_NAMESPACE_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}