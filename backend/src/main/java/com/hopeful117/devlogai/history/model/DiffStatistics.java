package com.hopeful117.devlogai.history.model;

public record DiffStatistics(
        int filesChanged,
        int insertions,
        int deletions,
        int binaryFiles
) {
}
