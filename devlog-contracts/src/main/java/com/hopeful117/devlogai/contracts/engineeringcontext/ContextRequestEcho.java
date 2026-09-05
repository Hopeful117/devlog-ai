package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.util.List;
import java.util.UUID;

public record ContextRequestEcho(
        String projectSlug,
        String intent,
        List<String> files,
        UUID storyId
) {
    public ContextRequestEcho {
        files = files == null ? List.of() : List.copyOf(files);
    }
}