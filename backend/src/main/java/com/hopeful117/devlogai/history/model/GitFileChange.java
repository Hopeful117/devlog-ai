package com.hopeful117.devlogai.history.model;

public record GitFileChange(
        FileChangeType changeType,
        String oldPath,
        String newPath,
        boolean binary,
        int insertions,
        int deletions
) {
}
