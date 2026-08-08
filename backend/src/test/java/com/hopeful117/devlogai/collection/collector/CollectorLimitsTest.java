package com.hopeful117.devlogai.collection.collector;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CollectorLimitsTest {

    @Test
    void shouldHaveDefaultValues() {
        CollectorLimits limits = new CollectorLimits();
        assertEquals(10_000, limits.getMaxFiles());
        assertEquals(1_048_576, limits.getMaxFileSize());
        assertEquals(20_971_520, limits.getMaxTotalBytes());
        assertEquals(500, limits.getMaxFactsPerType());
        assertEquals(Duration.ofSeconds(10), limits.getCollectorTimeout());
        assertTrue(limits.getExcludedDirectories().contains(".git"));
        assertTrue(limits.getExcludedDirectories().contains("target"));
    }

    @Test
    void shouldSetValidValues() {
        CollectorLimits limits = new CollectorLimits();
        limits.setMaxFiles(5000);
        limits.setMaxFileSize(2048);
        limits.setMaxTotalBytes(10240);
        limits.setMaxFactsPerType(100);
        limits.setCollectorTimeout(Duration.ofSeconds(30));
        limits.setExcludedDirectories(Set.of("custom"));

        assertEquals(5000, limits.getMaxFiles());
        assertEquals(2048, limits.getMaxFileSize());
        assertEquals(10240, limits.getMaxTotalBytes());
        assertEquals(100, limits.getMaxFactsPerType());
        assertEquals(Duration.ofSeconds(30), limits.getCollectorTimeout());
        assertEquals(Set.of("custom"), limits.getExcludedDirectories());
    }

    @Test
    void shouldRejectZeroOrNegativeValues() {
        CollectorLimits limits = new CollectorLimits();
        assertThrows(IllegalArgumentException.class, () -> limits.setMaxFiles(0));
        assertThrows(IllegalArgumentException.class, () -> limits.setMaxFiles(-1));
        assertThrows(IllegalArgumentException.class, () -> limits.setMaxFileSize(0));
        assertThrows(IllegalArgumentException.class, () -> limits.setMaxFileSize(-1));
        assertThrows(IllegalArgumentException.class, () -> limits.setMaxTotalBytes(0));
        assertThrows(IllegalArgumentException.class, () -> limits.setMaxTotalBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> limits.setMaxFactsPerType(0));
        assertThrows(IllegalArgumentException.class, () -> limits.setMaxFactsPerType(-1));
    }

    @Test
    void shouldRejectNullZeroOrNegativeTimeout() {
        CollectorLimits limits = new CollectorLimits();
        assertThrows(IllegalArgumentException.class, () -> limits.setCollectorTimeout(null));
        assertThrows(IllegalArgumentException.class, () -> limits.setCollectorTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> limits.setCollectorTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    void excludedDirectoriesShouldReturnCopy() {
        CollectorLimits limits = new CollectorLimits();
        Set<String> dirs1 = limits.getExcludedDirectories();
        Set<String> dirs2 = limits.getExcludedDirectories();
        assertNotSame(dirs1, dirs2, "Should return different copy instances");
        assertEquals(dirs1, dirs2, "Contents should be equal");
        assertThrows(UnsupportedOperationException.class, () -> dirs1.add("should-not-work"));
    }
}
