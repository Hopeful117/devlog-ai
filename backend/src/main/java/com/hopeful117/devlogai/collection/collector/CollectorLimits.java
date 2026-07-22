package com.hopeful117.devlogai.collection.collector;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "devlog.collection")
public class CollectorLimits {

    private int maxFiles = 10_000;
    private long maxFileSize = 1_048_576;
    private long maxTotalBytes = 20_971_520;
    private int maxFactsPerType = 500;
    private Duration collectorTimeout = Duration.ofSeconds(10);
    private Set<String> excludedDirectories = new LinkedHashSet<>(Set.of(
            ".git", "target", "build", "node_modules", "dist", "out",
            "coverage", ".venv", "venv"
    ));

    public int getMaxFiles() { return maxFiles; }
    public void setMaxFiles(int maxFiles) { this.maxFiles = positive(maxFiles, "maxFiles"); }
    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = positive(maxFileSize, "maxFileSize"); }
    public long getMaxTotalBytes() { return maxTotalBytes; }
    public void setMaxTotalBytes(long maxTotalBytes) { this.maxTotalBytes = positive(maxTotalBytes, "maxTotalBytes"); }
    public int getMaxFactsPerType() { return maxFactsPerType; }
    public void setMaxFactsPerType(int maxFactsPerType) { this.maxFactsPerType = positive(maxFactsPerType, "maxFactsPerType"); }
    public Duration getCollectorTimeout() { return collectorTimeout; }
    public void setCollectorTimeout(Duration collectorTimeout) {
        if (collectorTimeout == null || collectorTimeout.isZero() || collectorTimeout.isNegative()) {
            throw new IllegalArgumentException("collectorTimeout must be positive");
        }
        this.collectorTimeout = collectorTimeout;
    }
    public Set<String> getExcludedDirectories() { return Set.copyOf(excludedDirectories); }
    public void setExcludedDirectories(Set<String> excludedDirectories) {
        this.excludedDirectories = new LinkedHashSet<>(excludedDirectories);
    }

    private int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
    private long positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
