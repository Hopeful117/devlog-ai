package com.hopeful117.devlogai.history.provider;

import com.hopeful117.devlogai.history.model.GitCommitData;

import java.nio.file.Path;
import java.util.List;

/**
 * Provider-independent history port introduced by ADR-035 and ADR-036.
 */
public interface GitHistoryProvider {
    List<GitCommitData> readHistory(Path repository, String revision);
}
