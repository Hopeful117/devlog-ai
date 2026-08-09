package com.hopeful117.devlogai.repositorycontext.enrichment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "devlog.repository-context.symbols")
public class RepositorySymbolPolicy {
    public static final String POLICY_ID = "selected-java-symbols";
    public static final String POLICY_VERSION = "v1";

    private int maxInspectedFiles = 6;
    private int maxInputCharactersPerFile = 200_000;
    private int maxSymbolsPerFile = 40;
    private int maxTotalSymbols = 120;
    private int maxComponentCharacters = 300;
    private int maxTokens = 1_500;
    private Duration maxParseDurationPerFile = Duration.ofMillis(500);
    private Duration maxTotalDuration = Duration.ofSeconds(2);

    public int getMaxInspectedFiles() { return maxInspectedFiles; }
    public void setMaxInspectedFiles(int value) { maxInspectedFiles = positive(value); }
    public int getMaxInputCharactersPerFile() { return maxInputCharactersPerFile; }
    public void setMaxInputCharactersPerFile(int value) {
        maxInputCharactersPerFile = positive(value);
    }
    public int getMaxSymbolsPerFile() { return maxSymbolsPerFile; }
    public void setMaxSymbolsPerFile(int value) { maxSymbolsPerFile = positive(value); }
    public int getMaxTotalSymbols() { return maxTotalSymbols; }
    public void setMaxTotalSymbols(int value) { maxTotalSymbols = positive(value); }
    public int getMaxComponentCharacters() { return maxComponentCharacters; }
    public void setMaxComponentCharacters(int value) {
        maxComponentCharacters = positive(value);
    }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int value) { maxTokens = positive(value); }
    public Duration getMaxParseDurationPerFile() { return maxParseDurationPerFile; }
    public void setMaxParseDurationPerFile(Duration value) {
        maxParseDurationPerFile = positive(value);
    }
    public Duration getMaxTotalDuration() { return maxTotalDuration; }
    public void setMaxTotalDuration(Duration value) { maxTotalDuration = positive(value); }

    private int positive(int value) {
        if (value < 1) throw new IllegalArgumentException("Symbol limit must be positive");
        return value;
    }

    private Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Symbol duration must be positive");
        }
        return value;
    }
}
