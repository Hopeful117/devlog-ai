package com.hopeful117.devlogai.collection.collector;

import java.util.List;

public record RepositoryScan(
        List<RepositoryFile> files,
        int visitedFileCount,
        int directoryCount,
        List<CollectionWarning> warnings
) {
    public RepositoryScan {
        files = List.copyOf(files);
        warnings = List.copyOf(warnings);
    }
}
