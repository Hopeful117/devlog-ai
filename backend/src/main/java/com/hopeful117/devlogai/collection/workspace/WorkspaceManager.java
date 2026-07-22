package com.hopeful117.devlogai.collection.workspace;

import com.hopeful117.devlogai.source.entity.Source;

public interface WorkspaceManager {

    SynchronizedWorkspace synchronize(Source source, String targetRevision);
}
