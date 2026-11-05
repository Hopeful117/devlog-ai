package com.hopeful117.devlogai.engineeringcontext.mapper;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;

/** Shared Core classification rules for public context and evaluation metadata. */
public final class TrustTierClassifier {
    public enum Classification {
        TRUSTED,
        HUMAN_AUTHORED,
        TECHNICAL_EVIDENCE,
        SYSTEM_METADATA,
        UNVALIDATED,
        TRANSIENT_AI,
        UNKNOWN
    }

    private TrustTierClassifier() {
    }

    public static Classification classify(String kind, String sourceType) {
        if ("VALIDATABLE_PROPOSAL".equals(kind)) {
            return Classification.UNVALIDATED;
        }

        if ("AI_ENGINE".equals(sourceType)) {
            return Classification.TRANSIENT_AI;
        }

        return classifyPublicMetadata(kind, sourceType);
    }

    private static Classification classifyPublicMetadata(String kind, String sourceType) {
        if ("CORE_KNOWLEDGE".equals(sourceType)
                && ("INSIGHT".equals(kind) || "DECISION".equals(kind)
                || "ENGINEERING_EVENT".equals(kind))) {
            return Classification.TRUSTED;
        }

        if ("PROJECT_NOTE".equals(kind)
                || "MILESTONE".equals(kind)
                || "ARTIFACT".equals(kind)
                || "ENGINEERING_STORY".equals(kind)
                || "CHALLENGE".equals(kind)
                || "STORY_DOCUMENT".equals(kind)
                || "ADR_DOCUMENT".equals(kind)
                || "ROADMAP_DOCUMENT".equals(kind)) {
            return Classification.HUMAN_AUTHORED;
        }

        if ("ANALYSIS".equals(kind)
                || "FRESHNESS".equals(kind)
                || "DIAGNOSTIC".equals(kind)
                || "SELECTION_METADATA".equals(kind)) {
            return Classification.SYSTEM_METADATA;
        }

        if (sourceType != null
                && (sourceType.equals("GIT")
                || sourceType.equals("DETERMINISTIC_EXTRACTION")
                || sourceType.equals("CORE_ANALYSIS")
                || sourceType.equals("REPOSITORY_STRUCTURE"))) {
            return Classification.TECHNICAL_EVIDENCE;
        }

        return Classification.UNKNOWN;
    }

    public static TrustTier publicTrustTier(String kind, String sourceType) {
        if ("VALIDATABLE_PROPOSAL".equals(kind)) {
            return null;
        }
        return switch (classifyPublicMetadata(kind, sourceType)) {
            case TRUSTED -> TrustTier.TRUSTED;
            case HUMAN_AUTHORED -> TrustTier.HUMAN_AUTHORED;
            case TECHNICAL_EVIDENCE -> TrustTier.TECHNICAL_EVIDENCE;
            case SYSTEM_METADATA -> TrustTier.SYSTEM_METADATA;
            case UNVALIDATED, TRANSIENT_AI, UNKNOWN -> null;
        };
    }
}
