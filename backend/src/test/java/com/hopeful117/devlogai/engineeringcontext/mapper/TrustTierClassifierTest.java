package com.hopeful117.devlogai.engineeringcontext.mapper;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrustTierClassifierTest {
    @Test
    void classifiesPublicTrustTiersFromCandidateMetadata() {
        assertEquals(TrustTier.TRUSTED,
                TrustTierClassifier.publicTrustTier("INSIGHT", "CORE_KNOWLEDGE"));
        assertEquals(TrustTier.HUMAN_AUTHORED,
                TrustTierClassifier.publicTrustTier("ENGINEERING_STORY", "DOCUMENTATION"));
        assertEquals(TrustTier.SYSTEM_METADATA,
                TrustTierClassifier.publicTrustTier("ANALYSIS", "ANALYSIS"));
        assertEquals(TrustTier.TECHNICAL_EVIDENCE,
                TrustTierClassifier.publicTrustTier("SOURCE_FILE", "GIT"));
    }

    @Test
    void keepsExcludedAndUnknownValuesOutsidePublicTrustContract() {
        assertEquals(TrustTierClassifier.Classification.UNVALIDATED,
                TrustTierClassifier.classify("VALIDATABLE_PROPOSAL", "CORE_ANALYSIS"));
        assertEquals(TrustTierClassifier.Classification.TRANSIENT_AI,
                TrustTierClassifier.classify("INSIGHT", "AI_ENGINE"));
        assertEquals(TrustTierClassifier.Classification.UNKNOWN,
                TrustTierClassifier.classify("UNSUPPORTED", "OTHER"));
        assertNull(TrustTierClassifier.publicTrustTier("VALIDATABLE_PROPOSAL", "OTHER"));
        assertNull(TrustTierClassifier.publicTrustTier("INSIGHT", "AI_ENGINE"));
        assertNull(TrustTierClassifier.publicTrustTier("UNSUPPORTED", "OTHER"));
    }

    @Test
    void preservesPublicMapperPrecedenceForOverlappingMetadata() {
        assertEquals(TrustTier.SYSTEM_METADATA,
                TrustTierClassifier.publicTrustTier("ANALYSIS", "AI_ENGINE"));
        assertEquals(TrustTier.HUMAN_AUTHORED,
                TrustTierClassifier.publicTrustTier("ENGINEERING_STORY", "AI_ENGINE"));
        assertNull(TrustTierClassifier.publicTrustTier("VALIDATABLE_PROPOSAL", "GIT"));
        assertEquals(TrustTierClassifier.Classification.TRANSIENT_AI,
                TrustTierClassifier.classify("ANALYSIS", "AI_ENGINE"));
    }
}
