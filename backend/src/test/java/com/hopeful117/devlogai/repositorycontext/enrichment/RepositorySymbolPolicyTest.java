package com.hopeful117.devlogai.repositorycontext.enrichment;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositorySymbolPolicyTest {
    @Test
    void exposesBoundedDefaultsAndRejectsInvalidValues() {
        var policy = new RepositorySymbolPolicy();
        assertEquals(6, policy.getMaxInspectedFiles());
        assertEquals(1_500, policy.getMaxTokens());
        assertEquals(Duration.ofSeconds(2), policy.getMaxTotalDuration());
        assertThrows(IllegalArgumentException.class,
                () -> policy.setMaxInspectedFiles(0));
        assertThrows(IllegalArgumentException.class,
                () -> policy.setMaxParseDurationPerFile(Duration.ZERO));
    }
}
