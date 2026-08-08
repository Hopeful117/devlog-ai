package com.hopeful117.devlogai.repositorycontext.enrichment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "devlog.repository-context.content")
public class RepositoryContentPolicy {
    public static final String POLICY_ID = "selected-file-content";
    public static final String POLICY_VERSION = "v1";

    private int maxEnrichedFiles = 6;
    private int maxCharactersPerFile = 4_000;
    private int maxTotalCharacters = 12_000;

    public int getMaxEnrichedFiles() {
        return maxEnrichedFiles;
    }

    public void setMaxEnrichedFiles(int value) {
        maxEnrichedFiles = positive(value, "maxEnrichedFiles");
    }

    public int getMaxCharactersPerFile() {
        return maxCharactersPerFile;
    }

    public void setMaxCharactersPerFile(int value) {
        maxCharactersPerFile = positive(value, "maxCharactersPerFile");
    }

    public int getMaxTotalCharacters() {
        return maxTotalCharacters;
    }

    public void setMaxTotalCharacters(int value) {
        maxTotalCharacters = positive(value, "maxTotalCharacters");
    }

    private int positive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
